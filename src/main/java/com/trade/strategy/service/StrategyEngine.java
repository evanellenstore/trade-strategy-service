package com.trade.strategy.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trade.strategy.dto.IndicatorEvent;
import com.trade.strategy.dto.PatternEvent;
import com.trade.strategy.dto.SignalEvent;
import com.trade.strategy.entity.StrategyConfig;
import com.trade.strategy.repository.StrategyConfigRepository;
import com.trade.strategy.strategy.TradingStrategy;
import com.trade.strategy.util.StrategyContext;

@Service
public class StrategyEngine {

    private static final Logger log = LoggerFactory.getLogger(StrategyEngine.class);

    private final List<TradingStrategy> strategies;
    private final StrategyConfigRepository configRepository;

    public StrategyEngine(List<TradingStrategy> strategies,
                         StrategyConfigRepository configRepository) {
        this.strategies = strategies;
        this.configRepository = configRepository;
    }

    @Transactional
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null) {
            return Optional.empty();
        }

        try {
            StrategyContext context = new StrategyContext(indicator, pattern);
                List<StrategyConfig> enabledStrategies = configRepository.findByEnabledTrue();

            List<SignalEvent> candidates = new ArrayList<>();
            for (TradingStrategy strategy : strategies) {
                if (!strategy.isEnabled()) {
                    continue;
                }
                if (!isConfiguredForTimeframe(strategy, indicator, enabledStrategies)) {
                    continue;
                }
                strategy.evaluate(context).ifPresent(candidates::add);
            }

            if (candidates.isEmpty()) {
                return Optional.empty();
            }

            SignalEvent selected = candidates.stream()
                    .sorted(Comparator
                            .comparingInt(SignalEvent::getConfidence).reversed()
                            .thenComparing(Comparator.comparingInt(
                                (SignalEvent signal) -> findPriority(signal.getStrategyName())).reversed()))
                    .findFirst()
                    .orElse(null);

            if (selected == null) {
                return Optional.empty();
            }

            return Optional.of(selected);
        } catch (Exception ex) {
            log.error("Error evaluating strategy for symbol {}", indicator.getSymbol(), ex);
            return Optional.empty();
        }
    }

    private int findPriority(String strategyName) {
        return strategies.stream()
                .filter(strategy -> strategy.getName().equalsIgnoreCase(strategyName))
                .mapToInt(TradingStrategy::getPriority)
                .findFirst()
                .orElse(100);
    }

    private boolean isConfiguredForTimeframe(TradingStrategy strategy,
                                             IndicatorEvent indicator,
                                             List<StrategyConfig> enabledStrategies) {
        if (enabledStrategies.isEmpty()) {
            return true;
        }

        return enabledStrategies.stream()
                .filter(config -> config.getStrategyName() != null
                        && config.getStrategyName().equalsIgnoreCase(strategy.getName()))
                .anyMatch(config -> config.getTimeframe() == null
                        || indicator.getTimeframe() == null
                        || config.getTimeframe().equalsIgnoreCase(indicator.getTimeframe()));
    }
}
