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
 * PIVOT_BREAKOUT Trading Strategy.
 *
 * Generates BUY or SELL signals when price breaks through pivot support/
 * resistance levels, with confidence scaled by how far and how strong the
 * breakout is.
 *
 * ┌────────────────────────────── WHY PIVOT POINTS ──────────────────────────────────┐
 * │ • Pivot = calculated support/resistance level from the previous session          │
 * │ • R1/R2 = resistance levels above pivot (upside targets)                         │
 * │ • S1/S2 = support levels below pivot (downside targets)                          │
 * │ • Price breaks above R1/R2 -> bullish breakout -> trend continuation UP (BUY)    │
 * │ • Price breaks below S1/S2 -> bearish breakout -> trend continuation DOWN (SELL) │
 * │ • Widely watched by institutions -> real confluence, not an arbitrary level      │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY BREAKOUT STRENGTH ─────────────────────────────┐
 * │ • Not all breakouts are equal -- how FAR price pushes past the level matters     │
 * │ • Breaking the 2nd level (R2/S2) = strongest signal -> sustained pressure, +10   │
 * │ • Breaking only the 1st level (R1/S1), any distance beyond it -> +5             │
 * │ • These two bonuses are MUTUALLY EXCLUSIVE per signal -- see IMPORTANT note      │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY NOT OTHER INDICATORS ──────────────────────────┐
 * │ • RSI / Stochastic        -> those are reversal oscillators; this is a           │
 * │                              directional BREAKOUT strategy, not mean reversion   │
 * │ • MACD / EMA              -> pivot levels are simpler, price-level based, no     │
 * │                              moving-average lag                                  │
 * │ • Bollinger Bands         -> dynamic per-bar bands; pivots are fixed, stable,    │
 * │                              institutionally-watched levels instead              │
 * │ • ADX                     -> not needed -- breaking a hard level IS the trend    │
 * │                              confirmation here                                   │
 * │ • Volume                  -> could add value but isn't part of this mechanical,  │
 * │                              price-level-only system                            │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────── IMPORTANT: R2/S2 AND DISTANCE BONUS ARE EXCLUSIVE ─────────────┐
 * │ For a given signal, only ONE of the two bonuses below is ever applied:           │
 * │   • If R2 (or S2) is breached  -> +10, and the distance-from-R1/S1 check is      │
 * │     SKIPPED entirely (it sits in the `else` branch)                              │
 * │   • Otherwise, price is already confirmed past R1 (or S1) -- that's how we       │
 * │     got into this branch -- so the distance is always positive -> +5 always      │
 * │ So a very strong move past R2 gets +10, never +10 AND +5 (=15) together.         │
 * │ Max confidence per signal is therefore 75 + 10 = 85, not 100.                    │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────── INPUTS ───────────────────────────────────────┐
 * │                                                                                    │
 * │   IndicatorEvent  [DRIVES the signal]        PatternEvent  [IGNORED]              │
 * │  ┌──────────────────────────────┐           ┌──────────────────────────┐         │
 * │  │ price              -- required│           │ patternName  -- n/a      │         │
 * │  │ pivotPoint         -- required│           │ (chart-pattern signals   │         │
 * │  │   (existence-checked only,    │           │  belong to pattern-based │         │
 * │  │    value itself isn't compared)│          │  strategies, or those    │         │
 * │  │ pivotResistance1   -- required│           │  that explicitly add     │         │
 * │  │ pivotSupport1      -- required│           │  pattern confirmation --  │         │
 * │  │ pivotResistance2   -- optional,│          │  not this one)           │         │
 * │  │   +10 if breached (BUY side)  │           │                          │         │
 * │  │ pivotSupport2      -- optional,│          │                          │         │
 * │  │   +10 if breached (SELL side) │           │                          │         │
 * │  │ symbol/symbolToken, timeframe │           │                          │         │
 * │  └──────────────────────────────┘           └──────────────────────────┘         │
 * │                                                                                    │
 * │  PIVOT_BREAKOUT is a pure indicator-driven strategy: it reacts only to price vs.  │
 * │  pivot levels. The `pattern` argument is accepted to satisfy the TradingStrategy  │
 * │  interface but is never read here.                                                │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * STRATEGY FLOW
 * ═════════════
 *
 *              ┌─────────────────────┐        ┌─────────────────────┐
 *              │   IndicatorEvent    │        │    PatternEvent      │
 *              │ price, pivotPoint,  │        │    (received,        │
 *              │  R1, S1, R2, S2     │        │      unused)         │
 *              │  [ONLY input used]  │        │                      │
 *              └──────────┬──────────┘        └──────────────────────┘
 *                         │
 *                         ▼
 *              ┌───────────────────────────┐
 *              │   Compare price to R1/S1   │
 *              └──────────────┬─────────────┘
 *                              │
 *          ┌───────────────────┼───────────────────┐
 *          ▼                   ▼                    ▼
 *   price > R1          price between            price < S1
 *   (resistance          S1 and R1               (support
 *    breakout)           (inside range)           breakout)
 *          │                   │                    │
 *          ▼                   ▼                    ▼
 *   signal = BUY         signal = HOLD        signal = SELL
 *   confidence = 75      -> Optional.empty()  confidence = 75
 *          │                                        │
 *          ▼                                        ▼
 *   price > R2 ?                             price < S2 ?
 *  ┌─────┴─────┐                            ┌─────┴─────┐
 * yes           no                         yes           no
 *  │             │                          │             │
 *  ▼             ▼                          ▼             ▼
 * +10        +5 (=80)                      +10        +5 (=80)
 * (=85)     (already past R1,              (=85)      (already past S1,
 *            distance always > 0)                       distance always > 0)
 *  │             │                          │             │
 *  └──────┬──────┘                          └──────┬──────┘
 *         │                                         │
 *         └────────────────────┬────────────────────┘
 *                              ▼
 *              confidence = min(confidence, 100)
 *              (real max here is 85, cap never actually reached)
 *                              │
 *                              ▼
 *                   build & return SignalEvent
 *
 * CONFIDENCE SCORING (max 100, real max reached = 85)
 * ─────────────────────────────────────────────────────
 *   Base R1/S1 breakout ...... 75   (either direction, from IndicatorEvent)
 *   R2/S2 breach .............. +10  (EXCLUSIVE with distance bonus below)
 *   Distance past R1/S1 ....... +5   (only when R2/S2 was NOT breached;
 *                                      always applies in that branch, since
 *                                      being in it already means price is
 *                                      past R1/S1)
 *   Cap ........................ 100  (unreachable with this scoring -- see
 *                                      IMPORTANT note above)
 *
 * SIGNAL RULES
 * ────────────
 *   BUY  : price > R1 (resistance breakout -> bullish continuation)
 *   SELL : price < S1 (support breakout -> bearish continuation)
 *   HOLD : price sits between S1 and R1 -> no signal emitted (Optional.empty())
 */
@Component
public class PivotBreakoutStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "PIVOT_BREAKOUT";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getPivotPoint() == null || indicator.getPrice() == null
                || indicator.getPivotResistance1() == null || indicator.getPivotSupport1() == null) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;

        // RESISTANCE BREAKOUT (BUY Breakout - 75 points base)
        // USED: Price > R1 signals bullish breakout above key resistance
        // LOGIC: Institutional support/resistance; price break = trend continuation
        if (indicator.getPrice() > indicator.getPivotResistance1()) {
            signal = "BUY";
            confidence = 75;
            // SECOND RESISTANCE BREAKOUT (Strongest Signal - +10 points)
            // USED: Price > R2 = very strong upside breakout
            // REASON: Breaking 2nd resistance shows sustained buying pressure
            if (indicator.getPivotResistance2() != null
                    && indicator.getPrice() > indicator.getPivotResistance2()) {
                confidence += 10;
            } else {
                // BREAKOUT DISTANCE CONFIRMATION (+5 points)
                // USED: Reaching this branch already means price > R1 (the
                //       outer condition), so the breakout distance is always
                //       positive -- no need to re-check it, just award the bonus
                // MORE DISTANCE = stronger break = higher conviction
                confidence += 5;
            }
        }
        // SUPPORT BREAKOUT (SELL Breakout - 75 points base)
        // USED: Price < S1 signals bearish breakout below key support
        // LOGIC: Institutional support/resistance; price break = trend continuation
        else if (indicator.getPrice() < indicator.getPivotSupport1()) {
            signal = "SELL";
            confidence = 75;
            // SECOND SUPPORT BREAKOUT (Strongest Signal - +10 points)
            // USED: Price < S2 = very strong downside breakout
            // REASON: Breaking 2nd support shows sustained selling pressure
            if (indicator.getPivotSupport2() != null
                    && indicator.getPrice() < indicator.getPivotSupport2()) {
                confidence += 10;
            } else {
                // BREAKOUT DISTANCE CONFIRMATION (+5 points)
                // USED: Reaching this branch already means price < S1 (the
                //       outer condition), so the breakout distance is always
                //       positive -- no need to re-check it, just award the bonus
                // MORE DISTANCE = stronger break = higher conviction
                confidence += 5;
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
                .reason("Price " + indicator.getPrice() + " broke a pivot " + signal
                    + " level: R1=" + indicator.getPivotResistance1() + ", S1=" + indicator.getPivotSupport1())
                .timestamp(Instant.now())
                .build());
    }
}