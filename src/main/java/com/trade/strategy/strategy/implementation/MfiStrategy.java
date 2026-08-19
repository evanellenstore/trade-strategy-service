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
 * Generates BUY or SELL signals using the Money Flow Index (MFI), a
 * volume-weighted oscillator, to catch mean-reversion extremes -- with an
 * extra bonus for truly rare, exceptionally deep extremes.
 *
 * ┌───────────────────────────────── WHY MFI ─────────────────────────────────────────┐
 * │ • Unique among oscillators: combines BOTH price AND volume                       │
 * │ • MFI < 20 -> volume-weighted oversold -> strong reversal signal UP (BUY)         │
 * │ • MFI > 80 -> volume-weighted overbought -> strong reversal signal DOWN (SELL)    │
 * │ • Volume confirms the move: heavy volume at a reversal zone = higher probability  │
 * │ • More complete than RSI alone -- RSI has no volume component built in            │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY EXTREME LEVELS ADD CONFIDENCE ─────────────────┐
 * │ • MFI < 10  -> exceptionally rare extreme oversold -> panic-selling exhaustion    │
 * │ • MFI > 90  -> exceptionally rare extreme overbought -> euphoric-buying exhaustion│
 * │ • These deeper extremes are rare enough that hitting them is a much stronger      │
 * │   reversal tell than the ordinary 20/80 threshold alone -- worth +20             │
 * │ • Optional: not reaching the extreme does NOT block the signal, only the +20     │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY NOT OTHER INDICATORS ──────────────────────────┐
 * │ • RSI                      -> similar momentum concept but no volume insight     │
 * │ • Stochastic                -> also lacks a volume component; MFI is more        │
 * │                                complete for this purpose                         │
 * │ • MACD                      -> trend-following approach, not mean reversion      │
 * │ • Raw volume bars alone     -> need a normalized oscillator like MFI to be        │
 * │                                actionable                                        │
 * │ • ADX                       -> trend strength isn't relevant to a pure mean-      │
 * │                                reversion signal like this one                    │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────── INPUTS ───────────────────────────────────────┐
 * │                                                                                    │
 * │   IndicatorEvent  [ONLY input used]          PatternEvent  [IGNORED]              │
 * │  ┌──────────────────────────┐               ┌──────────────────────────┐         │
 * │  │ mfi         -- required  │               │ patternName  -- n/a      │         │
 * │  │   (also checked against  │               │ (chart-pattern signals   │         │
 * │  │    the 10/90 extreme     │               │  belong to pattern-based │         │
 * │  │    thresholds)           │               │  strategies, or those    │         │
 * │  │ price                    │               │  that explicitly add     │         │
 * │  │ symbol/symbolToken       │               │  pattern confirmation --  │         │
 * │  │ timeframe                │               │  not this one)           │         │
 * │  └──────────────────────────┘               └──────────────────────────┘         │
 * │                                                                                    │
 * │  MFI is a pure indicator-driven strategy: it reacts only to the MFI(14) value     │
 * │  (checked twice -- once at 20/80, once at 10/90). The `pattern` argument is       │
 * │  accepted to satisfy the TradingStrategy interface but is never read here.        │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * STRATEGY FLOW
 * ═════════════
 *
 *              ┌─────────────────────┐        ┌─────────────────────┐
 *              │   IndicatorEvent    │        │    PatternEvent      │
 *              │       mfi           │        │    (received,        │
 *              │  [ONLY input used]  │        │      unused)         │
 *              └──────────┬──────────┘        └──────────────────────┘
 *                         │
 *                         ▼
 *              ┌───────────────────────────┐
 *              │       Check MFI(14)        │
 *              └──────────────┬─────────────┘
 *                              │
 *          ┌───────────────────┴───────────────────┐
 *          ▼                                        ▼
 *   MFI < 20                                  MFI > 80
 *   (oversold)                                (overbought)
 *          │                                        │
 *          ▼                                        ▼
 *   signal = BUY                            signal = SELL
 *   confidence = 65                         confidence = 65
 *          │                                        │
 *          ▼                                        ▼
 *   MFI < 10 ?                               MFI > 90 ?
 *  ┌─────┴─────┐                            ┌─────┴─────┐
 * yes           no                         yes           no
 *  │             │                          │             │
 *  ▼             │                          ▼             │
 * +20 (=85)      │                         +20 (=85)      │
 *  │             │                          │             │
 *  └──────┬──────┘                          └──────┬──────┘
 *         │                                         │
 *         └────────────────────┬────────────────────┘
 *                              ▼
 *              MFI stayed between 20 and 80?
 *              signal stays HOLD -> return Optional.empty()
 *                              │
 *                              ▼
 *              confidence = min(confidence, 100)
 *              (real max here is 85, cap never actually reached)
 *                              │
 *                              ▼
 *                   build & return SignalEvent
 *
 * CONFIDENCE SCORING (max 100, real max reached = 85)
 * ─────────────────────────────────────────────────────
 *   Base MFI extreme .......... 65   (either direction, from IndicatorEvent)
 *   Deep-extreme bonus ......... +20  (MFI < 10 for BUY, MFI > 90 for SELL)
 *   Cap ......................... 100  (unreachable with this scoring)
 *   Note: the deep-extreme bonus is a bonus, not a requirement -- a plain
 *   20/80 threshold cross alone still produces a 65-confidence signal.
 *
 * SIGNAL RULES
 * ────────────
 *   BUY  : MFI(14) < 20  (volume-weighted oversold -> expect reversal up)
 *   SELL : MFI(14) > 80  (volume-weighted overbought -> expect reversal down)
 *   HOLD : MFI(14) between 20 and 80 -> no signal emitted (Optional.empty())
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

        // MFI OVERSOLD DETECTION (Mean Reversion UP - 65 points base)
        // USED: MFI < 20 signals volume-weighted oversold condition
        // LOGIC: When money flow index drops to extreme lows = strong sellers + weak buyers
        // REVERSAL: Sellers get exhausted; price likely bounces up
        if (indicator.getMfi() < 20) {
            signal = "BUY";
            confidence = 65;

            // EXTREME OVERSOLD CONFIRMATION (+20 points if MFI < 10)
            // USED: MFI < 10 = exceptionally rare extreme (very strong signal)
            // BENEFIT: Extreme levels signal panic selling with highest reversal probability
            if (indicator.getMfi() < 10) {
                confidence += 20;
            }
        }
        // MFI OVERBOUGHT DETECTION (Mean Reversion DOWN - 65 points base)
        // USED: MFI > 80 signals volume-weighted overbought condition
        // LOGIC: When money flow index rises to extreme highs = strong buyers + weak sellers
        // REVERSAL: Buyers get exhausted; price likely pulls back down
        else if (indicator.getMfi() > 80) {
            signal = "SELL";
            confidence = 65;

            // EXTREME OVERBOUGHT CONFIRMATION (+20 points if MFI > 90)
            // USED: MFI > 90 = exceptionally rare extreme (very strong signal)
            // BENEFIT: Extreme levels signal euphoric buying with highest reversal probability
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
                .reason("MFI " + indicator.getMfi() + " reached a money-flow extreme for " + signal)
                .timestamp(Instant.now())
                .build());
    }
}