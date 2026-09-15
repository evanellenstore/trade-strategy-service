package com.trade.strategy.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternEvent {
    private Long id;
    private String symbol;
    private String exchange;
    private String timeframe;
    private LocalDateTime candleTime;
    private String patternName;
    private String origin;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
    private String startTime;
    private String endTime;
    private List<CandleEvent> candles;
}
