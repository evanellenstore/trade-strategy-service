package com.trade.strategy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableKafka
@EnableAsync
@ConfigurationPropertiesScan
@EnableFeignClients
public class TradeStrategyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeStrategyServiceApplication.class, args);
    }
}
                        