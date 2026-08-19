package com.trade.strategy.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final StrategyEngine strategyEngine;
    private final StrategySignalRepository signalRepository;
    private final PatternCacheService patternCacheService;
    private final SignalProducer signalProducer;
    private final SignalMapper signalMapper;
    private final MeterRegistry meterRegistry;

    @Autowired
    public SignalProcessingService(StrategyEngine strategyEngine,
                                  StrategySignalRepository signalRepository,
                                  PatternCacheService patternCacheService,
                                  SignalProducer signalProducer,
                                  SignalMapper signalMapper,
                                  MeterRegistry meterRegistry) {
        this.strategyEngine = strategyEngine;
        this.signalRepository = signalRepository;
        this.patternCacheService = patternCacheService;
        this.signalProducer = signalProducer;
        this.signalMapper = signalMapper;
        this.meterRegistry = meterRegistry;
    }

    public SignalProcessingService(List<TradingStrategy> strategies,
                                  StrategyConfigRepository configRepository,
                                  StrategySignalRepository signalRepository,
                                  PatternCacheService patternCacheService,
                                  SignalProducer signalProducer,
                                  SignalMapper signalMapper,
                                  MeterRegistry meterRegistry) {
        this(new StrategyEngine(strategies, configRepository), signalRepository,
                patternCacheService, signalProducer, signalMapper, meterRegistry);
    }

    @Transactional
    public Optional<SignalEvent> processSignal(IndicatorEvent indicator, PatternEvent pattern) {
        if (indicator == null || indicator.getSymbol() == null || indicator.getSymbolToken() == null) {
            return Optional.empty();
        }

        long start = System.nanoTime();
        try {
            Optional<SignalEvent> evaluated = strategyEngine.evaluate(indicator, pattern);
            if (evaluated.isEmpty()) {
                return Optional.empty();
            }

            SignalEvent winning = evaluated.get();

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
