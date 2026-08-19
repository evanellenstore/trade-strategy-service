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

    @Column(name = "symbol")
    private String symbol;

    @Column(name = "symbol_token")
    private String symbolToken;

    @Column(name = "timeframe")
    private String timeframe;

    @Column(name = "strategy_name")
    private String strategyName;

    @Column(name = "`signal`")
    private String signal;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "reason")
    private String reason;

    @Column(name = "price")
    private Double price;

    @Column(name = "created_at")
    private Instant createdAt;
}
