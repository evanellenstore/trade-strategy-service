package com.trade.strategy.dto;

import lombok.Data;

@Data
public class PatternDetectedEvent {
    private String symbol;
    private String patternName;
    private String origin;
}
