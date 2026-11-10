package com.example.crobat.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Job {
    private String id;
    private String tenantId;
    private String taskId;
    private Instant scheduledAt;
    private Priority priority = Priority.NORMAL;
    private RequestSpec request = new RequestSpec();
    private int timeoutMs;
    private RetryPolicy retry = new RetryPolicy();
    private JobStatus status = JobStatus.PENDING;
    private int attempt;
    private Instant nextAttemptAt;
    private String workerId;
    private Instant leaseExpiresAt;
    private Integer httpStatus;
    private String error;
    private String responseBody;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;

    public Job() {
    }

    /**
     * Moves a running job to its next state: succeeded, pending with
     * backoff, or dead once its attempts are used up.
     */
    public void apply(Result result) {
        httpStatus = result.getHttpStatus();
        responseBody = truncate(result.getBody(), 1000);
        error = result.getError();
        if ((error == null || error.isEmpty()) && !result.ok()) {
            error = "http status " + result.getHttpStatus();
        }
        startedAt = result.getStartedAt();
        workerId = null;
        leaseExpiresAt = null;

        if (result.ok()) {
            status = JobStatus.SUCCEEDED;
            finishedAt = result.getFinishedAt();
            error = "";
        } else if (attempt >= retry.getMaxAttempts()) {
            status = JobStatus.DEAD;
            finishedAt = result.getFinishedAt();
        } else {
            status = JobStatus.PENDING;
            nextAttemptAt = result.getFinishedAt()
                    .plusMillis(Backoff.millis(retry.getBackoffMs(), attempt));
        }
        error = truncate(error, 1000);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public RequestSpec getRequest() {
        return request;
    }

    public void setRequest(RequestSpec request) {
        this.request = request;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public RetryPolicy getRetry() {
        return retry;
    }

    public void setRetry(RetryPolicy retry) {
        this.retry = retry;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public void setLeaseExpiresAt(Instant leaseExpiresAt) {
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
