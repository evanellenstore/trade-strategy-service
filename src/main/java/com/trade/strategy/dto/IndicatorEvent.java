package com.trade.strategy.dto;

import java.time.Instant;

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
    private Double vwap;
    private Double supertrend;
    private Double atr;
    private Double price;
    private Instant timestamp;
}
