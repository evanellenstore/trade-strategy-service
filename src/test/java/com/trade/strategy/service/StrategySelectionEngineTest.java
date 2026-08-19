package com.trade.strategy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import com.trade.strategy.dto.IndicatorEvent;
import com.trade.strategy.dto.PatternEvent;
import com.trade.strategy.dto.SignalEvent;
import com.trade.strategy.entity.StrategyConfig;
import com.trade.strategy.entity.StrategySignal;
import com.trade.strategy.mapper.SignalMapper;
import com.trade.strategy.kafka.SignalProducer;
import com.trade.strategy.repository.StrategyConfigRepository;
import com.trade.strategy.repository.StrategySignalRepository;
import com.trade.strategy.strategy.TradingStrategy;
import com.trade.strategy.util.StrategyContext;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class StrategySelectionEngineTest {

    @Test
    void selectsHighestConfidenceAndHigherPriorityOnTie() {
        StrategyConfigRepository configRepository = mock(StrategyConfigRepository.class);
        when(configRepository.findByEnabledTrue()).thenReturn(List.of(
                strategyConfig("LOW_PRIORITY"),
                strategyConfig("HIGH_PRIORITY")));

        StrategySignalRepository signalRepository = mock(StrategySignalRepository.class);
        when(signalRepository.save(org.mockito.ArgumentMatchers.any(StrategySignal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SignalProducer signalProducer = mock(SignalProducer.class);
        SignalMapper signalMapper = new SignalMapper();

        IndicatorEvent indicator = new IndicatorEvent();
        indicator.setSymbol("VEDL-EQ");
        indicator.setSymbolToken("3063");
        indicator.setTimeframe("ONE_MINUTE");
        indicator.setPrice(27520.0);

        List<TradingStrategy> strategies = List.of(
                new TestStrategy("LOW_PRIORITY", 10, "BUY", 80),
                new TestStrategy("HIGH_PRIORITY", 25, "BUY", 80)
        );

        SignalProcessingService signalProcessingService = new SignalProcessingService(
                strategies,
                configRepository,
                signalRepository,
                new PatternCacheService(),
                signalProducer,
                signalMapper,
                new SimpleMeterRegistry()
        );

        Optional<SignalEvent> result = signalProcessingService.processSignal(indicator, PatternEvent.builder().patternName("BullFlag").build());

        assertThat(result).isPresent();
        assertThat(result.get().getStrategyName()).isEqualTo("HIGH_PRIORITY");
        assertThat(result.get().getConfidence()).isEqualTo(80);
    }

    private static StrategyConfig strategyConfig(String strategyName) {
        StrategyConfig config = new StrategyConfig();
        config.setStrategyName(strategyName);
        config.setEnabled(true);
        config.setTimeframe("ONE_MINUTE");
        return config;
    }

    private static final class TestStrategy implements TradingStrategy {
        private final String name;
        private final int priority;
        private final String signal;
        private final int confidence;

        private TestStrategy(String name, int priority, String signal, int confidence) {
            this.name = name;
            this.priority = priority;
            this.signal = signal;
            this.confidence = confidence;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public Optional<SignalEvent> evaluate(StrategyContext context) {
            return Optional.of(SignalEvent.builder()
                    .symbol(context.getIndicator().getSymbol())
                    .symbolToken(context.getIndicator().getSymbolToken())
                    .strategyName(getName())
                    .timeframe(context.getIndicator().getTimeframe())
                    .signal(signal)
                    .confidence(confidence)
                    .price(context.getIndicator().getPrice())
                    .build());
        }
    }
}
