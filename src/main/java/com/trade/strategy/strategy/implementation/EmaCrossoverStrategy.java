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
 * EMA Crossover Trading Strategy.
 *
 * This strategy generates BUY or SELL signals by comparing the
 * 20-period Exponential Moving Average (EMA20) with the
 * 50-period Exponential Moving Average (EMA50).
 *
 * Strategy Flow:
 *
 *                    +------------------+
 *                    | Indicator Event  |
 *                    +--------+---------+
 *                             |
 *                             v
 *                 +-----------------------+
 *                 | Compare EMA20 & EMA50 |
 *                 +-----------+-----------+
 *                             |
 *              +--------------+--------------+
 *              |                             |
 *              v                             v
 *       EMA20 > EMA50                 EMA20 < EMA50
 *              |                             |
 *              v                             v
 *            BUY                           SELL
 *              |                             |
 *              +-------------+---------------+
 *                            |
 *                            v
 *                 Add Confidence Score
 *                            |
 *              +-------------+-------------+
 *              |                           |
 *              v                           v
 *       RSI Confirmation           ADX Confirmation
 *     BUY : RSI > 60 (+20)         ADX > 25 (+20)
 *     SELL: RSI < 40 (+20)
 *                            |
 *                            v
 *                 Pattern Confirmation
 *       BUY  -> bull/up patterns (+15)
 *       SELL -> bear/down patterns (+15)
 *                            |
 *                            v
 *                 Calculate Final Score
 *                      (Max = 100)
 *                            |
 *                            v
 *                Generate SignalEvent
 *
 * Confidence Scoring:
 * - EMA crossover confirmation: +25 points.
 * - RSI confirmation:
 *   - BUY: RSI(14) > 60 adds +20 points.
 *   - SELL: RSI(14) < 40 adds +20 points.
 * - ADX confirmation:
 *   - ADX > 25 adds +20 points.
 * - Pattern confirmation:
 *   - Bullish patterns ("bull", "up") add +15 points to BUY signals.
 *   - Bearish patterns ("bear", "down") add +15 points to SELL signals.
 *
 * Signal Generation:
 * - BUY when EMA20 > EMA50.
 * - SELL when EMA20 < EMA50.
 * - Confidence score is capped at 100.
 * - No signal is generated when trend direction cannot be determined.
 *
 * This strategy combines trend direction (EMA), momentum (RSI),
 * trend strength (ADX), and pattern confirmation to improve
 * signal reliability and reduce false signals.
 */



@Component
public class EmaCrossoverStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "EMA_CROSSOVER";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getEma20() == null || indicator.getEma50() == null) {
            return Optional.empty();
        }

        int confidence = 0;
        String signal = "HOLD";

        if (indicator.getEma20() > indicator.getEma50()) {
            confidence += 25;
            signal = "BUY";
        } else if (indicator.getEma20() < indicator.getEma50()) {
            confidence += 25;
            signal = "SELL";
        }

        if (indicator.getRsi14() != null) {
            if ("BUY".equals(signal) && indicator.getRsi14() > 60) {
                confidence += 20;
            } else if ("SELL".equals(signal) && indicator.getRsi14() < 40) {
                confidence += 20;
            }
        }

        if (indicator.getAdx() != null && indicator.getAdx() > 25) {
            confidence += 20;
        }

        if (pattern != null && pattern.getPatternName() != null) {
            String name = pattern.getPatternName().toLowerCase();
            if ("BUY".equals(signal) && (name.contains("bull") || name.contains("up"))) {
                confidence += 15;
            } else if ("SELL".equals(signal) && (name.contains("bear") || name.contains("down"))) {
                confidence += 15;
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
