package com.trade.strategy.mapper;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.trade.strategy.dto.SignalEvent;
import com.trade.strategy.entity.StrategySignal;

@Component
public class SignalMapper {
    public StrategySignal toEntity(SignalEvent e) {
        StrategySignal s = new StrategySignal();
        s.setSignalId(e.getSignalId());
        s.setSymbol(e.getSymbol());
        s.setSymbolToken(e.getSymbolToken());
        s.setTimeframe(e.getTimeframe());
        s.setStrategyName(e.getStrategyName());
        s.setSignal(e.getSignal());
        s.setConfidence(e.getConfidence() != null ? e.getConfidence().doubleValue() : null);
        s.setSignalPrice(e.getPrice());
        s.setCandleTime(e.getTimestamp());
        s.setCreatedAt(Instant.now());
        return s;
    }
}
