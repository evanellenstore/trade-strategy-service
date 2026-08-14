package com.trade.strategy.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trade.strategy.dto.StrategySummaryResponse;
import com.trade.strategy.service.StrategyConfigService;

@RestController
@RequestMapping("/api/v1")
public class StrategyController {

    private final StrategyConfigService strategyConfigService;

    public StrategyController(StrategyConfigService strategyConfigService) {
        this.strategyConfigService = strategyConfigService;
    }

    @GetMapping("/strategies")
    public List<StrategySummaryResponse> getStrategies() {
        return strategyConfigService.getAllStrategies();
    }

    @PutMapping("/strategies/{strategyName}/enable")
    public ResponseEntity<StrategySummaryResponse> enableStrategy(@PathVariable String strategyName) {
        return ResponseEntity.ok(strategyConfigService.enableStrategy(strategyName));
    }

    @PutMapping("/strategies/{strategyName}/disable")
    public ResponseEntity<StrategySummaryResponse> disableStrategy(@PathVariable String strategyName) {
        return ResponseEntity.ok(strategyConfigService.disableStrategy(strategyName));
    }
}
