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
 * MULTI_INDICATOR Trading Strategy.
 *
 * Combines six independent technical indicators into a consensus vote.
 * Generates BUY/SELL only when 3 or more of them agree on the same
 * direction AND that side has strictly more votes than the other -- a
 * high-confidence "majority rules" filter over single indicators.
 *
 * ┌────────────────────────────── WHY CONSENSUS APPROACH ────────────────────────────┐
 * │ • A single indicator alone often false-signals on normal market noise            │
 * │ • Multiple, independently-calculated indicators agreeing = much higher           │
 * │   probability the move is real                                                   │
 * │ • Requires 3+ of 6 aligned in the same direction -- a strict majority filter     │
 * │ • Trades only the strongest, most-confirmed setups; skips everything else        │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌──────────────────────── THE 6 INDICATORS (each votes independently) ─────────────┐
 * │ 1. RSI          RSI14 < 30 (bullish) / RSI14 > 70 (bearish)     -- momentum      │
 * │                 extreme / reversal signal                                        │
 * │ 2. MACD         MACD > Signal (bullish) / MACD < Signal (bearish) -- trend       │
 * │                 momentum crossover                                               │
 * │ 3. Stochastic   %K > %D (bullish) / %K < %D (bearish)           -- secondary,    │
 * │                 differently-calculated oscillator momentum                      │
 * │ 4. Bollinger    price <= BB_Lower (bullish) / price >= BB_Upper (bearish) --     │
 * │    Bands        price-extreme / dynamic support-resistance perspective          │
 * │ 5. ADX + DI     +DI > -DI (bullish) / -DI > +DI (bearish), ONLY when ADX > 25 -- │
 * │                 trend direction, gated by trend strength                        │
 * │ 6. EMA          price > EMA50 (bullish) / price < EMA50 (bearish) -- classic     │
 * │                 long-term trend-following perspective                           │
 * │                                                                                    │
 * │ Each indicator is evaluated in total isolation: a null/missing input for one      │
 * │ simply skips that vote (no bullish, no bearish) -- it never blocks the others.   │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌────────────────────────────── WHY NOT OTHER INDICATORS ──────────────────────────┐
 * │ • Pivot levels     -> price-based, not momentum-based; a different category      │
 * │                        of signal than the 6 already covered                      │
 * │ • Volume            -> already implicitly captured via MFI/CMF when those        │
 * │                        indicators are present elsewhere in the system            │
 * │ • Chart patterns    -> more subjective than objective numeric indicators;        │
 * │                        kept out of this purely-quantitative consensus vote       │
 * │ • More indicators   -> beyond 6, additional votes add noise rather than          │
 * │                        improving signal quality                                  │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────── INPUTS ───────────────────────────────────────┐
 * │                                                                                    │
 * │   IndicatorEvent  [DRIVES the signal -- all 6 votes]  PatternEvent  [IGNORED]     │
 * │  ┌──────────────────────────────┐                    ┌──────────────────┐        │
 * │  │ rsi14                         │                    │ patternName -- n/a│        │
 * │  │ macd, macdSignal              │                    │ (this strategy is │        │
 * │  │ stochK, stochD                │                    │  a pure numeric   │        │
 * │  │ bbUpper, bbLower, price        │                   │  consensus -- see │        │
 * │  │ adx, adxDIPlus, adxDIMinus     │                   │  WHY NOT OTHER     │        │
 * │  │ ema50, price                   │                   │  INDICATORS above)│        │
 * │  │ symbol/symbolToken, timeframe │                    │                  │        │
 * │  │ -- every field optional; a    │                    │                  │        │
 * │  │    missing one just skips its │                    │                  │        │
 * │  │    vote, nothing else         │                    │                  │        │
 * │  └──────────────────────────────┘                    └──────────────────┘        │
 * └────────────────────────────────────────────────────────────────────────────────┘
 *
 * STRATEGY FLOW
 * ═════════════
 *
 *                          ┌─────────────────────┐    ┌─────────────────────┐
 *                          │   IndicatorEvent     │    │    PatternEvent      │
 *                          │  (all 6 sub-fields)  │    │  (received, unused)  │
 *                          └──────────┬───────────┘    └──────────────────────┘
 *                                     │
 *                                     ▼
 *      ┌────────────────────────────────────────────────────────────────────┐
 *      │              Evaluate all 6 indicators INDEPENDENTLY                │
 *      │        (each present indicator casts exactly one bull/bear vote)    │
 *      ├──────────┬──────────┬───────────┬────────────┬────────────┬────────┤
 *      ▼          ▼          ▼           ▼            ▼            ▼
 *    RSI        MACD     Stochastic  Bollinger    ADX + DI       EMA50
 *  <30/>70    >Sig/<Sig    %K vs %D    Bands      (gated by     price vs
 *                                    price vs     ADX > 25)        EMA
 *                                    bands
 *      │          │          │           │            │            │
 *      └──────────┴──────────┴─────┬─────┴────────────┴────────────┘
 *                                   ▼
 *                    ┌───────────────────────────┐
 *                    │  bullishSignals count      │
 *                    │  bearishSignals count      │
 *                    │  (0 to 6 each)             │
 *                    └──────────────┬─────────────┘
 *                                   │
 *                                   ▼
 *                    ┌───────────────────────────┐
 *                    │ bullishSignals ==          │
 *                    │   bearishSignals ?         │
 *                    │ (TIE CHECK -- runs first)  │
 *                    └──────────────┬─────────────┘
 *                    yes │                    │ no
 *                        ▼                    ▼
 *                 signal = HOLD    ┌────────────────────┬────────────────────┐
 *                 (even if both    ▼                     ▼
 *                  sides hit 3+,   bullishSignals > 3   bearishSignals > 3
 *                  e.g. 3-3)       AND > bearishSignals AND > bullishSignals
 *                        │                    │                     │
 *                        │                    ▼                     ▼
 *                        │             signal = BUY           signal = SELL
 *                        │             confidence = 85         confidence = 85
 *                        │                    │                     │
 *                        │         bullishSignals > 3 ?   bearishSignals > 3 ?
 *                        │         +1 per extra vote       +1 per extra vote
 *                        │         (min(count-3, 5))       (min(count-3, 5))
 *                        │                    │                     │
 *                        └────────────────────┴──────────┬──────────┘
 *                                                          ▼
 *                                        neither side reaches 3
 *                                        (and not a tie)?
 *                                        signal = HOLD
 *                                        -> Optional.empty()
 *                                                          │
 *                                                          ▼
 *                                        confidence = min(confidence, 100)
 *                                        (real max reached = 88, with all
 *                                         6 aligned one-sided; see
 *                                         CONFIDENCE SCORING note below)
 *                                                          │
 *                                                          ▼
 *                                             build & return SignalEvent
 *
 * CONFIDENCE SCORING (max 100, real max reached = 88)
 * ─────────────────────────────────────────────────────
 *   Base consensus (3+ aligned, strictly ahead) ... 85
 *   Extra vote bonus ................................. +1 per vote beyond 3,
 *                                                        capped at +5
 *                                                        (only reachable up to
 *                                                        +3 in practice, since
 *                                                        there are only 6
 *                                                        indicators total:
 *                                                        85 + min(6-3, 5) = 88)
 *   Cap ................................................ 100 (not reachable)
 *
 * SIGNAL RULES
 * ────────────
 *   BUY  : bullishSignals >= 3 AND bullishSignals > bearishSignals
 *   SELL : bearishSignals >= 3 AND bearishSignals > bullishSignals
 *   HOLD : neither side reaches a 3-vote majority, OR the two sides are
 *          tied (including a 3-3 tie) -> no signal emitted (Optional.empty())
 */
@Component
public class MultiIndicatorStrategy implements TradingStrategy {

    @Override
    public String getName() {
        return "MULTI_INDICATOR";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null) {
            return Optional.empty();
        }

        int bullishSignals = 0;
        int bearishSignals = 0;

        // 1. RSI MOMENTUM INDICATOR (+1 signal if extreme)
        // USED: RSI < 30 = oversold momentum (BUY)
        //       RSI > 70 = overbought momentum (SELL)
        // PURPOSE: Momentum oscillator adds reversal confirmation to consensus
        if (indicator.getRsi14() != null) {
            if (indicator.getRsi14() < 30) {
                bullishSignals++;
            } else if (indicator.getRsi14() > 70) {
                bearishSignals++;
            }
        }

        // 2. MACD TREND MOMENTUM INDICATOR (+1 signal if aligned)
        // USED: MACD > Signal = bullish momentum crossover (BUY)
        //       MACD < Signal = bearish momentum crossover (SELL)
        // PURPOSE: Trend momentum confirmation; different calculation than RSI
        if (indicator.getMacd() != null && indicator.getMacdSignal() != null) {
            if (indicator.getMacd() > indicator.getMacdSignal()) {
                bullishSignals++;
            } else if (indicator.getMacd() < indicator.getMacdSignal()) {
                bearishSignals++;
            }
        }

        // 3. STOCHASTIC OSCILLATOR INDICATOR (+1 signal if aligned)
        // USED: %K > %D = bullish crossover (BUY)
        //       %K < %D = bearish crossover (SELL)
        // PURPOSE: Secondary oscillator; different momentum perspective than RSI
        if (indicator.getStochK() != null && indicator.getStochD() != null) {
            if (indicator.getStochK() > indicator.getStochD()) {
                bullishSignals++;
            } else if (indicator.getStochK() < indicator.getStochD()) {
                bearishSignals++;
            }
        }

        // 4. BOLLINGER BANDS PRICE EXTREME INDICATOR (+1 signal if aligned)
        // USED: Price <= BB_Lower = oversold mean reversion zone (BUY)
        //       Price >= BB_Upper = overbought mean reversion zone (SELL)
        // PURPOSE: Price extreme confirmation; dynamic support/resistance perspective
        if (indicator.getBbUpper() != null && indicator.getBbLower() != null
                && indicator.getPrice() != null) {
            if (indicator.getPrice() <= indicator.getBbLower()) {
                bullishSignals++;
            } else if (indicator.getPrice() >= indicator.getBbUpper()) {
                bearishSignals++;
            }
        }

        // 5. ADX + DIRECTIONAL INDICATORS TREND INDICATOR (+1 signal if aligned)
        // USED: +DI > -DI with ADX > 25 = uptrend confirmed (BUY)
        //       -DI > +DI with ADX > 25 = downtrend confirmed (SELL)
        // PURPOSE: Trend direction + strength confirmation; different framework than MACD
        if (indicator.getAdx() != null && indicator.getAdxDIPlus() != null
                && indicator.getAdxDIMinus() != null && indicator.getAdx() > 25) {
            if (indicator.getAdxDIPlus() > indicator.getAdxDIMinus()) {
                bullishSignals++;
            } else if (indicator.getAdxDIMinus() > indicator.getAdxDIPlus()) {
                bearishSignals++;
            }
        }

        // 6. EMA TREND FOLLOWING INDICATOR (+1 signal if aligned)
        // USED: Price > EMA50 = price above MA (uptrend) (BUY)
        //       Price < EMA50 = price below MA (downtrend) (SELL)
        // PURPOSE: Moving average trend confirmation; long-term direction perspective
        if (indicator.getEma50() != null && indicator.getPrice() != null) {
            if (indicator.getPrice() > indicator.getEma50()) {
                bullishSignals++;
            } else if (indicator.getPrice() < indicator.getEma50()) {
                bearishSignals++;
            }
        }

        String signal = "HOLD";
        int confidence = 0;

        // ┌──────────────────────────────── TIE CHECK ────────────────────────────────────┐
        // │ USED: runs BEFORE the majority checks below so a tie can never fall through   │
        // │       into the ">= 3" branches                                                │
        // │ PREVENTS: a 3-3 (or any equal) split from silently resolving to BUY just      │
        // │           because the bullish branch happened to be checked first             │
        // │ LOGIC: bullishSignals == bearishSignals (including 0-0) -> no consensus,      │
        // │        stay HOLD regardless of how high either count is                       │
        // └────────────────────────────────────────────────────────────────────────────────┘
        if (bullishSignals == bearishSignals) {
            return Optional.empty();
        }

        // CONSENSUS REQUIREMENT: 3+ BULLISH SIGNALS AND STRICTLY AHEAD OF BEARISH
        // USED: Bullish signals from 3+ independent indicators, with bullish votes
        //       strictly outnumbering bearish votes = consensus BUY
        // LOGIC: Probability much higher when multiple indicators align bullish;
        //        the strict-ahead check is redundant with the tie check above but
        //        kept for clarity/defense-in-depth
        // BASE CONFIDENCE: 85 points (very high; reflects consensus strength)
        // BONUS: +1 point per indicator beyond 3 (max +5, capped at 100)
        if (bullishSignals >= 3 && bullishSignals > bearishSignals) {
            signal = "BUY";
            // Base confidence for multi-indicator consensus
            confidence = 85;
            // Additional points for each indicator beyond 3
            if (bullishSignals > 3) {
                confidence += Math.min(bullishSignals - 3, 5);
            }
        }
        // CONSENSUS REQUIREMENT: 3+ BEARISH SIGNALS AND STRICTLY AHEAD OF BULLISH
        // USED: Bearish signals from 3+ independent indicators, with bearish votes
        //       strictly outnumbering bullish votes = consensus SELL
        // LOGIC: Probability much higher when multiple indicators align bearish;
        //        the strict-ahead check is redundant with the tie check above but
        //        kept for clarity/defense-in-depth
        // BASE CONFIDENCE: 85 points (very high; reflects consensus strength)
        // BONUS: +1 point per indicator beyond 3 (max +5, capped at 100)
        else if (bearishSignals >= 3 && bearishSignals > bullishSignals) {
            signal = "SELL";
            // Base confidence for multi-indicator consensus
            confidence = 85;
            // Additional points for each indicator beyond 3
            if (bearishSignals > 3) {
                confidence += Math.min(bearishSignals - 3, 5);
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
                .reason("Multi-indicator consensus generated " + signal
                    + " after aligned RSI, MACD, stochastic, band, DI, and EMA checks")
                .timestamp(Instant.now())
                .build());
    }
}