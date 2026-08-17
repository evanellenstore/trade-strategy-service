package com.trade.strategy.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trade.strategy.dto.StrategySummaryResponse;
import com.trade.strategy.entity.StrategyConfig;
import com.trade.strategy.repository.StrategyConfigRepository;
import com.trade.strategy.strategy.TradingStrategy;

@Service
public class StrategyConfigService {

    private static final Logger log = LoggerFactory.getLogger(StrategyConfigService.class);

    private final StrategyConfigRepository strategyConfigRepository;
    private final List<TradingStrategy> strategies;

    public StrategyConfigService(StrategyConfigRepository strategyConfigRepository, List<TradingStrategy> strategies) {
        this.strategyConfigRepository = strategyConfigRepository;
        this.strategies = strategies;
        initializeDefaults();
    }

    @Transactional
    public StrategySummaryResponse enableStrategy(String strategyName) {
        String normalized = normalizeName(strategyName);
        StrategyConfig config = strategyConfigRepository.findByStrategyName(normalized)
                .orElseGet(() -> createDefaultConfig(normalized));
        config.setEnabled(Boolean.TRUE);
        strategyConfigRepository.save(config);
        log.info("Enabled strategy {}", normalized);
        return toSummary(config);
    }

    @Transactional
    public StrategySummaryResponse disableStrategy(String strategyName) {
        String normalized = normalizeName(strategyName);
        StrategyConfig config = strategyConfigRepository.findByStrategyName(normalized)
                .orElseGet(() -> createDefaultConfig(normalized));
        config.setEnabled(Boolean.FALSE);
        strategyConfigRepository.save(config);
        log.info("Disabled strategy {}", normalized);
        return toSummary(config);
    }

    public List<StrategySummaryResponse> getAllStrategies() {
        return strategies.stream()
                .sorted(Comparator.comparing(TradingStrategy::getName))
                .map(strategy -> {
                    StrategyConfig config = strategyConfigRepository.findByStrategyName(strategy.getName())
                            .orElseGet(() -> createDefaultConfigOf(strategy.getName()));
                    return StrategySummaryResponse.builder()
                            .strategyName(config.getStrategyName())
                            .enabled(Boolean.TRUE.equals(config.getEnabled()))
                            .timeframe(config.getTimeframe() == null ? "ONE_MINUTE" : config.getTimeframe())
                            .priority(config.getPriority() == null ? 100 : config.getPriority())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private void initializeDefaults() {
        strategies.forEach(strategy -> strategyConfigRepository.findByStrategyName(strategy.getName())
                .orElseGet(() -> strategyConfigRepository.save(createDefaultConfigOf(strategy.getName()))));
    }

    private StrategyConfig createDefaultConfig(String strategyName) {
        StrategyConfig strategyConfig = new StrategyConfig();
        strategyConfig.setStrategyName(normalizeName(strategyName));
        strategyConfig.setEnabled(Boolean.TRUE);
        strategyConfig.setTimeframe("ONE_MINUTE");
        strategyConfig.setPriority(100);
        return strategyConfig;
    }

    private StrategyConfig createDefaultConfigOf(String strategyName) {
        return strategyConfigRepository.save(createDefaultConfig(strategyName));
    }

    private StrategySummaryResponse toSummary(StrategyConfig config) {
        return StrategySummaryResponse.builder()
                .strategyName(config.getStrategyName())
                .enabled(Boolean.TRUE.equals(config.getEnabled()))
                .timeframe(config.getTimeframe() == null ? "ONE_MINUTE" : config.getTimeframe())
                .priority(config.getPriority() == null ? 100 : config.getPriority())
                .build();
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Strategy name must not be blank");
        }
        return value.trim().toUpperCase();
    }

    @Transactional
    public StrategySummaryResponse createStrategy(StrategyConfig strategyConfig) {
        String normalized = normalizeName(strategyConfig.getStrategyName());
        StrategyConfig newConfig = new StrategyConfig();
        newConfig.setStrategyName(normalized);
        newConfig.setEnabled(strategyConfig.getEnabled() != null ? strategyConfig.getEnabled() : Boolean.FALSE);
        newConfig.setTimeframe(strategyConfig.getTimeframe() != null ? strategyConfig.getTimeframe() : "1H");
        newConfig.setPriority(strategyConfig.getPriority() != null ? strategyConfig.getPriority() : 1);
        strategyConfigRepository.save(newConfig);
        log.info("Created strategy {}", normalized);
        return toSummary(newConfig);
    }

    @Transactional
    public StrategySummaryResponse updateStrategy(String strategyName, StrategyConfig updates) {
        String normalized = normalizeName(strategyName);
        StrategyConfig config = strategyConfigRepository.findByStrategyName(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + strategyName));
        
        if (updates.getTimeframe() != null) {
            config.setTimeframe(updates.getTimeframe());
        }
        if (updates.getPriority() != null) {
            config.setPriority(updates.getPriority());
        }
        if (updates.getEnabled() != null) {
            config.setEnabled(updates.getEnabled());
        }
        
        strategyConfigRepository.save(config);
        log.info("Updated strategy {}", normalized);
        return toSummary(config);
    }

    @Transactional
    public void deleteStrategy(String strategyName) {
        String normalized = normalizeName(strategyName);
        StrategyConfig config = strategyConfigRepository.findByStrategyName(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + strategyName));
        strategyConfigRepository.delete(config);
        log.info("Deleted strategy {}", normalized);
    }
}
