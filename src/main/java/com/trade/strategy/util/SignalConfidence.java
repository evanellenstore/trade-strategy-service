package com.trade.strategy.util;

public final class SignalConfidence {

    private SignalConfidence() {
    }

    public static String classify(int confidence) {
        if (confidence >= 90) {
            return "VERY_HIGH";
        }
        if (confidence >= 70) {
            return "HIGH";
        }
        if (confidence >= 50) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
