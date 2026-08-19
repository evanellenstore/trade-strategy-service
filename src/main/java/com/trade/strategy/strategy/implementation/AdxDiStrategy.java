package com.trade.strategy.strategy.implementation;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.trade.strategy.dto.IndicatorEvent;
import com.trade.strategy.dto.PatternEvent;
import com.trade.strategy.dto.SignalEvent;
import com.trade.strategy.strategy.TradingStrategy;

/**
 * ADX_DI Trading Strategy.
 *
 * Generates BUY or SELL signals using ADX + Directional Indicators (+DI / -DI)
 * to catch confirmed trend moves, with an optional confidence boost (or veto)
 * from a matching chart pattern.
 *
 * ┌────────────────────────────────── WHY ADX + DI ──────────────────────────────────┐
 * │ • +DI / -DI show directional bias (uptrend vs downtrend pressure)                │
 * │ • ADX measures trend strength, independent of direction (>25 = valid trend)      │
 * │ • +DI > -DI with ADX > 25  -> confirmed uptrend   (BUY)                          │
 * │ • -DI > +DI with ADX > 25  -> confirmed downtrend (SELL)                         │
 * │ • Combines direction AND strength in a single indicator system                   │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY ADX > 40 CONFIRMATION ─────────────────────────┐
 * │ • ADX > 25 is just "trending"; ADX > 40 is "strongly trending"                   │
 * │ • Distinguishes a fresh/weak trend from an undeniably strong one                 │
 * │ • Adds +15 points on top of the base 70 when the trend is exceptionally strong   │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY PATTERN CONFIRMATION ──────────────────────────┐
 * │ • A bullish pattern (BullFlag, DoubleBottom, CupAndHandle, ...) backs up a BUY   │
 * │ • A bearish pattern (BearFlag, DoubleTop, HeadAndShoulders, ...) backs up a SELL │
 * │ • Matching bias  -> +15 pts (2nd, independent confirmation from price structure) │
 * │ • Conflicting bias -> VETO the signal (DI math and chart structure disagree)     │
 * │ • Neutral pattern (e.g. Rectangle) or none at all -> no effect either way        │
 * │ • Only applied when the pattern is on the SAME symbol as the indicator event     │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY NOT OTHER INDICATORS ──────────────────────────┐
 * │ • RSI               -> oscillator/reversal tool; fights a trend-following system │
 * │ • MACD               -> similar trend concept but slower to react than ADX+DI    │
 * │ • Moving Averages    -> less precise on direction than the +DI/-DI lines         │
 * │ • Stochastic         -> mean-reversion tool; wrong fit for a trend strategy      │
 * │ • Bollinger Bands    -> reversal/band system, not a trend-confirmation system    │
 * │ • Price alone        -> needs ADX/DI's trend structure to mean anything          │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────── INPUTS ───────────────────────────────────────┐
 * │                                                                                    │
 * │   IndicatorEvent  [DRIVES the signal]        PatternEvent  [CONFIRMS the signal]  │
 * │  ┌──────────────────────────┐               ┌──────────────────────────┐         │
 * │  │ adx        -- required   │               │ patternName -- optional, │         │
 * │  │ adxDIPlus  -- required   │               │   +15 pts if it agrees   │         │
 * │  │ adxDIMinus -- required   │               │   with the signal        │         │
 * │  │ symbol/symbolToken       │               │   direction (bullish for │         │
 * │  │ timeframe, price         │               │   BUY, bearish for SELL);│         │
 * │  │                           │               │   VETOES the signal if  │         │
 * │  │                           │               │   it opposes it         │         │
 * │  │                           │               │ symbol -- must match     │         │
 * │  │                           │               │   indicator.getSymbol()  │         │
 * │  └──────────────────────────┘               └──────────────────────────┘         │
 * │                                                                                    │
 * │  Direction (BUY/SELL/HOLD) is decided ENTIRELY by ADX + DI.                       │
 * │  The pattern is a confirmation layer on top -- it can add points or veto,         │
 * │  but it can never create a signal on its own.                                     │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * STRATEGY FLOW
 * ═════════════
 *
 *              ┌─────────────────────┐        ┌─────────────────────┐
 *              │   IndicatorEvent    │        │    PatternEvent      │
 *              │  adx, +DI, -DI      │        │    patternName        │
 *              │  [decides BUY/SELL] │        │  [confirms only,     │
 *              │                     │        │   used further below]│
 *              └──────────┬──────────┘        └───────────┬──────────┘
 *                         │                                │
 *                         ▼                                │
 *              ┌───────────────────────────┐               │
 *              │   ADX > 25 ?  (else HOLD)  │               │
 *              └──────────────┬─────────────┘               │
 *                              │                              │
 *          ┌───────────────────┴───────────────────┐          │
 *          ▼                                        ▼          │
 *   +DI > -DI                               -DI > +DI          │
 *   (uptrend pressure)                      (downtrend pressure)│
 *          │                                        │          │
 *          ▼                                        ▼          │
 *   signal = BUY                            signal = SELL      │
 *   confidence = 70                         confidence = 70     │
 *          │                                        │          │
 *          ▼                                        ▼          │
 *   ADX > 40 ?                              ADX > 40 ?          │
 *   yes -> +15 (=85)                        yes -> +15 (=85)    │
 *   no  -> unchanged                        no  -> unchanged    │
 *          │                                        │          │
 *          └───────────────────┬────────────────────┘          │
 *                              ▼                                │
 *              ┌──────────────────────────────────┐             │
 *              │ pattern present + same symbol?    │◄────────────┘
 *              └──────────────────┬─────────────────┘
 *                     no / null   │  yes
 *                        │        ▼
 *                        │  ┌────────────────────────────┐
 *                        │  │ pattern bias vs. signal dir │
 *                        │  └───────┬──────────┬──────────┘
 *                        │      MATCHES   NEUTRAL/CONFLICTS
 *                        │          │              │
 *                        │          ▼              ▼
 *                        │   confidence += 15   NEUTRAL: unchanged
 *                        │   (=> up to 100)     CONFLICTS: VETO
 *                        │                      (return Optional.empty())
 *                        ▼              │
 *                 unchanged             │
 *                        │              │
 *                        └──────┬───────┘
 *                               ▼
 *              Neither DI condition met (tie)?
 *              signal stays HOLD -> return Optional.empty()
 *                               │
 *                               ▼
 *              confidence = min(confidence, 100)
 *                               │
 *                               ▼
 *                    build & return SignalEvent
 *
 * CONFIDENCE SCORING (max 100)
 * ─────────────────────────────
 *   Base DI direction confirmed ... 70   (either direction, ADX > 25 required)
 *   ADX strength confirmation ..... +15  (ADX > 40)
 *   Pattern confirmation .......... +15  (matching bullish/bearish pattern, same symbol)
 *   Cap ............................ 100
 *   Note: ADX-strength and pattern confirmation are both bonuses, not requirements --
 *   a plain DI cross above ADX 25 still produces a 70-confidence signal; the two
 *   bonuses can stack together up to the 100 cap. A conflicting pattern, however,
 *   is treated as disqualifying and vetoes the signal outright.
 *
 * SIGNAL RULES
 * ────────────
 *   BUY  : +DI > -DI and ADX > 25   (confirmed uptrend)
 *   SELL : -DI > +DI and ADX > 25   (confirmed downtrend)
 *   HOLD : ADX <= 25, or +DI == -DI -> no signal emitted (Optional.empty())
 *   VETO : DI/ADX says BUY or SELL, but a same-symbol pattern shows the
 *          opposite bias -> no signal emitted (Optional.empty())
 */
@Component
public class AdxDiStrategy implements TradingStrategy {

    // Patterns treated as bullish (favor/confirm a BUY signal)
    private static final Set<String> BULLISH_PATTERNS = Set.of(
            "AscendingTriangle",
            "BullFlag",
            "CupAndHandle",
            "DoubleBottom",
            "FallingWedge",
            "InverseHeadAndShoulders",
            "ResistanceBreakout"
    );

    // Patterns treated as bearish (favor/confirm a SELL signal)
    private static final Set<String> BEARISH_PATTERNS = Set.of(
            "BearFlag",
            "DescendingTriangle",
            "DoubleTop",
            "HeadAndShoulders",
            "RisingWedge"
    );

    // Patterns with no inherent directional bias (e.g. Rectangle) are treated as neutral
    // by default: anything not in BULLISH_PATTERNS or BEARISH_PATTERNS is ignored.

    private static final int PATTERN_CONFIRMATION_BONUS = 15;

    @Override
    public String getName() {
        return "ADX_DI";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getAdx() == null
                || indicator.getAdxDIPlus() == null || indicator.getAdxDIMinus() == null) {
            return Optional.empty();
        }

        // ┌─────────────────────────── ADX MINIMUM THRESHOLD CHECK ───────────────────────────┐
        // │ USED: ADX must be > 25 to signal a valid trend (strict requirement)               │
        // │ PREVENTS: taking signals in range-bound markets with no directional bias          │
        // └────────────────────────────────────────────────────────────────────────────────────┘
        if (indicator.getAdx() <= 25) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;

        // ┌──────────────────────── DIRECTIONAL INDICATOR CHECK (+DI vs -DI) ─────────────────┐
        // │ USED: +DI > -DI = uptrend confirmed (bullish directional pressure)                │
        // │       -DI > +DI = downtrend confirmed (bearish directional pressure)              │
        // │ LOGIC: DI lines show which direction is controlling the market                    │
        // └────────────────────────────────────────────────────────────────────────────────────┘
        if (indicator.getAdxDIPlus() > indicator.getAdxDIMinus()) {
            signal = "BUY";
            confidence = 70;
        } else if (indicator.getAdxDIMinus() > indicator.getAdxDIPlus()) {
            signal = "SELL";
            confidence = 70;
        }

        if ("HOLD".equals(signal)) {
            return Optional.empty();
        }

        // ┌─────────────────── VERY STRONG TREND CONFIRMATION (+15 if ADX > 40) ──────────────┐
        // │ USED: ADX > 40 indicates an exceptionally strong trend (not just a valid one)     │
        // │ BENEFITS: higher confidence in the strongest trending environments                │
        // └────────────────────────────────────────────────────────────────────────────────────┘
        if (indicator.getAdx() > 40) {
            confidence += 15;
        }

        // ┌──────────────────────────── PATTERN CONFIRMATION CHECK ───────────────────────────┐
        // │ USED: cross-checks the DI-derived direction against an independently detected     │
        // │       chart pattern on the same symbol                                            │
        // │ PREVENTS: firing a signal when price structure (pattern) contradicts the          │
        // │           indicator-derived direction                                             │
        // │                                                                                     │
        // │   pattern present + same symbol? --NO--> unchanged                                 │
        // │              │ YES                                                                  │
        // │              ▼                                                                      │
        // │   pattern bias vs. signal direction                                                 │
        // │      ┌─────────┴─────────┐                                                          │
        // │   MATCHES          NEUTRAL/CONFLICTS                                                │
        // │      │                    │                                                         │
        // │      ▼                    ▼                                                         │
        // │ confidence += 15    NEUTRAL: unchanged                                              │
        // │                     CONFLICTS: veto (return Optional.empty())                       │
        // └────────────────────────────────────────────────────────────────────────────────────┘
        if (pattern != null && matchesSymbol(indicator, pattern)) {
            String patternType = pattern.getPatternName() == null ? null : pattern.getPatternName();

            boolean isBullishPattern = BULLISH_PATTERNS.contains(patternType);
            boolean isBearishPattern = BEARISH_PATTERNS.contains(patternType);

            if ("BUY".equals(signal)) {
                if (isBullishPattern) {
                    confidence += PATTERN_CONFIRMATION_BONUS;
                } else if (isBearishPattern) {
                    // Conflicting evidence: DI says uptrend, pattern says bearish structure
                    return Optional.empty();
                }
            } else { // SELL
                if (isBearishPattern) {
                    confidence += PATTERN_CONFIRMATION_BONUS;
                } else if (isBullishPattern) {
                    // Conflicting evidence: DI says downtrend, pattern says bullish structure
                    return Optional.empty();
                }
            }
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
                .reason("Directional movement produced " + signal + ": ADX=" + indicator.getAdx()
                    + ", +DI=" + indicator.getAdxDIPlus() + ", -DI=" + indicator.getAdxDIMinus())
                .timestamp(Instant.now())
                .build());
    }

    // ┌──────────────────────────────── SYMBOL MATCH CHECK ───────────────────────────────┐
    // │ USED: confirms the pattern event refers to the same instrument as the indicator   │
    // │ PREVENTS: applying a pattern detected on one symbol to a signal on another one    │
    // │ LOGIC: PatternEvent only exposes symbol (no symbolToken), so match on symbol name │
    // └────────────────────────────────────────────────────────────────────────────────────┘
    private boolean matchesSymbol(IndicatorEvent indicator, PatternEvent pattern) {
        return indicator.getSymbol() != null && indicator.getSymbol().equals(pattern.getSymbol());
    }
}