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
 * MACD Trading Strategy.
 *
 * Generates BUY or SELL signals from the relationship between the MACD line
 * and its Signal line, confirmed by ADX trend strength and RSI momentum.
 *
 * ┌───────────────────────────────── WHY MACD ────────────────────────────────────────┐
 * │ • Combines trend following with momentum detection in one indicator              │
 * │ • MACD crosses above Signal -> bullish momentum confirmation (BUY)               │
 * │ • MACD crosses below Signal -> bearish momentum reversal (SELL)                  │
 * │ • Smoothed by design -> fewer false signals than reacting to raw price noise     │
 * │ • Well suited to trending markets                                                │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY ADX CONFIRMATION ──────────────────────────────┐
 * │ • ADX > 25 validates the MACD crossover is happening in a real trend,            │
 * │   not a range-bound/choppy market                                                │
 * │ • MACD alone measures directional bias; ADX adds trend STRENGTH                  │
 * │ • Filters out weak crossovers that would whipsaw in sideways conditions          │
 * │ • Optional: absent/weak ADX does NOT block the signal, only the +20             │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY RSI CONFIRMATION ──────────────────────────────┐
 * │ • Secondary, independent momentum confirmation (overbought/oversold gauge)       │
 * │ • RSI14 > 60 for BUY  -> validates the bullish momentum is genuinely strong       │
 * │ • RSI14 < 40 for SELL -> validates the bearish momentum is genuinely strong       │
 * │ • Guards against a counter-trend trap: MACD crosses but underlying momentum      │
 * │   is actually weakening                                                          │
 * │ • Optional: absent/non-confirming RSI does NOT block the signal, only the +10   │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY NOT OTHER INDICATORS ──────────────────────────┐
 * │ • Raw price alone        -> too noisy without MACD's smoothing                   │
 * │ • Bollinger Bands        -> redundant here; this is trend confirmation, not      │
 * │                              mean-reversion price-extreme detection              │
 * │ • Moving Averages         -> slower than MACD and a similar underlying concept   │
 * │ • Pivot levels            -> fixed session levels, not ideal for a trend-        │
 * │                              following strategy like this one                    │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────── INPUTS ───────────────────────────────────────┐
 * │                                                                                    │
 * │   IndicatorEvent  [DRIVES the signal]        PatternEvent  [IGNORED]              │
 * │  ┌──────────────────────────┐               ┌──────────────────────────┐         │
 * │  │ macd        -- required  │               │ patternName  -- n/a      │         │
 * │  │ macdSignal  -- required  │               │ (chart-pattern signals   │         │
 * │  │ adx         -- optional, │               │  belong to pattern-based │         │
 * │  │   +20 pts, direction-    │               │  strategies, or those    │         │
 * │  │   agnostic               │               │  that explicitly add     │         │
 * │  │ rsi14       -- optional, │               │  pattern confirmation --  │         │
 * │  │   +10 pts if it agrees   │               │  not this one)           │         │
 * │  │   with the signal        │               │                          │         │
 * │  │   direction               │               │                          │         │
 * │  │ price                     │               │                          │         │
 * │  │ symbol/symbolToken       │               │                          │         │
 * │  │ timeframe                │               │                          │         │
 * │  └──────────────────────────┘               └──────────────────────────┘         │
 * │                                                                                    │
 * │  MACD is a pure indicator-driven strategy: it reacts only to MACD vs. Signal      │
 * │  (+ optional ADX + optional RSI). The `pattern` argument is accepted to satisfy   │
 * │  the TradingStrategy interface but is never read here.                            │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * STRATEGY FLOW
 * ═════════════
 *
 *              ┌─────────────────────┐        ┌─────────────────────┐
 *              │   IndicatorEvent    │        │    PatternEvent      │
 *              │ macd, macdSignal,   │        │    (received,        │
 *              │    adx, rsi14       │        │      unused)         │
 *              │  [ONLY input used]  │        │                      │
 *              └──────────┬──────────┘        └──────────────────────┘
 *                         │
 *                         ▼
 *              ┌───────────────────────────┐
 *              │ Compare MACD to Signal line │
 *              └──────────────┬─────────────┘
 *                              │
 *          ┌───────────────────┴───────────────────┐
 *          ▼                                        ▼
 *   MACD > Signal                            MACD < Signal
 *   (bullish crossover)                      (bearish crossover)
 *          │                                        │
 *          ▼                                        ▼
 *   signal = BUY                            signal = SELL
 *   confidence = 60                         confidence = 60
 *          │                                        │
 *          └───────────────────┬────────────────────┘
 *                              ▼
 *              ┌──────────────────────────────────┐
 *              │           ADX > 25 ?               │
 *              │ (direction-agnostic -- just         │
 *              │  confirms a trend exists, either way)│
 *              └──────────────────┬─────────────────┘
 *                    yes │              │ no / not present
 *                        ▼              ▼
 *                 confidence += 20     unchanged
 *                 (=> 80)
 *                        │              │
 *                        └──────┬───────┘
 *                               ▼
 *              ┌──────────────────────────────────┐
 *              │   RSI14 present?                   │
 *              │  BUY  -> RSI14 > 60 ?              │
 *              │  SELL -> RSI14 < 40 ?              │
 *              └──────────────────┬─────────────────┘
 *                    yes │              │ no / not present
 *                        ▼              ▼
 *                 confidence += 10     unchanged
 *                 (=> up to 90)
 *                        │              │
 *                        └──────┬───────┘
 *                               ▼
 *              Neither crossover condition met (MACD == Signal)?
 *              signal stays HOLD -> return Optional.empty()
 *                               │
 *                               ▼
 *              confidence = min(confidence, 100)
 *              (real max here is 90, cap never actually reached)
 *                               │
 *                               ▼
 *                    build & return SignalEvent
 *
 * CONFIDENCE SCORING (max 100, real max reached = 90)
 * ─────────────────────────────────────────────────────
 *   Base MACD crossover ....... 60   (either direction, from IndicatorEvent)
 *   ADX confirmation .......... +20  (ADX > 25, direction-agnostic)
 *   RSI confirmation ........... +10  (RSI14 > 60 for BUY, RSI14 < 40 for SELL)
 *   Cap ......................... 100  (unreachable with this scoring)
 *   Note: ADX and RSI confirmation are both bonuses, not requirements -- a
 *   plain MACD crossover alone still produces a 60-confidence signal; the
 *   two bonuses can stack together up to 90.
 *
 * SIGNAL RULES
 * ────────────
 *   BUY  : MACD > Signal  (bullish crossover -> upward momentum)
 *   SELL : MACD < Signal  (bearish crossover -> downward momentum)
 *   HOLD : MACD == Signal -> no signal emitted (Optional.empty())
 */
@Component
public class MacdStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "MACD";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getMacd() == null || indicator.getMacdSignal() == null) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;

        // MACD CROSSOVER (Primary Trend Signal - 60 points base)
        // USED: MACD > Signal = bullish momentum crossover (fast line above slow)
        //       MACD < Signal = bearish momentum crossover (fast line below slow)
        // LOGIC: Crossovers signal momentum shift; early trend detection
        if (indicator.getMacd() > indicator.getMacdSignal()) {
            signal = "BUY";
            confidence += 60;
        } else if (indicator.getMacd() < indicator.getMacdSignal()) {
            signal = "SELL";
            confidence += 60;
        }

        // ADX TREND STRENGTH CONFIRMATION (+20 points if valid)
        // USED: ADX > 25 validates trend exists (not range-bound market)
        // PREVENTS: Taking MACD signals in flat/choppy environments where they whipsaw
        if (indicator.getAdx() != null && indicator.getAdx() > 25) {
            confidence += 20;
        }

        // RSI MOMENTUM CONFIRMATION (+10 points if aligned)
        // USED: RSI > 60 for BUY confirms strong uptrend momentum
        //       RSI < 40 for SELL confirms strong downtrend momentum
        // PREVENTS: Counter-trend trades where MACD crosses but momentum weakens
        if (indicator.getRsi14() != null) {
            if ("BUY".equals(signal) && indicator.getRsi14() > 60) {
                confidence += 10;
            } else if ("SELL".equals(signal) && indicator.getRsi14() < 40) {
                confidence += 10;
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
                .reason("MACD " + indicator.getMacd() + " is " + signal
                    + " relative to signal line " + indicator.getMacdSignal())
                .timestamp(Instant.now())
                .build());
    }
}