package com.trade.strategy.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Data;

@Entity
@Table(name = "strategy_signals")
@Data
public class StrategySignal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signal_id", unique = true)
    private String signalId;

    private String symbol;

    @Column(name = "symbol_token")
    private String symbolToken;

    private String timeframe;

    @Column(name = "strategy_name")
    private String strategyName;

    @Column(name = "signal_text")
    private String signal;

    private Double confidence;

    @Column(name = "signal_price")
    private Double signalPrice;

    @Column(name = "candle_time")
    private Instant candleTime;

    @Column(name = "created_at")
    private Instant createdAt;

    private String remarks;
}
