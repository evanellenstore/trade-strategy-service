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
 * VWAP_BREAKOUT Trading Strategy.
 *
 * Generates BUY or SELL signals by comparing the current market price to the
 * Volume Weighted Average Price (VWAP), confirmed by a matching chart
 * pattern and RSI momentum.
 *
 * ┌───────────────────────────────── WHY VWAP ────────────────────────────────────────┐
 * │ • VWAP = average price weighted by traded volume for the session                 │
 * │ • Price > VWAP -> buyers are paying above the "fair" volume-weighted level        │
 * │   -> intraday strength -> breakout UP (BUY)                                      │
 * │ • Price < VWAP -> sellers are pushing below the "fair" level                      │
 * │   -> intraday weakness -> breakdown DOWN (SELL)                                  │
 * │ • Unlike Bollinger Bands (mean reversion), VWAP is a TREND/breakout reference --   │
 * │   this strategy rides the move rather than fading it                             │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY PATTERN CONFIRMATION ──────────────────────────┐
 * │ • A bullish chart pattern name (contains "bull"/"up") backs up a BUY             │
 * │ • A bearish chart pattern name (contains "bear"/"down") backs up a SELL          │
 * │ • Adds chart-structure confirmation on top of the price/VWAP relationship        │
 * │ • Matched by simple case-insensitive substring on PatternEvent.patternName       │
 * │ • Optional: absent/non-matching pattern does NOT block the signal, only the +15  │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY RSI CONFIRMATION ──────────────────────────────┐
 * │ • RSI14 > 60 backs up a BUY  -> momentum agrees the breakout has strength         │
 * │ • RSI14 < 40 backs up a SELL -> momentum agrees the breakdown has strength        │
 * │ • Note thresholds are 60/40 here (breakout momentum), not 30/70 as in mean-       │
 * │   reversion strategies -- VWAP wants momentum agreement, not an extreme           │
 * │ • Optional: absent/non-confirming RSI does NOT block the signal, only the +15    │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────── INPUTS ───────────────────────────────────────┐
 * │                                                                                    │
 * │   IndicatorEvent  [DRIVES the signal]        PatternEvent  [CONFIRMS the signal]  │
 * │  ┌──────────────────────────┐               ┌──────────────────────────┐         │
 * │  │ price      -- required   │               │ patternName -- optional, │         │
 * │  │ vwap       -- required   │               │   +15 pts if it agrees   │         │
 * │  │ rsi14      -- optional,  │               │   with the signal        │         │
 * │  │   +15 pts if it agrees   │               │   direction (bull/up for │         │
 * │  │   with the signal        │               │   BUY, bear/down for     │         │
 * │  │   direction               │               │   SELL)                 │         │
 * │  │ symbol/symbolToken       │               │                          │         │
 * │  │ timeframe                │               │  Never decides BUY vs.   │         │
 * │  │                          │               │  SELL vs. HOLD by itself │         │
 * │  └──────────────────────────┘               └──────────────────────────┘         │
 * │                                                                                    │
 * │  Direction (BUY/SELL/HOLD) is decided ENTIRELY by price vs. VWAP. Pattern and     │
 * │  RSI are both optional confirmations layered on top -- each can add points,       │
 * │  neither can create or block a signal on its own.                                │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * STRATEGY FLOW
 * ═════════════
 *
 *              ┌─────────────────────┐        ┌─────────────────────┐
 *              │   IndicatorEvent    │        │    PatternEvent      │
 *              │  price, vwap, rsi14 │        │    patternName       │
 *              │  [decides BUY/SELL] │        │  [confirms only,     │
 *              │                     │        │   used further below]│
 *              └──────────┬──────────┘        └───────────┬──────────┘
 *                         │                                │
 *                         ▼                                │
 *              ┌───────────────────────────┐               │
 *              │   Compare price to VWAP    │               │
 *              └──────────────┬─────────────┘               │
 *                              │                              │
 *          ┌───────────────────┴───────────────────┐          │
 *          ▼                                        ▼          │
 *   price > VWAP                            price < VWAP       │
 *   (buying above fair value)               (selling below fair value)│
 *          │                                        │          │
 *          ▼                                        ▼          │
 *   signal = BUY                            signal = SELL      │
 *   confidence = 20                         confidence = 20     │
 *          │                                        │          │
 *          └───────────────────┬────────────────────┘          │
 *                              ▼                                │
 *              ┌──────────────────────────────────┐             │
 *              │ patternName contains "bull"/"up"  │◄────────────┘
 *              │   (checked only if signal = BUY)  │
 *              │ patternName contains "bear"/"down"│
 *              │   (checked only if signal = SELL) │
 *              └──────────────────┬─────────────────┘
 *                    yes │              │ no / null pattern
 *                        ▼              ▼
 *                 confidence += 15     unchanged
 *                        │              │
 *                        └──────┬───────┘
 *                               ▼
 *              ┌──────────────────────────────────┐
 *              │        RSI14 present?              │
 *              │  BUY  -> RSI14 > 60 ?              │
 *              │  SELL -> RSI14 < 40 ?              │
 *              └──────────────────┬─────────────────┘
 *                    yes │              │ no / not present
 *                        ▼              ▼
 *                 confidence += 15     unchanged
 *                 (=> up to 50)
 *                        │              │
 *                        └──────┬───────┘
 *                               ▼
 *              Neither VWAP condition met (price == VWAP)?
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
 *   Base VWAP breakout ....... 20   (either direction, from IndicatorEvent)
 *   Pattern confirmation ..... +15  (bullish/"up" pattern for BUY, bearish/"down" for SELL)
 *   RSI confirmation .......... +15  (RSI14 > 60 for BUY, RSI14 < 40 for SELL)
 *   Cap ........................ 100
 *   Note: pattern and RSI confirmation are both bonuses, not requirements --
 *   a plain VWAP breakout alone still produces a 20-confidence signal; the
 *   two bonuses can stack together (max 50 here, well under the 100 cap).
 *
 * SIGNAL RULES
 * ────────────
 *   BUY  : price > VWAP  (trading above fair value -> breakout up)
 *   SELL : price < VWAP  (trading below fair value -> breakdown down)
 *   HOLD : price == VWAP -> no signal emitted (Optional.empty())
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

        // ── BUY: price above VWAP -> trading above the volume-weighted fair value ──
        // Base 20 pts. Interpreted as intraday strength / breakout up.
        if (indicator.getPrice() > indicator.getVwap()) {
            signal = "BUY";
            confidence += 20;
        }
        // ── SELL: price below VWAP -> trading below the volume-weighted fair value ──
        // Base 20 pts. Interpreted as intraday weakness / breakdown down.
        else if (indicator.getPrice() < indicator.getVwap()) {
            signal = "SELL";
            confidence += 20;
        }

        // ── PATTERN CONFIRMATION (+15 pts) ──
        // Chart-structure confirmation, independent of RSI momentum. Only
        // applied once a direction is already set above; a pattern can never
        // create a signal by itself, only reinforce one that VWAP already gave.
        // Matched by simple case-insensitive keyword on the pattern name
        // (e.g. "BullFlag" -> contains "bull").
        if (pattern != null && pattern.getPatternName() != null) {
            String name = pattern.getPatternName().toLowerCase();
            if ("BUY".equals(signal) && (name.contains("bull") || name.contains("up"))) {
                confidence += 15;
            } else if ("SELL".equals(signal) && (name.contains("bear") || name.contains("down"))) {
                confidence += 15;
            }
        }

        // ── RSI CONFIRMATION (+15 pts) ──
        // Momentum confirmation for the breakout. Thresholds are 60/40 (not
        // 30/70) because this strategy wants proof the move has momentum
        // behind it, not proof of an extreme like a mean-reversion strategy would.
        if (indicator.getRsi14() != null) {
            if ("BUY".equals(signal) && indicator.getRsi14() > 60) {
                confidence += 15;
            } else if ("SELL".equals(signal) && indicator.getRsi14() < 40) {
                confidence += 15;
            }
        }

        // Price sat exactly on VWAP -> no breakout either way -> no signal,
        // regardless of what pattern/RSI said.
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
                .reason("Price " + indicator.getPrice() + " moved " + signal
                    + " relative to VWAP " + indicator.getVwap())
                .timestamp(Instant.now())
                .build());
    }
}