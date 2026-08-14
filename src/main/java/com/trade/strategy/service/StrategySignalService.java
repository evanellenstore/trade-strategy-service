package com.trade.strategy.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trade.strategy.dto.SignalStatsResponse;
import com.trade.strategy.entity.StrategySignal;
import com.trade.strategy.repository.StrategySignalRepository;

@Service
public class StrategySignalService {

    private final StrategySignalRepository signalRepository;

    public StrategySignalService(StrategySignalRepository signalRepository) {
        this.signalRepository = signalRepository;
    }

    public List<StrategySignal> findAllSignals() {
        return signalRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public List<StrategySignal> findLatestSignals(String symbol) {
        return signalRepository.findBySymbolOrderByCreatedAtDesc(symbol);
    }

    @Transactional(readOnly = true)
    public SignalStatsResponse getSignalStats() {
        List<StrategySignal> signals = signalRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        long total = signals.size();
        long buy = signals.stream().filter(signal -> "BUY".equalsIgnoreCase(signal.getSignal())).count();
        long sell = signals.stream().filter(signal -> "SELL".equalsIgnoreCase(signal.getSignal())).count();
        long hold = signals.stream().filter(signal -> "HOLD".equalsIgnoreCase(signal.getSignal())).count();

        String latestSignal = signals.isEmpty() ? "NONE" : signals.get(0).getSignal();
        String latestSymbol = signals.isEmpty() ? "N/A" : signals.get(0).getSymbol();

        return SignalStatsResponse.builder()
                .totalSignals(total)
                .buySignals(buy)
                .sellSignals(sell)
                .holdSignals(hold)
                .buyRatio(total == 0 ? 0.0 : (buy * 100.0) / total)
                .sellRatio(total == 0 ? 0.0 : (sell * 100.0) / total)
                .holdRatio(total == 0 ? 0.0 : (hold * 100.0) / total)
                .latestSignal(latestSignal)
                .latestSymbol(latestSymbol)
                .build();
    }
}
