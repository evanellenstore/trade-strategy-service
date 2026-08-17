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
 * MFI Trading Strategy.
 *
 * This strategy generates BUY or SELL signals using the Money Flow Index (MFI),
 * which combines price and volume to identify overbought/oversold conditions.
 *
 * Strategy Flow:
 *
 *                    +------------------+
 *                    | Indicator Event  |
 *                    +--------+---------+
 *                             |
 *                             v
 *                  +-------------------+
 *                  | Check MFI(14)     |
 *                  +---------+---------+
 *                            |
 *             +--------------+--------------+
 *             |                             |
 *             v                             v
 *         MFI < 20                      MFI > 80
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
 *              Volume Confirmation
 *             Extreme MFI levels (+20)
 *             MFI < 10 or > 90
 *                           |
 *                           v
 *                Calculate Final Score
 *                     (Max = 100)
 *                           |
 *                           v
 *                 Generate SignalEvent
 *
 * Confidence Scoring:
 * - BUY when MFI < 20 (money flow oversold): 65 points
 * - SELL when MFI > 80 (money flow overbought): 65 points
 * - Extreme MFI levels (< 10 or > 90) add 20 points for very strong signals
 *
 * Signal Generation:
 * - BUY when Money Flow Index falls below 20, indicating oversold condition
 *   and potential volume-based reversal upward
 * - SELL when Money Flow Index rises above 80, indicating overbought condition
 *   and potential volume-based reversal downward
 * - MFI is unique among momentum indicators as it incorporates volume
 * - Confidence score is capped at 100
 * - No signal when MFI is between 20 and 80
 */
@Component
public class MfiStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "MFI";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getMfi() == null) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;

        // BUY Signal: Money Flow Index oversold (< 20)
        if (indicator.getMfi() < 20) {
            signal = "BUY";
            confidence = 65;

            // Extreme oversold condition (MFI < 10) - very strong signal
            if (indicator.getMfi() < 10) {
                confidence += 20;
            }
        }
        // SELL Signal: Money Flow Index overbought (> 80)
        else if (indicator.getMfi() > 80) {
            signal = "SELL";
            confidence = 65;

            // Extreme overbought condition (MFI > 90) - very strong signal
            if (indicator.getMfi() > 90) {
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
