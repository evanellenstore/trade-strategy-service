package com.trade.strategy.strategy;

import java.util.Optional;

import com.trade.strategy.dto.IndicatorEvent;
import com.trade.strategy.dto.PatternEvent;
import com.trade.strategy.dto.SignalEvent;

public interface TradingStrategy {

    String getName();

    boolean isEnabled();

    Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern);
}
