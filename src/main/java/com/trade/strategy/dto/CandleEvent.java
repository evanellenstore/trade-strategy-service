package com.trade.strategy.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CandleEvent {
    private String symbol;
    private String symbolToken;
    private String exchange;
    private String timeframe;
    private LocalDateTime candleTime;
    private LocalDateTime endTime;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
    private double ltp;
}