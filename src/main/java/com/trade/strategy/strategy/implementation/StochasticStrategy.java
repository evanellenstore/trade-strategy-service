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
 * STOCHASTIC Trading Strategy.
 *
 * This strategy generates BUY or SELL signals using the Stochastic Oscillator (%K and %D)
 * to identify overbought/oversold conditions and crossovers.
 *
 * Strategy Flow:
 *
 *                    +------------------+
 *                    | Indicator Event  |
 *                    +--------+---------+
 *                             |
 *                             v
 *                  +-------------------+
 *                  | Check Stoch K & D |
 *                  +---------+---------+
 *                            |
 *             +--------------+--------------+
 *             |                             |
 *             v                             v
 *         K < 20 OR                     K > 80 OR
 *         K > D (Bullish)               K < D (Bearish)
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
 *              Trend Confirmation
 *             ADX > 25 (+20)
 *                           |
 *                           v
 *                Calculate Final Score
 *                     (Max = 100)
 *                           |
 *                           v
 *                 Generate SignalEvent
 *
 * Confidence Scoring:
 * - BUY when %K < 20 (oversold) or %K > %D (bullish crossover): 60 points
 * - SELL when %K > 80 (overbought) or %K < %D (bearish crossover): 60 points
 * - ADX confirmation (ADX > 25) adds 20 points for trend strength
 *
 * Signal Generation:
 * - BUY when Stochastic %K drops below 20 (oversold condition)
 *   or when %K crosses above %D (bullish crossover)
 * - SELL when Stochastic %K rises above 80 (overbought condition)
 *   or when %K crosses below %D (bearish crossover)
 * - ADX > 25 confirms trend strength and adds confidence
 * - Confidence score is capped at 100
 */
@Component
public class StochasticStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "STOCHASTIC";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getStochK() == null || indicator.getStochD() == null) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;

        // BUY Signal: Oversold (%K < 20) or Bullish Crossover (%K > %D)
        if (indicator.getStochK() < 20 || indicator.getStochK() > indicator.getStochD()) {
            signal = "BUY";
            confidence = 60;
        }
        // SELL Signal: Overbought (%K > 80) or Bearish Crossover (%K < %D)
        else if (indicator.getStochK() > 80 || indicator.getStochK() < indicator.getStochD()) {
            signal = "SELL";
            confidence = 60;
        }

        // ADX confirmation for trend strength
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
