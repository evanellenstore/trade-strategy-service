package com.trade.strategy.strategy.implementation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.trade.strategy.dto.IndicatorEvent;
import com.trade.strategy.dto.PatternEvent;
import com.trade.strategy.dto.SignalEvent;
import com.trade.strategy.strategy.TradingStrategy;

/**
 * SuperTrend Trading Strategy.
 *
 * This strategy generates BUY or SELL signals by comparing the
 * current market price with the SuperTrend indicator value.
 *
 * Strategy Flow:
 *
 *                    +------------------+
 *                    | Indicator Event  |
 *                    +--------+---------+
 *                             |
 *                             v
 *                +------------------------+
 *                | Compare Price &        |
 *                | SuperTrend Value       |
 *                +-----------+------------+
 *                            |
 *             +--------------+--------------+
 *             |                             |
 *             v                             v
 *   Price > SuperTrend          Price < SuperTrend
 *             |                             |
 *             v                             v
 *           BUY                           SELL
 *             |                             |
 *             +-------------+---------------+
 *                           |
 *                           v
 *                Base Confidence = 60
 *                           |
 *                           v
 *                  ADX Confirmation
 *                 ADX > 25 (+20)
 *                           |
 *                           v
 *                Calculate Final Score
 *                     (Max = 100)
 *                           |
 *                           v
 *                 Generate SignalEvent
 *
 * Confidence Scoring:
 * - Price above SuperTrend: BUY signal with 60 points.
 * - Price below SuperTrend: SELL signal with 60 points.
 * - ADX > 25 adds 20 points, indicating a strong trend.
 *
 * Signal Generation:
 * - BUY when the market price moves above the SuperTrend line,
 *   indicating a potential uptrend.
 * - SELL when the market price moves below the SuperTrend line,
 *   indicating a potential downtrend.
 * - Confidence score is capped at 100.
 * - No signal is generated when required indicator values are unavailable.
 *
 * The strategy uses the SuperTrend indicator to identify trend direction
 * and combines it with ADX-based trend strength confirmation to improve
 * signal reliability and reduce false trend signals.
 */
@Component
public class SupertrendStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "SUPER_TREND";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getSupertrend() == null || indicator.getPrice() == null) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;

        if (indicator.getPrice() > indicator.getSupertrend()) {
            signal = "BUY";
            confidence += 60;
        } else if (indicator.getPrice() < indicator.getSupertrend()) {
            signal = "SELL";
            confidence += 60;
        }

        if (indicator.getAdx() != null && indicator.getAdx() > 25) {
            confidence += 20;
        }

        if ("HOLD".equals(signal)) {
            return Optional.empty();
        }

        return Optional.of(SignalEvent.builder()
                .signalId("SIG-" + UUID.randomUUID())
                .symbol(indicator.getSymbol())
                .symbolToken(indicator.getSymbolToken())
                .strategyName(getName())
                .timeframe(indicator.getTimeframe())
                .signal(signal)
                .confidence(Math.min(100, confidence))
                .price(indicator.getPrice())
                .timestamp(Instant.now())
                .build());
    }
}
