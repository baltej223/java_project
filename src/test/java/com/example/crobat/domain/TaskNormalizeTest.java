package com.example.crobat.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskNormalizeTest {
    private Task valid() {
        Task task = new Task();
        task.setName("hello");
        task.setRequest(new RequestSpec("POST", "http://example.com/run",
                Map.of(), ""));
        return task;
    }

    @Test
    void fillsDefaults() {
        Task task = valid();
        task.normalize();

        assertEquals("UTC", task.getTimezone());
        assertEquals(Priority.NORMAL, task.getPriority());
        assertEquals(10_000, task.getTimeoutMs());
        assertEquals(3, task.getRetry().getMaxAttempts());
        assertEquals(1_000, task.getRetry().getBackoffMs());
        assertEquals("POST", task.getRequest().getMethod());
    }

    @Test
    void rejectsCronAndAtTogether() {
        Task task = valid();
        task.setCron("0 9 * * *");
        task.setAt(Instant.parse("2026-12-01T09:00:00Z"));
        assertThrows(DomainException.class, task::normalize);
    }

    @Test
    void rejectsUnknownTimezone() {
        Task task = valid();
        task.setTimezone("Mars/Olympus");
        assertThrows(DomainException.class, task::normalize);
    }

    @Test
    void rejectsBadMethod() {
        Task task = valid();
        task.getRequest().setMethod("TRACE");
        assertThrows(DomainException.class, task::normalize);
    }

    @Test
    void rejectsRelativeUrl() {
        Task task = valid();
        task.getRequest().setUrl("/relative");
        assertThrows(DomainException.class, task::normalize);
    }

    @Test
    void rejectsBlankName() {
        Task task = valid();
        task.setName("   ");
        assertThrows(DomainException.class, task::normalize);
    }

    @Test
    void rejectsTimeoutOutOfRange() {
        Task task = valid();
        task.setTimeoutMs(50);
        assertThrows(DomainException.class, task::normalize);
    }

    @Test
    void rejectsAttemptsOutOfRange() {
        Task task = valid();
        task.getRetry().setMaxAttempts(0);
        task.normalize();
        assertEquals(3, task.getRetry().getMaxAttempts());

        task.getRetry().setMaxAttempts(26);
        assertThrows(DomainException.class, task::normalize);
    }
}
