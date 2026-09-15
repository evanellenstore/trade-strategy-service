package com.trade.strategy.dto;

import java.time.LocalDateTime;

import lombok.Data;
import java.util.List;

@Data
public class PatternDetectedEvent {
    private String symbol;
    private String timeframe;
    private LocalDateTime candleTime;
    private String patternName;
    private String origin;
    private List<CandleEvent> candles;
}
