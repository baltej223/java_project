package com.example.chroner.domain;

public class RetryPolicy {
    private int maxAttempts;
    private int backoffMs;

    public RetryPolicy() {
    }

    public RetryPolicy(int maxAttempts, int backoffMs) {
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    public RetryPolicy copy() {
        return new RetryPolicy(maxAttempts, backoffMs);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getBackoffMs() {
        return backoffMs;
    }

    public void setBackoffMs(int backoffMs) {
        this.backoffMs = backoffMs;
    }
}
