package com.example.crobat.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Task {
    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private String id;
    private String tenantId;
    private String name = "";
    private String cron = "";
    private String timezone = "UTC";
    private Instant at;
    private Priority priority = Priority.NORMAL;
    private RequestSpec request = new RequestSpec();
    private int timeoutMs;
    private RetryPolicy retry = new RetryPolicy();
    private TaskStatus status = TaskStatus.ACTIVE;
    private Instant nextRunAt;

    @JsonIgnore
    private String idempotencyKey;

    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    public Task() {
    }

    /** Fills defaults and validates. Call before saving. */
    public void normalize() {
        name = name == null ? "" : name.trim();
        if (name.isEmpty() || name.length() > 255) {
            throw new DomainException("name is required (max 255 chars)");
        }

        cron = cron == null ? "" : cron.trim();
        if (!cron.isEmpty() && at != null) {
            throw new DomainException("set cron or at, not both");
        }

        timezone = timezone == null || timezone.isBlank() ? "UTC" : timezone.trim();
        try {
            ZoneId.of(timezone);
        } catch (RuntimeException ex) {
            throw new DomainException("unknown timezone \"" + timezone + "\"");
        }

        if (!cron.isEmpty()) {
            CronSchedule.validate(cron);
        }

        if (priority == null) {
            priority = Priority.NORMAL;
        }

        if (request == null) {
            request = new RequestSpec();
        }
        request.setMethod(request.getMethod() == null
                ? "POST"
                : request.getMethod().trim().toUpperCase(Locale.ROOT));
        if (request.getMethod().isEmpty()) {
            request.setMethod("POST");
        }
        if (!ALLOWED_METHODS.contains(request.getMethod())) {
            throw new DomainException("method \"" + request.getMethod() + "\" not allowed");
        }
        if (request.getHeaders() == null) {
            request.setHeaders(new LinkedHashMap<>());
        }
        if (request.getBody() == null) {
            request.setBody("");
        }

        String url = request.getUrl() == null ? "" : request.getUrl();
        if (url.length() > 2048) {
            throw new DomainException("url must be an absolute http(s) URL");
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null) {
                throw new DomainException("url must be an absolute http(s) URL");
            }
        } catch (DomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DomainException("url must be an absolute http(s) URL");
        }

        if (timeoutMs == 0) {
            timeoutMs = 10_000;
        }
        if (timeoutMs < 100 || timeoutMs > 300_000) {
            throw new DomainException("timeout_ms must be between 100 and 300000");
        }

        if (retry == null) {
            retry = new RetryPolicy();
        }
        if (retry.getMaxAttempts() == 0) {
            retry.setMaxAttempts(3);
        }
        if (retry.getMaxAttempts() < 1 || retry.getMaxAttempts() > 25) {
            throw new DomainException("retry.max_attempts must be between 1 and 25");
        }
        if (retry.getBackoffMs() == 0) {
            retry.setBackoffMs(1_000);
        }
        if (retry.getBackoffMs() < 100 || retry.getBackoffMs() > 3_600_000) {
            throw new DomainException("retry.backoff_ms must be between 100 and 3600000");
        }
    }

    /** When the task is first due, given the current time. */
    public Instant firstRun(Instant now) {
        if (!cron.isEmpty()) {
            return nextAfter(now);
        }
        if (at != null) {
            return at;
        }
        return now;
    }

    /**
     * The run following prev, or null for one-shot tasks. It uses the previous
     * scheduled time, not now, so runs missed during an outage catch up.
     */
    public Instant nextAfter(Instant prev) {
        if (cron.isEmpty()) {
            return null;
        }
        ZoneId zone = ZoneId.of(timezone);
        return CronSchedule.next(cron, zone, prev);
    }

    /**
     * Plans one execution. It copies the request, timeout, retry policy, and
     * priority so later edits never change an existing job.
     */
    public Job newJob(Instant at) {
        Job job = new Job();
        job.setId(UUID.randomUUID().toString());
        job.setTenantId(tenantId);
        job.setTaskId(id);
        job.setScheduledAt(at);
        job.setPriority(priority);
        job.setRequest(request.copy());
        job.setTimeoutMs(timeoutMs);
        job.setRetry(retry.copy());
        job.setStatus(JobStatus.PENDING);
        job.setAttempt(0);
        job.setNextAttemptAt(at);
        job.setCreatedAt(Instant.now());
        return job;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Instant getAt() {
        return at;
    }

    public void setAt(Instant at) {
        this.at = at;
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

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(Instant nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    @JsonIgnore
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
