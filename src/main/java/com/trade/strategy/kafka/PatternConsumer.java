package com.trade.strategy.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.trade.strategy.dto.PatternDetectedEvent;
import com.trade.strategy.service.PatternCacheService;

@Component
public class PatternConsumer {

    private static final Logger log = LoggerFactory.getLogger(PatternConsumer.class);

    private final PatternCacheService cache;

    public PatternConsumer(PatternCacheService cache) {
        this.cache = cache;
    }

    @KafkaListener(topics = "${market.kafka.topics.pattern:pattern.detected}", groupId = "trade-strategy-service-pattern", containerFactory = "patternKafkaListenerContainerFactory")
    public void consumePattern(PatternDetectedEvent event) {
        try {
            if (event == null || event.getSymbol() == null || event.getPatternName() == null) {
                return;
            }
            cache.addPattern(event.getSymbol(), event.getPatternName());
        } catch (Exception ex) {
            log.error("Failed to process pattern event for symbol {}", event != null ? event.getSymbol() : "UNKNOWN", ex);
        }
    }
}
