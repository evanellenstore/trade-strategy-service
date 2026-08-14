package com.trade.strategy.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trade.strategy.dto.SignalStatsResponse;
import com.trade.strategy.entity.StrategySignal;
import com.trade.strategy.service.StrategySignalService;

@RestController
@RequestMapping("/api/v1")
public class SignalController {

    private final StrategySignalService signalService;

    public SignalController(StrategySignalService signalService) {
        this.signalService = signalService;
    }

    @GetMapping("/signals")
    public List<StrategySignal> getSignals() {
        return signalService.findAllSignals();
    }

    @GetMapping("/signals/latest/{symbol}")
    public List<StrategySignal> getLatestSignals(@PathVariable String symbol) {
        return signalService.findLatestSignals(symbol);
    }

    @GetMapping("/signals/stats")
    public SignalStatsResponse getSignalStats() {
        return signalService.getSignalStats();
    }
}

