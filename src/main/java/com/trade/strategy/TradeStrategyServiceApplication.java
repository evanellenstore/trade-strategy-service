package com.trade.strategy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
@ConfigurationPropertiesScan
public class TradeStrategyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeStrategyServiceApplication.class, args);
    }
}
                        