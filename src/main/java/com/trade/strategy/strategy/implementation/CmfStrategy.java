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
 * CMF Trading Strategy.
 *
 * This strategy generates BUY or SELL signals using the Chaikin Money Flow (CMF) indicator,
 * which measures the accumulation or distribution of money over a specified period.
 *
 * Strategy Flow:
 *
 *                    +------------------+
 *                    | Indicator Event  |
 *                    +--------+---------+
 *                             |
 *                             v
 *                  +-------------------+
 *                  | Check CMF Value   |
 *                  +---------+---------+
 *                            |
 *             +--------------+--------------+
 *             |                             |
 *             v                             v
 *         CMF > 0.05                    CMF < -0.05
 *      (Strong Accumulation)         (Strong Distribution)
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
 *              Strength Confirmation
 *            CMF extreme levels (+20)
 *             CMF > 0.2 or < -0.2
 *                           |
 *                           v
 *                Calculate Final Score
 *                     (Max = 100)
 *                           |
 *                           v
 *                 Generate SignalEvent
 *
 * Confidence Scoring:
 * - BUY when CMF > 0.05 (positive money flow/accumulation): 60 points
 * - SELL when CMF < -0.05 (negative money flow/distribution): 60 points
 * - Extreme CMF levels (> 0.2 or < -0.2) add 20 points for very strong signals
 *
 * Signal Generation:
 * - BUY when Chaikin Money Flow is positive (> 0.05), indicating
 *   accumulation and buying pressure
 * - SELL when Chaikin Money Flow is negative (< -0.05), indicating
 *   distribution and selling pressure
 * - CMF ranges from -1 to +1, with 0 being neutral
 * - Confidence score is capped at 100
 * - No signal when CMF is between -0.05 and 0.05 (neutral)
 */
@Component
public class CmfStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "CMF";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getCmf() == null) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;

        // BUY Signal: Strong accumulation (CMF > 0.05)
        if (indicator.getCmf() > 0.05) {
            signal = "BUY";
            confidence = 60;

            // Extreme accumulation (CMF > 0.2) - very strong signal
            if (indicator.getCmf() > 0.2) {
                confidence += 20;
            }
        }
        // SELL Signal: Strong distribution (CMF < -0.05)
        else if (indicator.getCmf() < -0.05) {
            signal = "SELL";
            confidence = 60;

            // Extreme distribution (CMF < -0.2) - very strong signal
            if (indicator.getCmf() < -0.2) {
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
