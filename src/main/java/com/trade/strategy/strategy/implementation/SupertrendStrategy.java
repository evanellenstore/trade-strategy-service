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
 * SUPER_TREND Trading Strategy.
 *
 * Generates BUY or SELL signals by comparing the current market price to the
 * SuperTrend indicator line, confirmed by ADX trend-strength.
 *
 * ┌────────────────────────────── WHY SUPERTREND ────────────────────────────────────┐
 * │ • SuperTrend combines Average True Range (ATR) with moving averages              │
 * │ • Result = adaptive, volatility-adjusted dynamic support/resistance band         │
 * │ • Price > SuperTrend -> price is above dynamic support -> uptrend (BUY)          │
 * │ • Price < SuperTrend -> price is below dynamic resistance -> downtrend (SELL)    │
 * │ • Very responsive to trend changes -- adjusts faster than a static MA            │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY ADX CONFIRMATION ──────────────────────────────┐
 * │ • ADX > 25 confirms a real trend exists (not a range-bound/choppy market)        │
 * │ • Strong trend + SuperTrend signal = higher-probability continuation             │
 * │ • Filters whipsaws: SuperTrend can flip often when the trend is weak/uncertain   │
 * │ • Optional: absent/weak ADX does NOT block the signal, only the +20             │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY NOT OTHER INDICATORS ──────────────────────────┐
 * │ • EMA / SMA               -> SuperTrend is more dynamic (ATR-adjusted bands)     │
 * │ • Bollinger Bands         -> fixed std-dev vs. SuperTrend's volatility-adjusted  │
 * │                              band -- different job (mean reversion, not trend)   │
 * │ • Raw price               -> needs the adaptive band for support/resistance      │
 * │                              context                                             │
 * │ • MACD                    -> smoother/slower; SuperTrend reacts faster to trend  │
 * │                              changes                                             │
 * │ • Pivot levels            -> pivots are fixed from the prior session; SuperTrend │
 * │                              tracks the CURRENT trend live                       │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────── INPUTS ───────────────────────────────────────┐
 * │                                                                                    │
 * │   IndicatorEvent  [DRIVES the signal]        PatternEvent  [IGNORED]              │
 * │  ┌──────────────────────────┐               ┌──────────────────────────┐         │
 * │  │ price       -- required  │               │ patternName  -- n/a      │         │
 * │  │ supertrend  -- required  │               │ (chart-pattern signals   │         │
 * │  │ adx         -- optional, │               │  belong to pattern-based │         │
 * │  │   +20 pts if it agrees   │               │  strategies, or those    │         │
 * │  │   with the signal        │               │  that explicitly add     │         │
 * │  │   direction (>25)        │               │  pattern confirmation --  │         │
 * │  │ symbol/symbolToken       │               │  not this one)           │         │
 * │  │ timeframe                │               │                          │         │
 * │  └──────────────────────────┘               └──────────────────────────┘         │
 * │                                                                                    │
 * │  SUPER_TREND is a pure indicator-driven strategy: it reacts only to price vs.     │
 * │  the SuperTrend line (+ optional ADX). The `pattern` argument is accepted to      │
 * │  satisfy the TradingStrategy interface but is never read here.                    │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * STRATEGY FLOW
 * ═════════════
 *
 *              ┌─────────────────────┐        ┌─────────────────────┐
 *              │   IndicatorEvent    │        │    PatternEvent      │
 *              │ price, supertrend,  │        │    (received,        │
 *              │        adx          │        │      unused)         │
 *              │  [ONLY input used]  │        │                      │
 *              └──────────┬──────────┘        └──────────────────────┘
 *                         │
 *                         ▼
 *              ┌───────────────────────────┐
 *              │ Compare price to SuperTrend│
 *              └──────────────┬─────────────┘
 *                              │
 *          ┌───────────────────┴───────────────────┐
 *          ▼                                        ▼
 *   price > SuperTrend                      price < SuperTrend
 *   (above dynamic support)                 (below dynamic resistance)
 *          │                                        │
 *          ▼                                        ▼
 *   signal = BUY                            signal = SELL
 *   confidence = 60                         confidence = 60
 *          │                                        │
 *          ▼                                        ▼
 *   ADX > 25 ?                              ADX > 25 ?
 *  (checked only when a                    (checked only when a
 *   signal was already set;                 signal was already set;
 *   direction-agnostic)                     direction-agnostic)
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
 *              Neither SuperTrend condition met (price == line)?
 *              signal stays HOLD -> return Optional.empty()
 *              (ADX check is never reached in this case)
 *                               │
 *                               ▼
 *              confidence = min(confidence, 100)
 *                               │
 *                               ▼
 *                    build & return SignalEvent
 *
 * CONFIDENCE SCORING (max 100)
 * ─────────────────────────────
 *   Base SuperTrend breakout . 60   (either direction, from IndicatorEvent)
 *   ADX confirmation .......... +20  (ADX > 25, either direction)
 *   Cap ........................ 100
 *   Note: ADX confirmation is a bonus, not a requirement -- a plain
 *   SuperTrend breakout alone still produces a 60-confidence signal.
 *
 * SIGNAL RULES
 * ────────────
 *   BUY  : price > SuperTrend  (above dynamic support -> uptrend)
 *   SELL : price < SuperTrend  (below dynamic resistance -> downtrend)
 *   HOLD : price == SuperTrend -> no signal emitted (Optional.empty())
 */
@Component
public class SupertrendStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "SUPER_TREND";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        // Direction (BUY/SELL/HOLD) comes entirely from price vs. the
        // SuperTrend line below. `pattern` is intentionally unused -- see
        // the INPUTS box in the class javadoc.
        if (indicator == null || indicator.getSupertrend() == null || indicator.getPrice() == null) {
            return Optional.empty();
        }

        String signal = "HOLD";
        int confidence = 0;

        // SUPERTREND LINE BREAKOUT (Primary Trend Signal - 60 points base)
        // USED: Price > SuperTrend = above dynamic support/resistance (uptrend)
        // LOGIC: SuperTrend band adjusts for volatility; very responsive trend tracker
        if (indicator.getPrice() > indicator.getSupertrend()) {
            signal = "BUY";
            confidence += 60;

            // ADX TREND STRENGTH CONFIRMATION (+20 points if valid)
            // USED: ADX > 25 validates trend exists (not range-bound market)
            // PREVENTS: SuperTrend whipsaws when trend is weak/choppy
            if (indicator.getAdx() != null && indicator.getAdx() > 25) {
                confidence += 20;
            }
        }
        // SUPERTREND LINE BREAKOUT (Primary Trend Signal - 60 points base)
        // USED: Price < SuperTrend = below dynamic support/resistance (downtrend)
        // LOGIC: SuperTrend band adjusts for volatility; very responsive trend tracker
        else if (indicator.getPrice() < indicator.getSupertrend()) {
            signal = "SELL";
            confidence += 60;

            // ADX TREND STRENGTH CONFIRMATION (+20 points if valid)
            // USED: ADX > 25 validates trend exists (not range-bound market)
            // PREVENTS: SuperTrend whipsaws when trend is weak/choppy
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
                .reason("Price " + indicator.getPrice() + " is " + signal
                    + " relative to SuperTrend " + indicator.getSupertrend())
                .timestamp(Instant.now())
                .build());
    }
}