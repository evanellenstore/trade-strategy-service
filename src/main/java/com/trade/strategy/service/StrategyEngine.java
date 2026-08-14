package com.trade.strategy.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trade.strategy.dto.IndicatorEvent;
import com.trade.strategy.dto.PatternEvent;
import com.trade.strategy.dto.SignalEvent;
import com.trade.strategy.repository.StrategyConfigRepository;
import com.trade.strategy.strategy.TradingStrategy;

@Service
public class StrategyEngine {

    private static final Logger log = LoggerFactory.getLogger(StrategyEngine.class);

    private final List<TradingStrategy> strategies;
    private final StrategyConfigRepository configRepository;
    private final SignalProcessingService signalProcessingService;

    public StrategyEngine(List<TradingStrategy> strategies,
                         StrategyConfigRepository configRepository,
                         SignalProcessingService signalProcessingService) {
        this.strategies = strategies;
        this.configRepository = configRepository;
        this.signalProcessingService = signalProcessingService;
    }

    @Transactional
    public Optional<SignalEvent> evaluate(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null) {
            return Optional.empty();
        }

        try {
            List<String> enabledStrategies = configRepository.findByEnabledTrue().stream()
                    .map(config -> config.getStrategyName())
                    .filter(Objects::nonNull)
                    .toList();

            for (TradingStrategy strategy : strategies) {
                if (!strategy.isEnabled()) {
                    continue;
                }
                if (!enabledStrategies.isEmpty() && !enabledStrategies.contains(strategy.getName())) {
                    continue;
                }
                Optional<SignalEvent> signal = strategy.evaluate(indicator, pattern);
                if (signal.isPresent()) {
                    signalProcessingService.processSignal(indicator, pattern);
                    return signal;
                }
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.error("Error evaluating strategy for symbol {}", indicator.getSymbol(), ex);
            return Optional.empty();
        }
    }
}
