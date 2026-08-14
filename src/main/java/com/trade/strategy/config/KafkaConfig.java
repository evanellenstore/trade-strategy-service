package com.trade.strategy.config;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Bean
    public ProducerFactory<String, Object> producerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> configProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.RETRIES_CONFIG, 3,
                ProducerConfig.LINGER_MS_CONFIG, 10
        );
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
            JsonDeserializer.TRUSTED_PACKAGES, "*",
            JsonDeserializer.USE_TYPE_INFO_HEADERS, false
        );
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new JsonDeserializer<>(Object.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler((record, exception) -> {
            log.warn("Kafka message discarded after retries for topic {}: {}", record.topic(), exception.getMessage());
        }, new FixedBackOff(0L, 0L)));
        return factory;
    }

    @Bean
    public ConsumerFactory<String, com.trade.strategy.dto.IndicatorEvent> indicatorConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                JsonDeserializer.TRUSTED_PACKAGES, "com.trade.strategy.dto",
                JsonDeserializer.USE_TYPE_INFO_HEADERS, false
        );
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new JsonDeserializer<>(com.trade.strategy.dto.IndicatorEvent.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, com.trade.strategy.dto.IndicatorEvent> indicatorKafkaListenerContainerFactory(
            ConsumerFactory<String, com.trade.strategy.dto.IndicatorEvent> indicatorConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, com.trade.strategy.dto.IndicatorEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(indicatorConsumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler((record, exception) -> {
            log.warn("Kafka message discarded after retries for topic {}: {}", record.topic(), exception.getMessage());
        }, new FixedBackOff(0L, 0L)));
        return factory;
    }

    @Bean
    public ConsumerFactory<String, com.trade.strategy.dto.PatternDetectedEvent> patternConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                JsonDeserializer.TRUSTED_PACKAGES, "com.trade.strategy.dto",
                JsonDeserializer.USE_TYPE_INFO_HEADERS, false
        );
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new JsonDeserializer<>(com.trade.strategy.dto.PatternDetectedEvent.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, com.trade.strategy.dto.PatternDetectedEvent> patternKafkaListenerContainerFactory(
            ConsumerFactory<String, com.trade.strategy.dto.PatternDetectedEvent> patternConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, com.trade.strategy.dto.PatternDetectedEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(patternConsumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler((record, exception) -> {
            log.warn("Kafka message discarded after retries for topic {}: {}", record.topic(), exception.getMessage());
        }, new FixedBackOff(0L, 0L)));
        return factory;
    }
}

