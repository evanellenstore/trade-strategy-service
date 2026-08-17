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
 * BB_REVERSAL Trading Strategy.
 *
 * This strategy generates BUY or SELL signals using Bollinger Bands to identify
 * potential reversal points when price touches or breaches the bands.
 *
 * Strategy Flow:
 *
 *                    +------------------+
 *                    | Indicator Event  |
 *                    +--------+---------+
 *                             |
 *                             v
 *                  +-------------------+
 *                  | Check BB Levels   |
 *                  +---------+---------+
 *                            |
 *             +--------------+--------------+
 *             |                             |
 *             v                             v
 *     Price <= BB Lower                Price >= BB Upper
 *             |                             |
 *             v                             v
 *           BUY                           SELL
 *             |                             |
 *             +-------------+---------------+
 *                           |
 *                           v
 *                Base Confidence = 65
 *                           |
 *                           v
 *              RSI Confirmation
 *             RSI < 30 (+20)
 *                           |
 *                           v
 *                Calculate Final Score
 *                     (Max = 100)
 *                           |
 *                           v
 *                 Generate SignalEvent
 *
 * Confidence Scoring:
 * - BUY when price touches/breaches lower Bollinger Band: 65 points (mean reversion setup)
 * - SELL when price touches/breaches upper Bollinger Band: 65 points (mean reversion setup)
 * - RSI confirmation (RSI < 30 for BUY, RSI > 70 for SELL) adds 20 points
 *
 * Signal Generation:
 * - BUY when price <= BB Lower, indicating potential oversold condition and reversal up
 * - SELL when price >= BB Upper, indicating potential overbought condition and reversal down
 * - Optional RSI confirmation increases confidence
 * - Confidence score is capped at 100
 */
@Component
public class BbReversalStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "BB_REVERSAL";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getBbUpper() == null
                || indicator.getBbLower() == null || indicator.getPrice() == null) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;

        // Price touches or breaches lower Bollinger Band - potential reversal UP
        if (indicator.getPrice() <= indicator.getBbLower()) {
            signal = "BUY";
            confidence = 65;

            // RSI confirmation for BUY (oversold)
            if (indicator.getRsi14() != null && indicator.getRsi14() < 30) {
                confidence += 20;
            }
        }
        // Price touches or breaches upper Bollinger Band - potential reversal DOWN
        else if (indicator.getPrice() >= indicator.getBbUpper()) {
            signal = "SELL";
            confidence = 65;

            // RSI confirmation for SELL (overbought)
            if (indicator.getRsi14() != null && indicator.getRsi14() > 70) {
                confidence += 20;
            }
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
