package com.example.crobat.services;

import com.example.crobat.call.HttpCaller;
import com.example.crobat.db.JobStore;
import com.example.crobat.domain.Job;
import com.example.crobat.domain.Result;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Claims jobs, fires each on its own timer, calls the endpoint, saves the result. */
public class ExecutorLoop implements Runnable {
    private static final Logger LOG = Logger.getLogger(ExecutorLoop.class.getName());

    private final JobStore jobs;
    private final HttpCaller caller;
    private final String workerId;
    private final int workers;
    private final long pollMs;

    private final Semaphore slots;
    private final Semaphore wake = new Semaphore(0);
    private final AtomicInteger inFlight = new AtomicInteger();
    private volatile boolean running = true;
    private ScheduledThreadPoolExecutor timers;

    public ExecutorLoop(JobStore jobs, HttpCaller caller, String workerId,
                        int workers, long pollMs) {
        this.jobs = jobs;
        this.caller = caller;
        this.workerId = workerId;
        this.workers = Math.max(1, workers);
        this.pollMs = Math.max(50, pollMs);
        this.slots = new Semaphore(this.workers);
    }

    @Override
    public void run() {
        timers = new ScheduledThreadPoolExecutor(this.workers, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("crobat-job-timer");
            thread.setDaemon(true);
            return thread;
        });
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                int free = workers - inFlight.get();
                if (free > 0) {
                    claim(free);
                }
                wake.tryAcquire(pollMs, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            shutdownTimers();
        }
    }

    private void claim(int free) {
        Instant now = Instant.now();
        List<Job> claimed;
        try {
            claimed = jobs.claim(workerId, free, now.plusMillis(pollMs), now);
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "claim failed", ex);
            return;
        }
        for (Job job : claimed) {
            try {
                slots.acquire();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            inFlight.incrementAndGet();
            long delay = Math.max(0,
                    Duration.between(Instant.now(), job.getNextAttemptAt()).toMillis());
            try {
                timers.schedule(() -> fire(job), delay, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.RejectedExecutionException ex) {
                // Shutting down. The lease will expire and the reconciler will rescue it.
                inFlight.decrementAndGet();
                slots.release();
            }
        }
    }

    private void fire(Job job) {
        Result result = null;
        try {
            result = caller.call(job);
            job.apply(result);
            boolean saved = jobs.saveResult(job, workerId);
            long lagMs = result.getStartedAt() == null
                    ? 0
                    : Duration.between(job.getScheduledAt(), result.getStartedAt()).toMillis();
            String line = "job=" + job.getId()
                    + " task=" + job.getTaskId()
                    + " attempt=" + job.getAttempt()
                    + " status=" + job.getStatus().getJsonName()
                    + " http_status=" + result.getHttpStatus()
                    + " lag_ms=" + lagMs;
            if (!saved) {
                LOG.warning("result fenced off " + line);
            } else if (result.ok()) {
                LOG.info(line);
            } else {
                LOG.warning(line + " error=" + result.getError());
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.SEVERE, "job failed before result could be saved job="
                    + job.getId(), ex);
        } finally {
            inFlight.decrementAndGet();
            slots.release();
            wake.release();
        }
    }

    public void stop() {
        running = false;
        ScheduledThreadPoolExecutor current = timers;
        if (current != null) {
            current.shutdown();
        }
    }

    private void shutdownTimers() {
        ScheduledThreadPoolExecutor current = timers;
        if (current == null) {
            return;
        }
        current.shutdown();
        try {
            if (!current.awaitTermination(10, TimeUnit.SECONDS)) {
                current.shutdownNow();
            }
        } catch (InterruptedException ex) {
            current.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
