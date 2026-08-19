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
 * RSI Trading Strategy.
 *
 * Generates BUY or SELL signals using the Relative Strength Index (RSI) to
 * identify overbought/oversold extremes, confirmed by ADX trend strength.
 *
 * ┌───────────────────────────────── WHY RSI ─────────────────────────────────────────┐
 * │ • Momentum oscillator, range-bound 0-100 -- easy to read extremes                │
 * │ • RSI < 30 -> oversold -> extreme selling -> reversal UP likely (BUY)            │
 * │ • RSI > 70 -> overbought -> extreme buying -> reversal DOWN likely (SELL)        │
 * │ • Fast to react to momentum changes; well suited to mean-reversion setups        │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY ADX CONFIRMATION ──────────────────────────────┐
 * │ • ADX > 25 confirms a real trend exists behind the RSI extreme                   │
 * │ • RSI alone can stay pinned at an extreme for a long stretch in a strong trend   │
 * │   -- ADX helps validate the reversal isn't fighting a still-strong move          │
 * │ • Filters out weak signals from range-bound/choppy conditions                    │
 * │ • Optional: absent/weak ADX does NOT block the signal, only the +15             │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY NOT OTHER INDICATORS ──────────────────────────┐
 * │ • Raw price action        -> too noisy without a momentum oscillator for extremes│
 * │ • Bollinger Bands         -> price-based, not purpose-built for overbought/      │
 * │                              oversold identification the way RSI is             │
 * │ • Moving Averages         -> lag too much for a reversal-timing strategy         │
 * │ • MACD                    -> better suited to trend-following than mean reversion│
 * │ • Stochastic              -> similar oscillator to RSI; would be redundant       │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────── INPUTS ───────────────────────────────────────┐
 * │                                                                                    │
 * │   IndicatorEvent  [DRIVES the signal]        PatternEvent  [IGNORED]              │
 * │  ┌──────────────────────────┐               ┌──────────────────────────┐         │
 * │  │ rsi14       -- required  │               │ patternName  -- n/a      │         │
 * │  │ adx         -- optional, │               │ (chart-pattern signals   │         │
 * │  │   +15 pts, direction-    │               │  belong to pattern-based │         │
 * │  │   agnostic               │               │  strategies, or those    │         │
 * │  │ price                    │               │  that explicitly add     │         │
 * │  │ symbol/symbolToken       │               │  pattern confirmation --  │         │
 * │  │ timeframe                │               │  not this one)           │         │
 * │  └──────────────────────────┘               └──────────────────────────┘         │
 * │                                                                                    │
 * │  RSI is a pure indicator-driven strategy: it reacts only to RSI(14)               │
 * │  (+ optional ADX). The `pattern` argument is accepted to satisfy the              │
 * │  TradingStrategy interface but is never read here.                                │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * STRATEGY FLOW
 * ═════════════
 *
 *              ┌─────────────────────┐        ┌─────────────────────┐
 *              │   IndicatorEvent    │        │    PatternEvent      │
 *              │   rsi14, adx        │        │    (received,        │
 *              │  [ONLY input used]  │        │      unused)         │
 *              └──────────┬──────────┘        └──────────────────────┘
 *                         │
 *                         ▼
 *              ┌───────────────────────────┐
 *              │      Check RSI(14)         │
 *              └──────────────┬─────────────┘
 *                              │
 *          ┌───────────────────┴───────────────────┐
 *          ▼                                        ▼
 *   RSI < 30                                 RSI > 70
 *   (oversold extreme)                       (overbought extreme)
 *          │                                        │
 *          ▼                                        ▼
 *   signal = BUY                            signal = SELL
 *   confidence = 80                         confidence = 80
 *          │                                        │
 *          ▼                                        ▼
 *   ADX > 25 ?                              ADX > 25 ?
 *  (checked only when a                    (checked only when a
 *   signal was already set)                 signal was already set)
 *  ┌─────┴─────┐                            ┌─────┴─────┐
 * yes           no                         yes           no
 *  │             │                          │             │
 *  ▼             │                          ▼             │
 * +15 (=95)      │                         +15 (=95)      │
 *  │             │                          │             │
 *  └──────┬──────┘                          └──────┬──────┘
 *         │                                         │
 *         └────────────────────┬────────────────────┘
 *                              ▼
 *              RSI stayed between 30 and 70?
 *              signal stays HOLD -> return Optional.empty()
 *              (ADX check is never reached in this case)
 *                               │
 *                               ▼
 *              confidence = min(confidence, 100)
 *                               │
 *                               ▼
 *                    build & return SignalEvent
 *
 * CONFIDENCE SCORING (max 100, real max reached = 95)
 * ─────────────────────────────────────────────────────
 *   Base RSI extreme .......... 80   (either direction, from IndicatorEvent)
 *   ADX confirmation .......... +15  (ADX > 25, direction-agnostic)
 *   Cap ........................ 100  (unreachable with this scoring)
 *   Note: ADX confirmation is a bonus, not a requirement -- a plain RSI
 *   extreme alone still produces an 80-confidence signal (max with ADX = 95).
 *
 * SIGNAL RULES
 * ────────────
 *   BUY  : RSI(14) < 30  (oversold -> expect reversal up)
 *   SELL : RSI(14) > 70  (overbought -> expect reversal down)
 *   HOLD : RSI(14) between 30 and 70 -> no signal emitted (Optional.empty())
 */
@Component
public class RsiStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "RSI";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getRsi14() == null) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;

        // RSI OVERSOLD DETECTION (BUY - 80 points base)
        // USED: RSI < 30 indicates oversold condition (extreme selling = reversal up likely)
        // NOT USED alone: RSI can stay extreme for extended periods; needs trend confirmation
        if (indicator.getRsi14() < 30) {
            signal = "BUY";
            confidence = 80;

            // ADX TREND STRENGTH CONFIRMATION (+15 points if valid)
            // USED: ADX > 25 ensures a trend is present before reversing
            // PREVENTS: Taking reversal signals in range-bound markets where they fail
            if (indicator.getAdx() != null && indicator.getAdx() > 25) {
                confidence += 15;
            }
        }
        // RSI OVERBOUGHT DETECTION (SELL - 80 points base)
        // USED: RSI > 70 indicates overbought condition (extreme buying = reversal down likely)
        // NOT USED alone: RSI can stay extreme for extended periods; needs trend confirmation
        else if (indicator.getRsi14() > 70) {
            signal = "SELL";
            confidence = 80;

            // ADX TREND STRENGTH CONFIRMATION (+15 points if valid)
            // USED: ADX > 25 ensures a trend is present before reversing
            // PREVENTS: Taking reversal signals in range-bound markets where they fail
            if (indicator.getAdx() != null && indicator.getAdx() > 25) {
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
                .reason("RSI " + indicator.getRsi14() + " triggered " + signal
                    + (indicator.getAdx() != null && indicator.getAdx() > 25
                        ? " with ADX trend confirmation"
                        : " without ADX trend confirmation"))
                .timestamp(Instant.now())
                .build());
    }
}