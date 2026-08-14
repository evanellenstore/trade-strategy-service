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
 * VWAP Breakout Trading Strategy.
 *
 * This strategy generates BUY or SELL signals by comparing the
 * current market price with the Volume Weighted Average Price (VWAP).
 *
 * Strategy Flow:
 *
 *                    +------------------+
 *                    | Indicator Event  |
 *                    +--------+---------+
 *                             |
 *                             v
 *                +------------------------+
 *                | Compare Price & VWAP   |
 *                +-----------+------------+
 *                            |
 *             +--------------+--------------+
 *             |                             |
 *             v                             v
 *      Price > VWAP                 Price < VWAP
 *             |                             |
 *             v                             v
 *           BUY                           SELL
 *             |                             |
 *             +-------------+---------------+
 *                           |
 *                           v
 *                Base Confidence = 20
 *                           |
 *                           v
 *                Pattern Confirmation
 *       BUY  -> bull/up patterns (+15)
 *       SELL -> bear/down patterns (+15)
 *                           |
 *                           v
 *                  RSI Confirmation
 *          BUY  -> RSI > 60 (+15)
 *          SELL -> RSI < 40 (+15)
 *                           |
 *                           v
 *                Calculate Final Score
 *                     (Max = 100)
 *                           |
 *                           v
 *                 Generate SignalEvent
 *
 * Confidence Scoring:
 * - Price above VWAP: BUY signal with 20 points.
 * - Price below VWAP: SELL signal with 20 points.
 * - Pattern confirmation:
 *   - Bullish patterns containing "bull" or "up" add 15 points to BUY signals.
 *   - Bearish patterns containing "bear" or "down" add 15 points to SELL signals.
 * - RSI confirmation:
 *   - BUY: RSI(14) > 60 adds 15 points.
 *   - SELL: RSI(14) < 40 adds 15 points.
 *
 * Signal
 */

@Component
public class VwapBreakoutStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "VWAP";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getVwap() == null || indicator.getPrice() == null) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;

        if (indicator.getPrice() > indicator.getVwap()) {
            signal = "BUY";
            confidence += 20;
        } else if (indicator.getPrice() < indicator.getVwap()) {
            signal = "SELL";
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

        if (indicator.getRsi14() != null) {
            if ("BUY".equals(signal) && indicator.getRsi14() > 60) {
                confidence += 15;
            } else if ("SELL".equals(signal) && indicator.getRsi14() < 40) {
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
