package com.trade.strategy.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trade.strategy.dto.IndicatorEvent;
import com.trade.strategy.dto.PatternDetectedEvent;
import com.trade.strategy.dto.PatternEvent;
import com.trade.strategy.dto.SignalEvent;
import com.trade.strategy.entity.StrategySignal;
import com.trade.strategy.kafka.SignalProducer;
import com.trade.strategy.mapper.SignalMapper;
import com.trade.strategy.repository.StrategyConfigRepository;
import com.trade.strategy.repository.StrategySignalRepository;
import com.trade.strategy.strategy.TradingStrategy;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class SignalProcessingService {

    private static final Logger log = LoggerFactory.getLogger(SignalProcessingService.class);

    private final List<TradingStrategy> strategies;
    private final StrategyConfigRepository configRepository;
    private final StrategySignalRepository signalRepository;
    private final PatternCacheService patternCacheService;
    private final SignalProducer signalProducer;
    private final SignalMapper signalMapper;
    private final MeterRegistry meterRegistry;

    public SignalProcessingService(List<TradingStrategy> strategies,
                                  StrategyConfigRepository configRepository,
                                  StrategySignalRepository signalRepository,
                                  PatternCacheService patternCacheService,
                                  SignalProducer signalProducer,
                                  SignalMapper signalMapper,
                                  MeterRegistry meterRegistry) {
        this.strategies = strategies;
        this.configRepository = configRepository;
        this.signalRepository = signalRepository;
        this.patternCacheService = patternCacheService;
        this.signalProducer = signalProducer;
        this.signalMapper = signalMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public Optional<SignalEvent> processSignal(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getSymbol() == null || indicator.getSymbolToken() == null) {
            return Optional.empty();
        }

        long start = System.nanoTime();
        try {
            List<String> enabledStrategies = configRepository.findByEnabledTrue().stream()
                    .map(config -> config.getStrategyName())
                    .filter(Objects::nonNull)
                    .toList();

            List<SignalEvent> candidates = new ArrayList<>();
            for (TradingStrategy strategy : strategies) {
                if (!strategy.isEnabled()) {
                    continue;
                }
                if (!enabledStrategies.isEmpty() && !enabledStrategies.contains(strategy.getName())) {
                    continue;
                }
                Optional<SignalEvent> evaluated = strategy.evaluate(indicator, pattern);
                evaluated.ifPresent(candidates::add);
            }

            if (candidates.isEmpty()) {
                return Optional.empty();
            }

            SignalEvent winning = candidates.stream()
                    .max(Comparator.comparingInt(SignalEvent::getConfidence))
                    .orElseThrow();

            winning.setSignalId(winning.getSignalId() == null ? "SIG-" + UUID.randomUUID() : winning.getSignalId());
            winning.setTimestamp(winning.getTimestamp() == null ? Instant.now() : winning.getTimestamp());

            StrategySignal entity = signalMapper.toEntity(winning);
            signalRepository.save(entity);
            signalProducer.publish(winning);

            meterRegistry.counter("strategy.signals.generated").increment();
            meterRegistry.counter("strategy.signals." + winning.getSignal().toLowerCase()).increment();
            meterRegistry.timer("strategy.execution.time").record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);

            return Optional.of(winning);
        } catch (Exception ex) {
            meterRegistry.counter("strategy.errors").increment();
            log.error("Error processing trading signal for symbol {}", indicator.getSymbol(), ex);
            return Optional.empty();
        }
    }

    public void handlePattern(PatternDetectedEvent event) {
        if (event == null || event.getSymbol() == null || event.getPatternName() == null) {
            return;
        }
        patternCacheService.addPattern(event.getSymbol(), event.getPatternName());
    }
}
