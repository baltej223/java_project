package com.example.chroner.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobApplyTest {
    private Job job(int attempt) {
        Job job = new Job();
        job.setId("job-1");
        job.setScheduledAt(Instant.parse("2026-01-01T00:00:00Z"));
        job.setPriority(Priority.HIGH);
        job.setRequest(new RequestSpec("POST", "http://example.com", null, ""));
        job.setTimeoutMs(1000);
        job.setRetry(new RetryPolicy(3, 1000));
        job.setStatus(JobStatus.RUNNING);
        job.setAttempt(attempt);
        job.setWorkerId("worker-1");
        return job;
    }

    private Result result(int status, String error) {
        Result result = new Result();
        result.setHttpStatus(status);
        result.setError(error);
        result.setBody("body");
        result.setStartedAt(Instant.parse("2026-01-01T00:00:01Z"));
        result.setFinishedAt(Instant.parse("2026-01-01T00:00:02Z"));
        return result;
    }

    @Test
    void successMovesToSucceeded() {
        Job job = job(1);
        job.apply(result(200, ""));
        assertEquals(JobStatus.SUCCEEDED, job.getStatus());
        assertEquals("", job.getError());
        assertEquals(Instant.parse("2026-01-01T00:00:02Z"), job.getFinishedAt());
    }

    @Test
    void failureWithAttemptsLeftBacksOff() {
        Job job = job(1);
        job.apply(result(500, ""));
        assertEquals(JobStatus.PENDING, job.getStatus());
        assertEquals("http status 500", job.getError());
        assertEquals(Instant.parse("2026-01-01T00:00:03Z"), job.getNextAttemptAt());
    }

    @Test
    void failureOnLastAttemptGoesToDlq() {
        Job job = job(3);
        job.apply(result(500, ""));
        assertEquals(JobStatus.DEAD, job.getStatus());
    }

    @Test
    void networkErrorUsesErrorText() {
        Job job = job(1);
        job.apply(result(0, "timeout"));
        assertEquals(JobStatus.PENDING, job.getStatus());
        assertEquals("timeout", job.getError());
    }
}
