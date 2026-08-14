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
 * MACD Trading Strategy.
 *
 * This strategy generates BUY or SELL signals based on the relationship
 * between the Moving Average Convergence Divergence (MACD) line and
 * its Signal line.
 *
 * Strategy Flow:
 *
 *                    +------------------+
 *                    | Indicator Event  |
 *                    +--------+---------+
 *                             |
 *                             v
 *                +-------------------------+
 *                | Compare MACD & Signal   |
 *                +------------+------------+
 *                             |
 *              +--------------+--------------+
 *              |                             |
 *              v                             v
 *      MACD > Signal                 MACD < Signal
 *              |                             |
 *              v                             v
 *            BUY                           SELL
 *              |                             |
 *              +-------------+---------------+
 *                            |
 *                            v
 *                 Add Base Confidence
 *                       (+60 Points)
 *                            |
 *                            v
 *                  ADX Confirmation
 *                 ADX > 25 (+20)
 *                            |
 *                            v
 *                  RSI Confirmation
 *          BUY  -> RSI > 60 (+10)
 *          SELL -> RSI < 40 (+10)
 *                            |
 *                            v
 *                 Calculate Final Score
 *                      (Max = 100)
 *                            |
 *                            v
 *                Generate SignalEvent
 *
 * Confidence Scoring:
 * - MACD bullish/bearish crossover: +60 points.
 * - ADX > 25 indicates strong trend strength: +20 points.
 * - RSI confirmation:
 *   - BUY: RSI(14) > 60 adds +10 points.
 *   - SELL: RSI(14) < 40 adds +10 points.
 *
 * Signal Generation:
 * - BUY when MACD is above the Signal line.
 * - SELL when MACD is below the Signal line.
 * - Confidence score is capped at 100.
 * - No signal is generated when MACD and Signal values are unavailable.
 *
 * The strategy combines MACD trend momentum, ADX trend strength,
 * and RSI momentum confirmation to improve signal reliability
 * and reduce false trading signals.
 */
@Component
public class MacdStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "MACD";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getMacd() == null || indicator.getMacdSignal() == null) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;

        if (indicator.getMacd() > indicator.getMacdSignal()) {
            signal = "BUY";
            confidence += 60;
        } else if (indicator.getMacd() < indicator.getMacdSignal()) {
            signal = "SELL";
            confidence += 60;
        }

        if (indicator.getAdx() != null && indicator.getAdx() > 25) {
            confidence += 20;
        }

        if (indicator.getRsi14() != null) {
            if ("BUY".equals(signal) && indicator.getRsi14() > 60) {
                confidence += 10;
            } else if ("SELL".equals(signal) && indicator.getRsi14() < 40) {
                confidence += 10;
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
