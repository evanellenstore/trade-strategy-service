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
 * MULTI_INDICATOR Trading Strategy.
 *
 * This strategy combines signals from multiple technical indicators to generate
 * high-confidence BUY or SELL signals based on consensus. It acts as a consensus
 * mechanism that looks for multiple indicators to align on the same direction.
 *
 * Strategy Flow:
 *
 *                    +------------------+
 *                    | Indicator Event  |
 *                    +--------+---------+
 *                             |
 *                             v
 *                  +-------------------+
 *                  | Evaluate All      |
 *                  | Major Indicators  |
 *                  +---------+---------+
 *                            |
 *          +----------+------+------+----------+
 *          |          |             |          |
 *          v          v             v          v
 *        RSI       MACD         Stoch        EMA
 *      < 30 / >70  Cross        Cross      Cross
 *          |          |             |          |
 *          +----------+------+------+----------+
 *                     |
 *                     v
 *           Count Bullish/Bearish
 *               Signals
 *                     |
 *              +-----------+
 *              |           |
 *              v           v
 *          >= 3 BUY   >= 3 SELL
 *          signals   signals
 *              |           |
 *              v           v
 *            BUY          SELL
 *              |           |
 *              +-----+-----+
 *                    |
 *                    v
 *            Confidence = 85+
 *            (Multi-indicator
 *             consensus)
 *                    |
 *                    v
 *            Generate SignalEvent
 *
 * Confidence Scoring:
 * - Requires consensus from multiple indicators (minimum 3 signals in same direction)
 * - Base confidence: 85 points (high-confidence multi-indicator agreement)
 * - Each additional indicator in agreement: +5 points (up to 100 max)
 * - Consensus signals have much higher confidence than single indicators
 *
 * Indicators Evaluated:
 * 1. RSI: RSI < 30 (BUY) or RSI > 70 (SELL)
 * 2. MACD: MACD > Signal (BUY) or MACD < Signal (SELL)
 * 3. Stochastic: %K > %D (BUY) or %K < %D (SELL)
 * 4. Bollinger Bands: Price below BB Lower (BUY) or above BB Upper (SELL)
 * 5. ADX+DI: +DI > -DI (BUY) or -DI > +DI (SELL) with ADX > 25
 * 6. EMA: Price above EMA (BUY) or below EMA (SELL)
 *
 * Signal Generation:
 * - BUY only when 3+ indicators show bullish signals (consensus)
 * - SELL only when 3+ indicators show bearish signals (consensus)
 * - High confidence (85+) ensures only strongest multi-indicator setups are used
 * - More robust than any single indicator strategy
 * - Reduces false signals through consensus mechanism
 */
@Component
public class MultiIndicatorStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "MULTI_INDICATOR";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null) {
            return Optional.empty();
        }

        int bullishSignals = 0;
        int bearishSignals = 0;

        // 1. RSI Signal
        if (indicator.getRsi14() != null) {
            if (indicator.getRsi14() < 30) {
                bullishSignals++;
            } else if (indicator.getRsi14() > 70) {
                bearishSignals++;
            }
        }

        // 2. MACD Signal
        if (indicator.getMacd() != null && indicator.getMacdSignal() != null) {
            if (indicator.getMacd() > indicator.getMacdSignal()) {
                bullishSignals++;
            } else if (indicator.getMacd() < indicator.getMacdSignal()) {
                bearishSignals++;
            }
        }

        // 3. Stochastic Signal
        if (indicator.getStochK() != null && indicator.getStochD() != null) {
            if (indicator.getStochK() > indicator.getStochD()) {
                bullishSignals++;
            } else if (indicator.getStochK() < indicator.getStochD()) {
                bearishSignals++;
            }
        }

        // 4. Bollinger Bands Signal
        if (indicator.getBbUpper() != null && indicator.getBbLower() != null
                && indicator.getPrice() != null) {
            if (indicator.getPrice() <= indicator.getBbLower()) {
                bullishSignals++;
            } else if (indicator.getPrice() >= indicator.getBbUpper()) {
                bearishSignals++;
            }
        }

        // 5. ADX + Directional Indicators Signal
        if (indicator.getAdx() != null && indicator.getAdxDIPlus() != null
                && indicator.getAdxDIMinus() != null && indicator.getAdx() > 25) {
            if (indicator.getAdxDIPlus() > indicator.getAdxDIMinus()) {
                bullishSignals++;
            } else if (indicator.getAdxDIMinus() > indicator.getAdxDIPlus()) {
                bearishSignals++;
            }
        }

        // 6. EMA Signal (simple: price above EMA50 = bullish)
        if (indicator.getEma50() != null && indicator.getPrice() != null) {
            if (indicator.getPrice() > indicator.getEma50()) {
                bullishSignals++;
            } else if (indicator.getPrice() < indicator.getEma50()) {
                bearishSignals++;
            }
        }

        String signal = "HOLD";
        int confidence = 0;

        // Require consensus from at least 3 indicators
        if (bullishSignals >= 3) {
            signal = "BUY";
            // Base confidence for multi-indicator consensus
            confidence = 85;
            // Additional points for each indicator beyond 3
            if (bullishSignals > 3) {
                confidence += Math.min(bullishSignals - 3, 5);
            }
        } else if (bearishSignals >= 3) {
            signal = "SELL";
            // Base confidence for multi-indicator consensus
            confidence = 85;
            // Additional points for each indicator beyond 3
            if (bearishSignals > 3) {
                confidence += Math.min(bearishSignals - 3, 5);
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
