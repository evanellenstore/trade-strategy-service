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
 * Generates BUY or SELL signals using the Chaikin Money Flow (CMF)
 * indicator, which measures money accumulation vs. distribution over a
 * period, with a bonus for exceptionally strong readings.
 *
 * ┌───────────────────────────────── WHY CMF ─────────────────────────────────────────┐
 * │ • Measures money flow direction: accumulation vs. distribution                   │
 * │ • CMF > 0.05  -> positive money flow / strong accumulation -> BUY setup           │
 * │ • CMF < -0.05 -> negative money flow / strong distribution -> SELL setup          │
 * │ • Normalized range -1 to +1 -- easy to interpret vs. raw volume bars              │
 * │ • Combines price location within its range + volume, similar in spirit to        │
 * │   On-Balance Volume but normalized for direct comparison                         │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY EXTREME LEVELS ADD CONFIDENCE ─────────────────┐
 * │ • CMF > 0.2   -> rare, extreme accumulation -> strong institutional conviction    │
 * │ • CMF < -0.2  -> rare, extreme distribution -> strong institutional conviction    │
 * │ • These deeper readings are uncommon enough that hitting them is a materially     │
 * │   stronger tell than the ordinary ±0.05 threshold alone -- worth +20             │
 * │ • Optional: not reaching the extreme does NOT block the signal, only the +20     │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY NOT OTHER INDICATORS ──────────────────────────┐
 * │ • Raw volume bars alone   -> unnormalized; CMF gives a bounded, comparable range  │
 * │ • RSI                      -> no volume component; CMF adds institutional         │
 * │                                money-flow insight RSI can't see                  │
 * │ • MFI                      -> similar goal but a different calculation (CMF is    │
 * │                                Chaikin-accumulation based, not RSI-style)         │
 * │ • Price action alone       -> needs the volume context CMF provides               │
 * │ • Other oscillators        -> different purpose; CMF specifically isolates money  │
 * │                                flow direction, not momentum or overbought state   │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────── INPUTS ───────────────────────────────────────┐
 * │                                                                                    │
 * │   IndicatorEvent  [ONLY input used]          PatternEvent  [IGNORED]              │
 * │  ┌──────────────────────────┐               ┌──────────────────────────┐         │
 * │  │ cmf         -- required  │               │ patternName  -- n/a      │         │
 * │  │   (also checked against  │               │ (chart-pattern signals   │         │
 * │  │    the ±0.2 extreme      │               │  belong to pattern-based │         │
 * │  │    thresholds)           │               │  strategies, or those    │         │
 * │  │ price                    │               │  that explicitly add     │         │
 * │  │ symbol/symbolToken       │               │  pattern confirmation --  │         │
 * │  │ timeframe                │               │  not this one)           │         │
 * │  └──────────────────────────┘               └──────────────────────────┘         │
 * │                                                                                    │
 * │  CMF is a pure indicator-driven strategy: it reacts only to the CMF value          │
 * │  (checked twice -- once at ±0.05, once at ±0.2). The `pattern` argument is        │
 * │  accepted to satisfy the TradingStrategy interface but is never read here.        │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * STRATEGY FLOW
 * ═════════════
 *
 *              ┌─────────────────────┐        ┌─────────────────────┐
 *              │   IndicatorEvent    │        │    PatternEvent      │
 *              │        cmf          │        │    (received,        │
 *              │  [ONLY input used]  │        │      unused)         │
 *              └──────────┬──────────┘        └──────────────────────┘
 *                         │
 *                         ▼
 *              ┌───────────────────────────┐
 *              │       Check CMF value       │
 *              └──────────────┬─────────────┘
 *                              │
 *          ┌───────────────────┴───────────────────┐
 *          ▼                                        ▼
 *   CMF > 0.05                                CMF < -0.05
 *   (strong accumulation)                     (strong distribution)
 *          │                                        │
 *          ▼                                        ▼
 *   signal = BUY                            signal = SELL
 *   confidence = 60                         confidence = 60
 *          │                                        │
 *          ▼                                        ▼
 *   CMF > 0.2 ?                              CMF < -0.2 ?
 *  ┌─────┴─────┐                            ┌─────┴─────┐
 * yes           no                         yes           no
 *  │             │                          │             │
 *  ▼             │                          ▼             │
 * +20 (=80)      │                         +20 (=80)      │
 *  │             │                          │             │
 *  └──────┬──────┘                          └──────┬──────┘
 *         │                                         │
 *         └────────────────────┬────────────────────┘
 *                              ▼
 *              CMF stayed between -0.05 and 0.05?
 *              signal stays HOLD -> return Optional.empty()
 *                              │
 *                              ▼
 *              confidence = min(confidence, 100)
 *              (real max here is 80, cap never actually reached)
 *                              │
 *                              ▼
 *                   build & return SignalEvent
 *
 * CONFIDENCE SCORING (max 100, real max reached = 80)
 * ─────────────────────────────────────────────────────
 *   Base CMF signal ........... 60   (either direction, from IndicatorEvent)
 *   Extreme-level bonus ........ +20  (CMF > 0.2 for BUY, CMF < -0.2 for SELL)
 *   Cap ......................... 100  (unreachable with this scoring)
 *   Note: the extreme-level bonus is a bonus, not a requirement -- a plain
 *   ±0.05 threshold cross alone still produces a 60-confidence signal.
 *
 * SIGNAL RULES
 * ────────────
 *   BUY  : CMF > 0.05   (positive money flow -> accumulation / buying pressure)
 *   SELL : CMF < -0.05  (negative money flow -> distribution / selling pressure)
 *   HOLD : CMF between -0.05 and 0.05 (neutral) -> no signal emitted (Optional.empty())
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

        // CMF ACCUMULATION (Money Flow BUY - 60 points base)
        // USED: CMF > 0.05 signals positive money flow (accumulation phase)
        // LOGIC: Large volume purchases near high of range = institutional buying
        // SIGNAL: Smart money accumulating = likely further upside
        if (indicator.getCmf() > 0.05) {
            signal = "BUY";
            confidence = 60;

            // EXTREME ACCUMULATION CONFIRMATION (+20 points if CMF > 0.2)
            // USED: CMF > 0.2 = very strong accumulation (rare, extreme signal)
            // BENEFIT: Extreme accumulation suggests institutional conviction buying
            if (indicator.getCmf() > 0.2) {
                confidence += 20;
            }
        }
        // CMF DISTRIBUTION (Money Flow SELL - 60 points base)
        // USED: CMF < -0.05 signals negative money flow (distribution phase)
        // LOGIC: Large volume sales near low of range = institutional selling
        // SIGNAL: Smart money distributing = likely further downside
        else if (indicator.getCmf() < -0.05) {
            signal = "SELL";
            confidence = 60;

            // EXTREME DISTRIBUTION CONFIRMATION (+20 points if CMF < -0.2)
            // USED: CMF < -0.2 = very strong distribution (rare, extreme signal)
            // BENEFIT: Extreme distribution suggests institutional conviction selling
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
                .reason("CMF " + indicator.getCmf() + " indicates " + signal
                    + " accumulation/distribution pressure")
                .timestamp(Instant.now())
                .build());
    }
}