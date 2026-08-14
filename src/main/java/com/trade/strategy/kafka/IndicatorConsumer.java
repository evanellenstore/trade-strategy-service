package com.trade.strategy.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.trade.strategy.dto.IndicatorEvent;
import com.trade.strategy.dto.PatternEvent;
import com.trade.strategy.service.StrategyEngine;

@Component
public class IndicatorConsumer {

    private static final Logger log = LoggerFactory.getLogger(IndicatorConsumer.class);

    private final StrategyEngine strategyEngine;

    public IndicatorConsumer(StrategyEngine strategyEngine) {
        this.strategyEngine = strategyEngine;
    }

    @KafkaListener(topics = "${market.kafka.topics.indicator:indicator.updated}", groupId = "trade-strategy-service", containerFactory = "indicatorKafkaListenerContainerFactory")
    public void consumeIndicator(IndicatorEvent event) {
        try {
            if (event == null) {
                return;
            }
            strategyEngine.evaluate(event, new PatternEvent());
        } catch (Exception ex) {
            log.error("Failed to process indicator event for symbol {}", event != null ? event.getSymbol() : "UNKNOWN", ex);
        }
    }
}