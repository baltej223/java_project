package com.example.crobat.services;

import com.example.crobat.db.TaskStore;
import com.example.crobat.domain.DomainException;
import com.example.crobat.domain.Job;
import com.example.crobat.domain.NotFoundException;
import com.example.crobat.domain.Task;
import com.example.crobat.domain.TaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The write API for tasks. */
public class TaskService {
    private final TaskStore tasks;

    public TaskService(TaskStore tasks) {
        this.tasks = tasks;
    }

    public Task create(String tenantId, String idempotencyKey, Task input) {
        input.normalize();
        if (idempotencyKey != null && idempotencyKey.length() > 255) {
            throw new DomainException("Idempotency-Key too long");
        }

        Instant now = Instant.now();
        input.setId(UUID.randomUUID().toString());
        input.setTenantId(tenantId);
        input.setIdempotencyKey(idempotencyKey == null ? "" : idempotencyKey);
        input.setStatus(TaskStatus.ACTIVE);
        input.setVersion(1);
        input.setCreatedAt(now);
        input.setUpdatedAt(now);

        Job firstJob = null;
        if (input.getCron().isEmpty() && input.getAt() == null) {
            // "now": the task and its first job are saved in one transaction.
            firstJob = input.newJob(now);
        } else {
            Instant next = input.firstRun(now);
            if (next == null) {
                throw new DomainException("cron expression has no next run");
            }
            input.setNextRunAt(next);
        }
        return tasks.create(input, firstJob);
    }

    public Task get(String tenantId, String id) {
        return tasks.get(tenantId, id);
    }

    public List<Task> list(String tenantId) {
        return tasks.list(tenantId, 500);
    }

    public Task update(String tenantId, String id, Task input, boolean preserveSecrets) {
        Task task = tasks.get(tenantId, id);
        input.normalize();

        if (preserveSecrets
                && input.getRequest().getHeaders().isEmpty()
                && input.getRequest().getBody().isEmpty()) {
            input.getRequest().setHeaders(task.getRequest().getHeaders());
            input.getRequest().setBody(task.getRequest().getBody());
        }

        boolean scheduleChanged = !input.getCron().equals(task.getCron())
                || !input.getTimezone().equals(task.getTimezone())
                || !sameInstant(input.getAt(), task.getAt());

        task.setName(input.getName());
        task.setCron(input.getCron());
        task.setTimezone(input.getTimezone());
        task.setAt(input.getAt());
        task.setPriority(input.getPriority());
        task.setRequest(input.getRequest());
        task.setTimeoutMs(input.getTimeoutMs());
        task.setRetry(input.getRetry());

        if (scheduleChanged) {
            Instant next = task.firstRun(Instant.now());
            if (next == null && !task.getCron().isEmpty()) {
                throw new DomainException("cron expression has no next run");
            }
            task.setNextRunAt(next);
        }
        return save(task);
    }

    public Task pause(String tenantId, String id) {
        return setStatus(tenantId, id, TaskStatus.PAUSED);
    }

    public Task resume(String tenantId, String id) {
        return setStatus(tenantId, id, TaskStatus.ACTIVE);
    }

    public void delete(String tenantId, String id) {
        setStatus(tenantId, id, TaskStatus.DELETED);
    }

    private Task setStatus(String tenantId, String id, TaskStatus status) {
        Task task = tasks.get(tenantId, id);
        if (task.getStatus() == TaskStatus.DELETED) {
            throw new NotFoundException("task not found");
        }
        if (status == TaskStatus.ACTIVE
                && task.getStatus() == TaskStatus.PAUSED
                && !task.getCron().isEmpty()) {
            // Runs missed while paused are skipped on purpose.
            task.setNextRunAt(task.nextAfter(Instant.now()));
        }
        if (status == TaskStatus.DELETED) {
            task.setNextRunAt(null);
        }
        task.setStatus(status);
        return save(task);
    }

    private Task save(Task task) {
        task.setUpdatedAt(Instant.now());
        tasks.update(task);
        return task;
    }

    private static boolean sameInstant(Instant a, Instant b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }
}
