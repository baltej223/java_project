package com.example.crobat.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackoffTest {
    @Test
    void doublesPerAttempt() {
        assertEquals(1000, Backoff.millis(1000, 1));
        assertEquals(2000, Backoff.millis(1000, 2));
        assertEquals(8000, Backoff.millis(1000, 4));
    }

    @Test
    void capsAtOneHour() {
        assertEquals(3_600_000, Backoff.millis(1000, 20));
        assertEquals(3_600_000, Backoff.millis(3_600_000, 2));
    }

    @Test
    void zeroBaseStaysZero() {
        assertEquals(0, Backoff.millis(0, 5));
    }
}
