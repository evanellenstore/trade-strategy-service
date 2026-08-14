package com.trade.strategy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trade.strategy.entity.StrategySignal;

@Repository
public interface StrategySignalRepository extends JpaRepository<StrategySignal, Long> {
    List<StrategySignal> findBySymbolOrderByCreatedAtDesc(String symbol);
}
