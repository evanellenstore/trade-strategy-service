package com.trade.strategy.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.trade.strategy.dto.SignalEvent;

@Component
public class SignalProducer {

    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public SignalProducer(org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${market.kafka.topics.signal:signal.generated}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(SignalEvent event) {
        kafkaTemplate.send(topic, event.getSymbol(), event);
    }
}
