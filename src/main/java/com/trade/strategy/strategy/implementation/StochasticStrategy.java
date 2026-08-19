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
 * Generates BUY or SELL signals using the Stochastic Oscillator (%K and %D)
 * to catch overbought/oversold extremes and momentum crossovers, confirmed
 * by ADX trend strength.
 *
 * ┌────────────────────────────── WHY STOCHASTIC ────────────────────────────────────┐
 * │ • %K = momentum position (0-100) within the recent trading range                 │
 * │ • %K < 20         -> oversold -> reversal UP likely                              │
 * │ • %K > %D         -> bullish crossover -> fast momentum above slow (BUY leg)     │
 * │ • %K > 80         -> overbought -> reversal DOWN likely                          │
 * │ • %K < %D         -> bearish crossover -> fast momentum below slow (SELL leg)    │
 * │ • Each side is an OR of two conditions (extreme level OR crossover) -- either    │
 * │   one alone is enough to trigger that side, see IMPORTANT note below             │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY ADX CONFIRMATION ──────────────────────────────┐
 * │ • ADX > 25 confirms a trending environment exists                                │
 * │ • Stochastic is a range/mean-reversion oscillator -- it can give false signals   │
 * │   when the market is choppy or trendless                                         │
 * │ • Optional: absent/weak ADX does NOT block the signal, only the +20             │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY NOT OTHER INDICATORS ──────────────────────────┐
 * │ • RSI                     -> similar oscillator to Stochastic; would be redundant│
 * │ • MACD                    -> better for trend-following than mean reversion      │
 * │ • Bollinger Bands         -> similar goal but price-based, not range-based       │
 * │ • Moving Averages         -> too slow for an overbought/oversold strategy        │
 * │ • Raw price               -> needs %K/%D's range context to mean anything        │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌──────────────────────── IMPORTANT: BUY IS CHECKED FIRST ─────────────────────────┐
 * │ The BUY condition (`stochK < 20 || stochK > stochD`) is evaluated in an          │
 * │ if / else-if chain BEFORE the SELL condition. Because it's an OR, a case where   │
 * │ %K is simultaneously > 80 (overbought) AND %K > %D (still crossed above %D)      │
 * │ will match the BUY branch first and never reach the SELL check. In practice      │
 * │ this means the crossover half of the OR can dominate the overbought/oversold     │
 * │ half whenever both legs disagree on direction.                                   │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────── INPUTS ───────────────────────────────────────┐
 * │                                                                                    │
 * │   IndicatorEvent  [DRIVES the signal]        PatternEvent  [IGNORED]              │
 * │  ┌──────────────────────────┐               ┌──────────────────────────┐         │
 * │  │ stochK      -- required  │               │ patternName  -- n/a      │         │
 * │  │ stochD      -- required  │               │ (chart-pattern signals   │         │
 * │  │ adx         -- optional, │               │  belong to pattern-based │         │
 * │  │   +20 pts, direction-    │               │  strategies, or those    │         │
 * │  │   agnostic               │               │  that explicitly add     │         │
 * │  │ price                    │               │  pattern confirmation --  │         │
 * │  │ symbol/symbolToken       │               │  not this one)           │         │
 * │  │ timeframe                │               │                          │         │
 * │  └──────────────────────────┘               └──────────────────────────┘         │
 * │                                                                                    │
 * │  STOCHASTIC is a pure indicator-driven strategy: it reacts only to %K/%D          │
 * │  (+ optional ADX). The `pattern` argument is accepted to satisfy the              │
 * │  TradingStrategy interface but is never read here.                                │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * STRATEGY FLOW
 * ═════════════
 *
 *              ┌─────────────────────┐        ┌─────────────────────┐
 *              │   IndicatorEvent    │        │    PatternEvent      │
 *              │  stochK, stochD,    │        │    (received,        │
 *              │        adx          │        │      unused)         │
 *              │  [ONLY input used]  │        │                      │
 *              └──────────┬──────────┘        └──────────────────────┘
 *                         │
 *                         ▼
 *              ┌───────────────────────────┐
 *              │   Evaluate BUY condition   │   (checked first)
 *              │  stochK < 20  OR           │
 *              │  stochK > stochD           │
 *              └──────────────┬─────────────┘
 *                    true │        │ false
 *                         ▼        ▼
 *                  signal = BUY   ┌───────────────────────────┐
 *                  confidence=60  │  Evaluate SELL condition   │
 *                         │       │  stochK > 80  OR           │
 *                         │       │  stochK < stochD           │
 *                         │       └──────────────┬─────────────┘
 *                         │             true │        │ false
 *                         │                  ▼        ▼
 *                         │           signal = SELL   signal stays HOLD
 *                         │           confidence=60           │
 *                         ▼                  ▼                │
 *                  ┌────────────┐    ┌────────────┐            │
 *                  │ ADX > 25 ? │    │ ADX > 25 ? │            │
 *                  │(checked    │    │(checked    │            │
 *                  │ only for   │    │ only for   │            │
 *                  │ this leg)  │    │ this leg)  │            │
 *                  └─────┬──────┘    └─────┬──────┘            │
 *                  yes│      │no     yes│      │no             │
 *                     ▼      │          ▼      │               │
 *                confidence  │     confidence  │               │
 *                +=20 (=80)  │     +=20 (=80)  │               │
 *                     │      │          │      │               │
 *                     └───┬──┘          └───┬──┘               │
 *                         ▼                 ▼                  ▼
 *                          confidence = min(confidence, 100)  return Optional.empty()
 *                                     │                        (ADX check never
 *                                     ▼                         reached here)
 *                          build & return SignalEvent
 *
 * CONFIDENCE SCORING (max 100)
 * ─────────────────────────────
 *   Base Stochastic signal ... 60   (either direction, from IndicatorEvent)
 *   ADX confirmation .......... +20  (ADX > 25, direction-agnostic)
 *   Cap ........................ 100
 *   Note: ADX confirmation is a bonus, not a requirement -- a plain
 *   Stochastic signal alone still produces a 60-confidence signal.
 *
 * SIGNAL RULES
 * ────────────
 *   BUY  : stochK < 20 (oversold) OR stochK > stochD (bullish crossover)
 *          -- checked FIRST; see IMPORTANT note above
 *   SELL : stochK > 80 (overbought) OR stochK < stochD (bearish crossover)
 *          -- only reached if the BUY condition above is false
 *   HOLD : neither condition matches -> no signal emitted (Optional.empty())
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

        // STOCHASTIC OVERSOLD/CROSSOVER (Primary Momentum Signal - 60 points base)
        // USED: %K < 20 = oversold (mean reversion up likely)
        //       %K > %D = bullish crossover (fast momentum above slow)
        // LOGIC: Dual conditions capture both extremes and momentum direction.
        // NOTE: this OR is checked first -- if a bar is overbought (%K > 80)
        // but %K is still above %D, it lands here as BUY, not in the SELL
        // branch below (see IMPORTANT note in the class javadoc).
        if (indicator.getStochK() < 20 || indicator.getStochK() > indicator.getStochD()) {
            signal = "BUY";
            confidence = 60;

            // ADX TREND STRENGTH CONFIRMATION (+20 points if valid)
            // USED: ADX > 25 validates trend environment
            // PREVENTS: Stochastic whipsaws in choppy range-bound markets
            if (indicator.getAdx() != null && indicator.getAdx() > 25) {
                confidence += 20;
            }
        }
        // STOCHASTIC OVERBOUGHT/CROSSOVER (Primary Momentum Signal - 60 points base)
        // USED: %K > 80 = overbought (mean reversion down likely)
        //       %K < %D = bearish crossover (fast momentum below slow)
        // LOGIC: Dual conditions capture both extremes and momentum reversal.
        // Only reached when the BUY condition above did not match.
        else if (indicator.getStochK() > 80 || indicator.getStochK() < indicator.getStochD()) {
            signal = "SELL";
            confidence = 60;

            // ADX TREND STRENGTH CONFIRMATION (+20 points if valid)
            // USED: ADX > 25 validates trend environment
            // PREVENTS: Stochastic whipsaws in choppy range-bound markets
            if (indicator.getAdx() != null && indicator.getAdx() > 25) {
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
                .reason("Stochastic crossover generated " + signal + ": %K=" + indicator.getStochK()
                    + ", %D=" + indicator.getStochD())
                .timestamp(Instant.now())
                .build());
    }
}