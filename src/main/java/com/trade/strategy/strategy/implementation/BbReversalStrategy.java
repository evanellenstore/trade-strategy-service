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
 * BB_REVERSAL Trading Strategy.
 *
 * Generates BUY or SELL signals using Bollinger Bands to catch mean-reversion
 * turning points, confirmed by RSI momentum and (optionally) a matching
 * chart pattern.
 *
 * ┌────────────────────────────── WHY BOLLINGER BANDS ───────────────────────────────┐
 * │ • Upper/Lower bands = dynamic support/resistance (price ± 2 std dev)             │
 * │ • Price at/below lower band  -> oversold zone   -> expect bounce UP (BUY)        │
 * │ • Price at/above upper band  -> overbought zone -> expect pullback DOWN (SELL)   │
 * │ • Bands widen/narrow with volatility, so the "extreme" adapts to the market      │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY RSI CONFIRMATION ──────────────────────────────┐
 * │ • RSI < 30 backs up a BUY  -> momentum agrees price is genuinely oversold        │
 * │ • RSI > 70 backs up a SELL -> momentum agrees price is genuinely overbought      │
 * │ • Without it, a band touch alone can be a false reversal (band walk / breakout)  │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY PATTERN CONFIRMATION ──────────────────────────┐
 * │ • A bullish chart pattern name (contains "bull"/"up") backs up a BUY             │
 * │ • A bearish chart pattern name (contains "bear"/"down") backs up a SELL          │
 * │ • Adds a 2nd, independent confirmation source (chart structure, not momentum)    │
 * │ • Matched by simple case-insensitive substring on PatternEvent.patternName       │
 * │ • Optional: absent/non-matching pattern does NOT block the signal, only the +15  │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY NOT OTHER INDICATORS ──────────────────────────┐
 * │ • MACD / EMA (trend)     -> trend tools fight a mean-reversion setup             │
 * │ • ADX                    -> high ADX = strong trend = bad time to fade the move  │
 * │ • Raw price               -> needs the band's volatility context to mean anything│
 * │ • Fixed pivot levels     -> bands already give a volatility-relative level       │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────── INPUTS ───────────────────────────────────────┐
 * │                                                                                    │
 * │   IndicatorEvent  [DRIVES the signal]        PatternEvent  [CONFIRMS the signal]  │
 * │  ┌──────────────────────────┐               ┌──────────────────────────┐         │
 * │  │ price      -- required   │               │ patternName -- optional, │         │
 * │  │ bbUpper    -- required   │               │   +15 pts if it agrees   │         │
 * │  │ bbLower    -- required   │               │   with the signal        │         │
 * │  │ rsi14      -- optional,  │               │   direction (bull/up for │         │
 * │  │   +20 pts if it agrees   │               │   BUY, bear/down for     │         │
 * │  │   with the signal        │               │   SELL)                 │         │
 * │  │   direction               │               │                          │         │
 * │  │ symbol/symbolToken       │               │  Never decides BUY vs.   │         │
 * │  │ timeframe                │               │  SELL vs. HOLD by itself │         │
 * │  └──────────────────────────┘               └──────────────────────────┘         │
 * │                                                                                    │
 * │  Direction (BUY/SELL/HOLD) is decided ENTIRELY by price vs. Bollinger Bands.      │
 * │  RSI and the chart pattern are both optional confirmations layered on top --      │
 * │  each can add points, neither can create or block a signal on its own.           │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * STRATEGY FLOW
 * ═════════════
 *
 *              ┌─────────────────────┐        ┌─────────────────────┐
 *              │   IndicatorEvent    │        │    PatternEvent      │
 *              │  price, BB, RSI14   │        │    patternName       │
 *              │  [decides BUY/SELL] │        │  [confirms only,     │
 *              │                     │        │   used further below]│
 *              └──────────┬──────────┘        └───────────┬──────────┘
 *                         │                                │
 *                         ▼                                │
 *              ┌───────────────────────────┐               │
 *              │   Compare price to bands   │               │
 *              └──────────────┬─────────────┘               │
 *                              │                              │
 *          ┌───────────────────┴───────────────────┐          │
 *          ▼                                        ▼          │
 *   price <= BB_Lower                       price >= BB_Upper  │
 *   (oversold extreme)                      (overbought extreme)│
 *          │                                        │          │
 *          ▼                                        ▼          │
 *   signal = BUY                            signal = SELL      │
 *   confidence = 65                         confidence = 65     │
 *          │                                        │          │
 *          ▼                                        ▼          │
 *   RSI14 < 30 ?                            RSI14 > 70 ?        │
 *   yes -> +20 (=85)                        yes -> +20 (=85)    │
 *   no  -> unchanged                        no  -> unchanged    │
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
 *                 (=> up to 100)
 *                        │              │
 *                        └──────┬───────┘
 *                               ▼
 *              Neither BB condition met?
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
 *   Base band touch ......... 65   (either direction, from IndicatorEvent)
 *   RSI confirmation ........ +20  (RSI < 30 for BUY, RSI > 70 for SELL)
 *   Pattern confirmation ..... +15  (bullish/"up" pattern for BUY, bearish/"down" for SELL)
 *   Cap ...................... 100
 *   Note: RSI and pattern confirmation are both bonuses, not requirements --
 *   a plain band touch alone still produces a 65-confidence signal; RSI and
 *   pattern can stack together up to the 100 cap.
 *
 * SIGNAL RULES
 * ────────────
 *   BUY  : price <= BB_Lower  (oversold -> expect reversal up)
 *   SELL : price >= BB_Upper  (overbought -> expect reversal down)
 *   HOLD : price is between the bands -> no signal emitted (Optional.empty())
 */
@Component
public class BbReversalStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "BB_REVERSAL";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        // Direction (BUY/SELL/HOLD) comes entirely from price vs. Bollinger
        // Bands below. `pattern` is only consulted afterwards as an optional
        // confirmation bonus -- see WHY PATTERN CONFIRMATION in the class javadoc.
        if (indicator == null || indicator.getBbUpper() == null
                || indicator.getBbLower() == null || indicator.getPrice() == null) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;

        // ── BUY: price at/below lower band -> oversold mean-reversion setup ──
        // Base 65 pts. Support is dynamic (the band), so a touch here suggests
        // a bounce is likely.
        if (indicator.getPrice() <= indicator.getBbLower()) {
            signal = "BUY";
            confidence = 65;

            // RSI < 30 confirms genuine oversold momentum, not just a band
            // touch -> +20 pts. Guards against false reversals (e.g. a band
            // walk during a strong downtrend).
            if (indicator.getRsi14() != null && indicator.getRsi14() < 30) {
                confidence += 20;
            }
        }
        // ── SELL: price at/above upper band -> overbought mean-reversion setup ──
        // Base 65 pts. Resistance is dynamic (the band), so a touch here
        // suggests a pullback is likely.
        else if (indicator.getPrice() >= indicator.getBbUpper()) {
            signal = "SELL";
            confidence = 65;

            // RSI > 70 confirms genuine overbought momentum, not just a band
            // touch -> +20 pts. Guards against false reversals (e.g. a band
            // walk during a strong uptrend).
            if (indicator.getRsi14() != null && indicator.getRsi14() > 70) {
                confidence += 20;
            }
        }

        // ── PATTERN CONFIRMATION (+15 pts) ──
        // Chart-structure confirmation, independent of RSI momentum. Only
        // applied once a direction is already set above; a pattern can never
        // create a signal by itself, only reinforce one that BB already gave.
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

        // Price stayed inside the bands -> no reversal setup -> no signal,
        // regardless of what the pattern said.
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
                .reason("Price " + indicator.getPrice() + " reached the " + signal
                    + " Bollinger Band extreme")
                .timestamp(Instant.now())
                .build());
    }
}