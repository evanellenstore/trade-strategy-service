package com.trade.strategy.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.trade.strategy.dto.PatternEvent;

@Service
public class PatternCacheService {

    private static class PatternRecord {
        final PatternEvent event;
        final Instant ts;

        PatternRecord(PatternEvent event, Instant ts) {
            this.event = event;
            this.ts = ts;
        }
    }

    private final Map<String, List<PatternRecord>> cache = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofMinutes(15);

    public void addPattern(String symbol, String patternName) {
        if (symbol == null || patternName == null) return;
        PatternEvent event = PatternEvent.builder()
                .symbol(symbol)
                .patternName(patternName)
                .build();
        addPattern(event);
    }

    public void addPattern(PatternEvent event) {
        if (event == null || event.getSymbol() == null || event.getPatternName() == null) return;
        String symbol = event.getSymbol();
        cache.compute(symbol, (k, v) -> {
            Instant now = Instant.now();
            List<PatternRecord> list = (v == null) ? new java.util.concurrent.CopyOnWriteArrayList<>() : v;
            list.add(new PatternRecord(event, now));
            // remove expired
            list.removeIf(r -> r.ts.isBefore(now.minus(ttl)));
            return list;
        });
    }

    public PatternEvent getLatestPattern(String symbol) {
        List<PatternRecord> list = cache.get(symbol);
        if (list == null) return null;
        Instant now = Instant.now();
        list.removeIf(r -> r.ts.isBefore(now.minus(ttl)));
        return list.isEmpty() ? null : list.get(list.size() - 1).event;
    }

    public List<String> getRecentPatterns(String symbol) {
        List<PatternRecord> list = cache.get(symbol);
        if (list == null) return java.util.Collections.emptyList();
        Instant now = Instant.now();
        list.removeIf(r -> r.ts.isBefore(now.minus(ttl)));
        return list.stream().map(r -> r.event.getPatternName()).collect(Collectors.toList());
    }
}
