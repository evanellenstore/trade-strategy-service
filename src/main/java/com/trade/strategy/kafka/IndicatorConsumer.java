package com.trade.strategy.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.trade.strategy.dto.IndicatorEvent;
import com.trade.strategy.service.PatternCacheService;
import com.trade.strategy.service.SignalProcessingService;

@Component
public class IndicatorConsumer {

    private static final Logger log = LoggerFactory.getLogger(IndicatorConsumer.class);

    private final SignalProcessingService signalProcessingService;
    private final PatternCacheService patternCache;

    public IndicatorConsumer(SignalProcessingService signalProcessingService, PatternCacheService patternCache) {
        this.signalProcessingService = signalProcessingService;
        this.patternCache = patternCache;
    }

    @KafkaListener(topics = "${market.kafka.topics.indicator:indicator.updated}", groupId = "trade-strategy-service", containerFactory = "indicatorKafkaListenerContainerFactory")
    public void consumeIndicator(IndicatorEvent event) {
        try {
            if (event == null) {
                return;
            }
            signalProcessingService.processSignal(event, patternCache.getLatestPattern(event.getSymbol()));
        } catch (Exception ex) {
            log.error("Failed to process indicator event for symbol {}", event != null ? event.getSymbol() : "UNKNOWN", ex);
        }
    }
}