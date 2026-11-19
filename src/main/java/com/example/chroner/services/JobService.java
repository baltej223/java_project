package com.example.chroner.services;

import com.example.chroner.db.HistoryStore;
import com.example.chroner.db.JobStore;
import com.example.chroner.domain.Job;
import com.example.chroner.domain.NotFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The read side: status, history, DLQ, and manual retry. */
public class JobService {
    private final JobStore jobs;
    private final HistoryStore history;

    public JobService(JobStore jobs, HistoryStore history) {
        this.jobs = jobs;
        this.history = history;
    }

    public Job get(String tenantId, String id) {
        try {
            return jobs.get(tenantId, id);
        } catch (NotFoundException ex) {
            return history.get(tenantId, id);
        }
    }

    public List<Job> taskHistory(String tenantId, String taskId, int limit) {
        List<Job> merged = new ArrayList<>();
        merged.addAll(jobs.listForTask(tenantId, taskId, limit));
        merged.addAll(history.listForTask(tenantId, taskId, limit));

        Map<String, Job> unique = new LinkedHashMap<>();
        for (Job job : merged) {
            unique.putIfAbsent(job.getId(), job);
        }
        List<Job> out = new ArrayList<>(unique.values());
        out.sort(Comparator.comparing(Job::getScheduledAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    public List<Job> dlq(String tenantId) {
        return jobs.listDead(tenantId, 500);
    }

    public Job retry(String tenantId, String id) {
        jobs.retryDead(tenantId, id, Instant.now());
        return jobs.get(tenantId, id);
    }
}
