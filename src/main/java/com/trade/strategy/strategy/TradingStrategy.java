package com.trade.strategy.strategy;

import java.util.Optional;

import com.trade.strategy.dto.IndicatorEvent;
import com.trade.strategy.dto.PatternEvent;
import com.trade.strategy.dto.SignalEvent;
import com.trade.strategy.util.StrategyContext;

public interface TradingStrategy {

    String getName();

    default int getPriority() {
        return 100;
    }

    default boolean isEnabled() {
        return true;
    }

    default Optional<SignalEvent> evaluate(StrategyContext context) {
        if (context == null || context.getIndicator() == null) {
            return Optional.empty();
        }
        return evaluate(context.getIndicator(), context.getPattern());
    }

    default Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        return Optional.empty();
    }
}
