package com.trade.strategy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategySummaryResponse {

    private String strategyName;
    private boolean enabled;
    private String timeframe;
    private int priority;
}
