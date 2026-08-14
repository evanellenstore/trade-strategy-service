package com.trade.strategy.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class PatternCacheService {

    private static class PatternRecord {
        final String name;
        final Instant ts;

        PatternRecord(String name, Instant ts) {
            this.name = name;
            this.ts = ts;
        }
    }

    private final Map<String, List<PatternRecord>> cache = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofMinutes(15);

    public void addPattern(String symbol, String patternName) {
        if (symbol == null || patternName == null) return;
        cache.compute(symbol, (k, v) -> {
            Instant now = Instant.now();
            List<PatternRecord> list = (v == null) ? new java.util.concurrent.CopyOnWriteArrayList<>() : v;
            list.add(new PatternRecord(patternName, now));
            // remove expired
            list.removeIf(r -> r.ts.isBefore(now.minus(ttl)));
            return list;
        });
    }

    public List<String> getRecentPatterns(String symbol) {
        List<PatternRecord> list = cache.get(symbol);
        if (list == null) return java.util.Collections.emptyList();
        Instant now = Instant.now();
        list.removeIf(r -> r.ts.isBefore(now.minus(ttl)));
        return list.stream().map(r -> r.name).collect(Collectors.toList());
    }
}
