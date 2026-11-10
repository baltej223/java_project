package com.example.crobat.domain;

public final class Backoff {
    private static final long MAX_MILLIS = 3_600_000L;

    private Backoff() {
    }

    /** base * 2^(attempt-1), capped at one hour. */
    public static long millis(int baseMillis, int attempt) {
        long value = baseMillis;
        if (value <= 0) {
            return 0;
        }
        for (int i = 1; i < attempt && value < MAX_MILLIS; i++) {
            value *= 2;
        }
        return Math.min(value, MAX_MILLIS);
    }
}
