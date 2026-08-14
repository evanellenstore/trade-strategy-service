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
 * RSI Trading Strategy.
 *
 * This strategy generates BUY or SELL signals using the
 * Relative Strength Index (RSI) to identify overbought
 * and oversold market conditions.
 *
 * Strategy Flow:
 *
 *                    +------------------+
 *                    | Indicator Event  |
 *                    +--------+---------+
 *                             |
 *                             v
 *                  +-------------------+
 *                  | Check RSI(14)     |
 *                  +---------+---------+
 *                            |
 *             +--------------+--------------+
 *             |                             |
 *             v                             v
 *         RSI < 30                     RSI > 70
 *             |                             |
 *             v                             v
 *           BUY                           SELL
 *             |                             |
 *             +-------------+---------------+
 *                           |
 *                           v
 *                Base Confidence = 80
 *                           |
 *                           v
 *                  ADX Confirmation
 *                 ADX > 25 (+15)
 *                           |
 *                           v
 *                Calculate Final Score
 *                     (Max = 100)
 *                           |
 *                           v
 *                 Generate SignalEvent
 *
 * Confidence Scoring:
 * - RSI oversold condition (RSI < 30): BUY signal with 80 points.
 * - RSI overbought condition (RSI > 70): SELL signal with 80 points.
 * - ADX > 25 adds 15 points, indicating a stronger market trend.
 *
 * Signal Generation:
 * - BUY when RSI(14) falls below 30, indicating a potentially
 *   oversold market and possible price reversal.
 * - SELL when RSI(14) rises above 70, indicating a potentially
 *   overbought market and possible price correction.
 * - Confidence score is capped at 100.
 * - No signal is generated when RSI remains between 30 and 70.
 *
 * The strategy uses RSI as a momentum oscillator to identify
 * potential reversal opportunities and uses ADX as an additional
 * confirmation of trend strength before increasing confidence.
 */
@Component
public class RsiStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "RSI";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getRsi14() == null) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;

        if (indicator.getRsi14() < 30) {
            signal = "BUY";
            confidence = 80;
        } else if (indicator.getRsi14() > 70) {
            signal = "SELL";
            confidence = 80;
        }

        if (indicator.getAdx() != null && indicator.getAdx() > 25) {
            confidence += 15;
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
