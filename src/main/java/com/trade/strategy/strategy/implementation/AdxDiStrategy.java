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
 * ADX_DI Trading Strategy.
 *
 * This strategy generates BUY or SELL signals using the Average Directional Index (ADX)
 * combined with Directional Indicators (+DI and -DI) to identify trend direction and strength.
 *
 * Strategy Flow:
 *
 *                    +------------------+
 *                    | Indicator Event  |
 *                    +--------+---------+
 *                             |
 *                             v
 *                  +-------------------+
 *                  | Check ADX & DI    |
 *                  +---------+---------+
 *                            |
 *             +--------------+--------------+
 *             |                             |
 *             v                             v
 *         +DI > -DI                     -DI > +DI
 *         ADX > 25                      ADX > 25
 *             |                             |
 *             v                             v
 *           BUY                           SELL
 *             |                             |
 *             +-------------+---------------+
 *                           |
 *                           v
 *                Base Confidence = 70
 *                           |
 *                           v
 *              ADX Strength Confirmation
 *             ADX > 40 (+15)
 *                           |
 *                           v
 *                Calculate Final Score
 *                     (Max = 100)
 *                           |
 *                           v
 *                 Generate SignalEvent
 *
 * Confidence Scoring:
 * - BUY when +DI > -DI and ADX > 25: 70 points (trend direction confirmed)
 * - SELL when -DI > +DI and ADX > 25: 70 points (downtrend direction confirmed)
 * - ADX > 40 adds 15 points, indicating a very strong trend
 *
 * Signal Generation:
 * - BUY when +DI (Plus Directional Indicator) is above -DI (Minus Directional Indicator)
 *   with ADX > 25, indicating an uptrend.
 * - SELL when -DI is above +DI with ADX > 25, indicating a downtrend.
 * - ADX must be > 25 for a valid signal (minimum trend strength threshold)
 * - Confidence score is capped at 100
 * - No signal when ADX <= 25 (insufficient trend strength)
 */
@Component
public class AdxDiStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "ADX_DI";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getAdx() == null
                || indicator.getAdxDIPlus() == null || indicator.getAdxDIMinus() == null) {
            return Optional.empty();
        }

        // ADX must be > 25 for a valid signal (minimum trend strength)
        if (indicator.getAdx() <= 25) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;

        // Check directional indicators
        if (indicator.getAdxDIPlus() > indicator.getAdxDIMinus()) {
            signal = "BUY";
            confidence = 70;
        } else if (indicator.getAdxDIMinus() > indicator.getAdxDIPlus()) {
            signal = "SELL";
            confidence = 70;
        }

        // Additional confirmation: very strong trend (ADX > 40)
        if (indicator.getAdx() > 40) {
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
