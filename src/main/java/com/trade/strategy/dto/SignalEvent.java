package com.trade.strategy.dto;

import java.time.Instant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SignalEvent {
    private String signalId;
    private String symbol;
    private String symbolToken;
    private String timeframe;
    private String strategyName;
    private String signal;
    private Integer confidence;
    private Double price;
    private Instant timestamp;
}
