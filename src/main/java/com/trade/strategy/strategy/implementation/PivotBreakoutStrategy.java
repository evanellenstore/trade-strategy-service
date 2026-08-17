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
 * PIVOT_BREAKOUT Trading Strategy.
 *
 * This strategy generates BUY or SELL signals when price breaks through pivot levels
 * (support/resistance), indicating potential trend continuation.
 *
 * Strategy Flow:
 *
 *                    +------------------+
 *                    | Indicator Event  |
 *                    +--------+---------+
 *                             |
 *                             v
 *                  +-------------------+
 *                  | Check Pivot Levels|
 *                  +---------+---------+
 *                            |
 *         +------------------+------------------+
 *         |                  |                  |
 *         v                  v                  v
 *    Price > R1/R2    Price = Pivot      Price < S1/S2
 *         |                  |                  |
 *         v                  v                  v
 *       BUY                HOLD               SELL
 *         |                                      |
 *         +------------------+------------------+
 *                             |
 *                             v
 *                Base Confidence = 75
 *                             |
 *                             v
 *              Breakout Strength Check
 *            Distance from Pivot (+10)
 *                             |
 *                             v
 *                Calculate Final Score
 *                     (Max = 100)
 *                             |
 *                             v
 *                 Generate SignalEvent
 *
 * Confidence Scoring:
 * - BUY when price breaks above Resistance 1 or Resistance 2: 75 points
 * - SELL when price breaks below Support 1 or Support 2: 75 points
 * - Additional distance from pivot point adds 10 points for stronger breakout
 *
 * Signal Generation:
 * - BUY when price crosses above R1 or R2 (resistance breakout)
 * - SELL when price crosses below S1 or S2 (support breakout)
 * - Hold at pivot point (no signal)
 * - Stronger breakouts (more distance from pivot) increase confidence
 * - Confidence score is capped at 100
 */
@Component
public class PivotBreakoutStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "PIVOT_BREAKOUT";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getPivotPoint() == null || indicator.getPrice() == null
                || indicator.getPivotResistance1() == null || indicator.getPivotSupport1() == null) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;
        double priceDistance = 0;

        // Check for BUY breakout (price above resistance levels)
        if (indicator.getPrice() > indicator.getPivotResistance1()) {
            signal = "BUY";
            confidence = 75;
            // Additional confirmation if breaking R2
            if (indicator.getPivotResistance2() != null
                    && indicator.getPrice() > indicator.getPivotResistance2()) {
                confidence += 10;
            } else {
                // Calculate distance for strength confirmation
                priceDistance = indicator.getPrice() - indicator.getPivotResistance1();
                if (priceDistance > 0) {
                    confidence += 5;
                }
            }
        }
        // Check for SELL breakout (price below support levels)
        else if (indicator.getPrice() < indicator.getPivotSupport1()) {
            signal = "SELL";
            confidence = 75;
            // Additional confirmation if breaking S2
            if (indicator.getPivotSupport2() != null
                    && indicator.getPrice() < indicator.getPivotSupport2()) {
                confidence += 10;
            } else {
                // Calculate distance for strength confirmation
                priceDistance = indicator.getPivotSupport1() - indicator.getPrice();
                if (priceDistance > 0) {
                    confidence += 5;
                }
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
