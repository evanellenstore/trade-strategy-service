package com.trade.strategy.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trade.strategy.dto.StrategySummaryResponse;
import com.trade.strategy.entity.StrategyConfig;
import com.trade.strategy.service.StrategyConfigService;

@RestController
@RequestMapping("/strategy")
public class StrategyController {

    private final StrategyConfigService strategyConfigService;

    public StrategyController(StrategyConfigService strategyConfigService) {
        this.strategyConfigService = strategyConfigService;
    }

    @GetMapping("/strategies")
    public List<StrategySummaryResponse> getStrategies() {
        return strategyConfigService.getAllStrategies();
    }

    @PostMapping("/strategies")
    public ResponseEntity<StrategySummaryResponse> createStrategy(@RequestBody StrategyConfig strategyConfig) {
        StrategySummaryResponse response = strategyConfigService.createStrategy(strategyConfig);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/strategies/{strategyName}")
    public ResponseEntity<StrategySummaryResponse> updateStrategy(@PathVariable String strategyName, @RequestBody StrategyConfig strategyConfig) {
        StrategySummaryResponse response = strategyConfigService.updateStrategy(strategyName, strategyConfig);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/strategies/{strategyName}")
    public ResponseEntity<Void> deleteStrategy(@PathVariable String strategyName) {
        strategyConfigService.deleteStrategy(strategyName);
        return ResponseEntity.noContent().build();
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
