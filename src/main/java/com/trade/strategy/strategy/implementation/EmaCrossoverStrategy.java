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
 * EMA_CROSSOVER Trading Strategy.
 *
 * Generates BUY or SELL signals by comparing the 20-period EMA (fast) to the
 * 50-period EMA (slow), confirmed by RSI momentum, ADX trend strength, and a
 * matching chart pattern -- the most heavily-confirmed strategy in the suite.
 *
 * ┌────────────────────────────── WHY EMA CROSSOVER ─────────────────────────────────┐
 * │ • EMA20 crosses above EMA50 -> fast MA above slow MA -> bullish trend start      │
 * │ • EMA20 crosses below EMA50 -> fast MA below slow MA -> bearish trend start      │
 * │ • Moving averages smooth price action, reducing false signals from noise        │
 * │ • EMA weights recent price more than SMA -> more responsive to fresh moves       │
 * │ • Works well across short-term timeframes                                        │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY RSI CONFIRMATION ──────────────────────────────┐
 * │ • Confirms the momentum direction behind the EMA crossover                       │
 * │ • RSI14 > 60 for BUY  -> validates strong buying momentum                        │
 * │ • RSI14 < 40 for SELL -> validates strong selling momentum                       │
 * │ • Guards against taking a crossover whose underlying momentum is weak            │
 * │ • Optional: absent/non-confirming RSI does NOT block the signal, only the +20   │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY ADX CONFIRMATION ──────────────────────────────┐
 * │ • ADX > 25 confirms a valid trending environment (not sideways/choppy)           │
 * │ • EMA crossovers in low-ADX conditions tend to whipsaw back and forth            │
 * │ • Direction-agnostic: it validates trend STRENGTH, not direction                 │
 * │ • Optional: absent/weak ADX does NOT block the signal, only the +20             │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY PATTERN CONFIRMATION ──────────────────────────┐
 * │ • A bullish chart pattern name (contains "bull"/"up") backs up a BUY             │
 * │ • A bearish chart pattern name (contains "bear"/"down") backs up a SELL          │
 * │ • Adds a directional-bias confirmation beyond pure moving-average math           │
 * │ • Matched by simple case-insensitive substring on PatternEvent.patternName       │
 * │ • Optional: absent/non-matching pattern does NOT block the signal, only the +15  │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY NOT OTHER INDICATORS ──────────────────────────┐
 * │ • VWAP                    -> similar trend concept, but less responsive to       │
 * │                              fresh trend changes than a fast/slow EMA pair       │
 * │ • Raw price alone         -> too noisy; EMA provides the needed smoothing        │
 * │ • Bollinger Bands         -> built for mean reversion, not trend-following       │
 * │ • Stochastic              -> redundant with RSI for momentum confirmation        │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────── INPUTS ───────────────────────────────────────┐
 * │                                                                                    │
 * │   IndicatorEvent  [DRIVES the signal]        PatternEvent  [CONFIRMS the signal]  │
 * │  ┌──────────────────────────┐               ┌──────────────────────────┐         │
 * │  │ ema20       -- required  │               │ patternName -- optional, │         │
 * │  │ ema50       -- required  │               │   +15 pts if it agrees   │         │
 * │  │ rsi14       -- optional, │               │   with the signal        │         │
 * │  │   +20 pts if it agrees   │               │   direction (bull/up for │         │
 * │  │   with the signal        │               │   BUY, bear/down for     │         │
 * │  │   direction               │               │   SELL)                 │         │
 * │  │ adx         -- optional, │               │                          │         │
 * │  │   +20 pts, direction-    │               │  Never decides BUY vs.   │         │
 * │  │   agnostic               │               │  SELL vs. HOLD by itself │         │
 * │  │ price                     │               │                          │         │
 * │  │ symbol/symbolToken       │               │                          │         │
 * │  │ timeframe                │               │                          │         │
 * │  └──────────────────────────┘               └──────────────────────────┘         │
 * │                                                                                    │
 * │  Direction (BUY/SELL/HOLD) is decided ENTIRELY by EMA20 vs. EMA50. RSI, ADX,      │
 * │  and the chart pattern are all optional confirmations layered on top -- each      │
 * │  can add points, none can create or block a signal on its own.                   │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * STRATEGY FLOW
 * ═════════════
 *
 *              ┌─────────────────────┐        ┌─────────────────────┐
 *              │   IndicatorEvent    │        │    PatternEvent      │
 *              │ ema20, ema50, rsi14,│        │    patternName       │
 *              │        adx          │        │  [confirms only,     │
 *              │  [decides BUY/SELL] │        │   used further below]│
 *              └──────────┬──────────┘        └───────────┬──────────┘
 *                         │                                │
 *                         ▼                                │
 *              ┌───────────────────────────┐               │
 *              │  Compare EMA20 to EMA50    │               │
 *              └──────────────┬─────────────┘               │
 *                              │                              │
 *          ┌───────────────────┴───────────────────┐          │
 *          ▼                                        ▼          │
 *   EMA20 > EMA50                           EMA20 < EMA50      │
 *   (fast above slow -> uptrend)            (fast below slow -> downtrend)│
 *          │                                        │          │
 *          ▼                                        ▼          │
 *   signal = BUY                            signal = SELL      │
 *   confidence = 25                         confidence = 25     │
 *          │                                        │          │
 *          └───────────────────┬────────────────────┘          │
 *                              ▼                                │
 *              ┌──────────────────────────────────┐             │
 *              │   RSI14 present?                   │             │
 *              │  BUY  -> RSI14 > 60 ?              │             │
 *              │  SELL -> RSI14 < 40 ?              │             │
 *              └──────────────────┬─────────────────┘             │
 *                    yes │              │ no / not present         │
 *                        ▼              ▼                          │
 *                 confidence += 20     unchanged                    │
 *                 (=> 45)                                           │
 *                        │              │                           │
 *                        └──────┬───────┘                           │
 *                               ▼                                   │
 *              ┌──────────────────────────────────┐                 │
 *              │           ADX > 25 ?               │                 │
 *              │ (direction-agnostic)                │                 │
 *              └──────────────────┬─────────────────┘                 │
 *                    yes │              │ no / not present             │
 *                        ▼              ▼                              │
 *                 confidence += 20     unchanged                        │
 *                 (=> 65)                                               │
 *                        │              │                               │
 *                        └──────┬───────┘                               │
 *                               ▼                                       │
 *              ┌──────────────────────────────────┐                     │
 *              │ patternName contains "bull"/"up"  │◄────────────────────┘
 *              │   (checked only if signal = BUY)  │
 *              │ patternName contains "bear"/"down"│
 *              │   (checked only if signal = SELL) │
 *              └──────────────────┬─────────────────┘
 *                    yes │              │ no / null pattern
 *                        ▼              ▼
 *                 confidence += 15     unchanged
 *                 (=> up to 80)
 *                        │              │
 *                        └──────┬───────┘
 *                               ▼
 *              Neither EMA condition met (EMA20 == EMA50)?
 *              signal stays HOLD -> return Optional.empty()
 *                               │
 *                               ▼
 *              confidence = min(confidence, 100)
 *              (real max here is 80, cap never actually reached)
 *                               │
 *                               ▼
 *                    build & return SignalEvent
 *
 * CONFIDENCE SCORING (max 100, real max reached = 80)
 * ─────────────────────────────────────────────────────
 *   Base EMA crossover ........ 25   (either direction, from IndicatorEvent)
 *   RSI confirmation ........... +20  (RSI14 > 60 for BUY, RSI14 < 40 for SELL)
 *   ADX confirmation ........... +20  (ADX > 25, direction-agnostic)
 *   Pattern confirmation ........ +15  (bullish/"up" pattern for BUY, bearish/"down" for SELL)
 *   Cap ........................... 100  (unreachable with this scoring)
 *   Note: RSI, ADX, and pattern confirmation are all bonuses, not
 *   requirements -- a plain EMA crossover alone still produces a
 *   25-confidence signal; all three bonuses can stack together up to 80.
 *
 * SIGNAL RULES
 * ────────────
 *   BUY  : EMA20 > EMA50  (fast MA above slow MA -> uptrend)
 *   SELL : EMA20 < EMA50  (fast MA below slow MA -> downtrend)
 *   HOLD : EMA20 == EMA50 -> no signal emitted (Optional.empty())
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

        // EMA CROSSOVER (Primary Trend Direction - 25 points base)
        // USED: EMA20 > EMA50 = fast MA above slow MA = uptrend established
        //       EMA20 < EMA50 = fast MA below slow MA = downtrend established
        // LOGIC: Smooth price action; responsive to trend changes; reduces noise
        if (indicator.getEma20() > indicator.getEma50()) {
            confidence += 25;
            signal = "BUY";
        } else if (indicator.getEma20() < indicator.getEma50()) {
            confidence += 25;
            signal = "SELL";
        }

        // RSI MOMENTUM CONFIRMATION (+20 points if aligned)
        // USED: RSI > 60 for BUY confirms strong bullish momentum
        //       RSI < 40 for SELL confirms strong bearish momentum
        // PREVENTS: Weak crossovers with declining momentum
        if (indicator.getRsi14() != null) {
            if ("BUY".equals(signal) && indicator.getRsi14() > 60) {
                confidence += 20;
            } else if ("SELL".equals(signal) && indicator.getRsi14() < 40) {
                confidence += 20;
            }
        }

        // ADX TREND STRENGTH CONFIRMATION (+20 points if valid)
        // USED: ADX > 25 validates a strong trend exists
        // PREVENTS: False EMA crossovers in choppy/sideways markets
        if (indicator.getAdx() != null && indicator.getAdx() > 25) {
            confidence += 20;
        }

        // PATTERN CONFIRMATION (+15 points if matched)
        // USED: Bullish/bearish patterns validate EMA signal direction
        // NOT USED alone: Patterns subjective; need price/EMA action confirmation
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
                .reason("EMA crossover produced " + signal + ": EMA20=" + indicator.getEma20()
                    + ", EMA50=" + indicator.getEma50())
                .timestamp(Instant.now())
                .build());
    }
}