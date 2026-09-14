package com.trade.strategy.dto;

import java.time.Instant;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class IndicatorEvent {
    private String symbol;
    private String symbolToken;
    private String timeframe;
    private Double ema20;
    private Double ema50;
    private Double rsi14;
    private Double adx;
    private Double macd;
    private Double macdSignal;
    private Double macdPrevious;
    private Double macdSignalPrevious;
    private Double vwap;
    private Double supertrend;
    private Double atr;
    private Double price;
    private Instant timestamp;
    private LocalDateTime candleTime;
    
    // ADX_DI indicators
    private Double adxDIPlus;
    private Double adxDIMinus;
    
    // BB_REVERSAL indicators
    private Double bbUpper;
    private Double bbMiddle;
    private Double bbLower;
    
    // STOCHASTIC indicators
    private Double stochK;
    private Double stochD;
    
    // PIVOT_BREAKOUT indicators
    private Double pivotPoint;
    private Double pivotResistance1;
    private Double pivotResistance2;
    private Double pivotSupport1;
    private Double pivotSupport2;
    
    // MFI indicator
    private Double mfi;
    
    // CMF indicator
    private Double cmf;
}
