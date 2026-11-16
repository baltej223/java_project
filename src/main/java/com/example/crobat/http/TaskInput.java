package com.example.crobat.http;

import com.example.crobat.domain.Priority;
import com.example.crobat.domain.RequestSpec;
import com.example.crobat.domain.RetryPolicy;
import com.example.crobat.domain.Task;

import java.time.Instant;

/** The JSON body of POST/PUT /tasks. */
public class TaskInput {
    private String name;
    private String cron;
    private Instant at;
    private String timezone = "UTC";
    private Priority priority = Priority.NORMAL;
    private RequestSpec request = new RequestSpec();
    private int timeoutMs;
    private RetryPolicy retry = new RetryPolicy();

    public Task toTask() {
        Task task = new Task();
        task.setName(name);
        task.setCron(cron == null ? "" : cron);
        task.setAt(at);
        task.setTimezone(timezone);
        task.setPriority(priority);
        task.setRequest(request);
        task.setTimeoutMs(timeoutMs);
        task.setRetry(retry);
        return task;
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

    public Instant getAt() {
        return at;
    }

    public void setAt(Instant at) {
        this.at = at;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
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
}
