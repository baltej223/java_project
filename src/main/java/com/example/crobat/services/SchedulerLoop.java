package com.example.crobat.services;

import com.example.crobat.db.TaskStore;
import com.example.crobat.domain.Job;
import com.example.crobat.domain.Task;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Turns due tasks into pending jobs ahead of time. */
public class SchedulerLoop implements Runnable {
    private static final Logger LOG = Logger.getLogger(SchedulerLoop.class.getName());
    private static final int MAX_CATCH_UP = 1000;

    private final TaskStore tasks;
    private final long intervalMs;
    private final long lookaheadMs;
    private final int batch;

    public SchedulerLoop(TaskStore tasks, long intervalMs, long lookaheadMs, int batch) {
        this.tasks = tasks;
        this.intervalMs = intervalMs;
        this.lookaheadMs = lookaheadMs;
        this.batch = batch;
    }

    public int tick() {
        Instant horizon = Instant.now().plusMillis(lookaheadMs);
        return tasks.scheduleDue(horizon, batch, task -> plan(task, horizon));
    }

    private List<Job> plan(Task task, Instant horizon) {
        List<Job> jobs = new ArrayList<>();
        int count = 0;
        while (task.getNextRunAt() != null
                && !task.getNextRunAt().isAfter(horizon)
                && count < MAX_CATCH_UP) {
            jobs.add(task.newJob(task.getNextRunAt()));
            task.setNextRunAt(task.nextAfter(task.getNextRunAt()));
            count++;
        }
        return jobs;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                int handled = tick();
                if (handled > 0) {
                    LOG.info("scheduled tasks=" + handled);
                }
                if (handled < batch) {
                    Thread.sleep(intervalMs);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                LOG.log(Level.SEVERE, "schedule tick failed", ex);
                sleepQuietly();
            }
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(intervalMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
