package com.trade.strategy.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trade.strategy.entity.StrategyConfig;

@Repository
public interface StrategyConfigRepository extends JpaRepository<StrategyConfig, Long> {
    List<StrategyConfig> findByEnabledTrue();

    Optional<StrategyConfig> findByStrategyName(String strategyName);
}
