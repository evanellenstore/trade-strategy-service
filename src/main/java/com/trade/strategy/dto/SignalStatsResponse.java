package com.trade.strategy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalStatsResponse {

    private long totalSignals;
    private long buySignals;
    private long sellSignals;
    private long holdSignals;
    private double buyRatio;
    private double sellRatio;
    private double holdRatio;
    private String latestSignal;
    private String latestSymbol;
}
