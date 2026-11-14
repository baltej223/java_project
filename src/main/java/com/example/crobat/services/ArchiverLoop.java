package com.example.crobat.services;

import com.example.crobat.db.HistoryStore;
import com.example.crobat.db.JobStore;
import com.example.crobat.domain.Job;

import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Moves finished jobs from the hot table to job_history, then deletes them. */
public class ArchiverLoop implements Runnable {
    private static final Logger LOG = Logger.getLogger(ArchiverLoop.class.getName());

    private final JobStore jobs;
    private final HistoryStore history;
    private final long intervalMs;
    private final int batch;
    private final long keepSucceededMs;
    private final long keepDeadMs;
    private final long historyTtlMs;

    public ArchiverLoop(JobStore jobs, HistoryStore history, long intervalMs, int batch,
                        long keepSucceededMs, long keepDeadMs, long historyTtlMs) {
        this.jobs = jobs;
        this.history = history;
        this.intervalMs = intervalMs;
        this.batch = batch;
        this.keepSucceededMs = keepSucceededMs;
        this.keepDeadMs = keepDeadMs;
        this.historyTtlMs = historyTtlMs;
    }

    public int tick() {
        Instant now = Instant.now();
        List<Job> archived = jobs.listArchivable(
                now.minusMillis(keepSucceededMs),
                now.minusMillis(keepDeadMs),
                batch);
        if (!archived.isEmpty()) {
            // History first, delete second: a crash leaves a duplicate, never a loss.
            history.archive(archived);
            jobs.deleteArchived(archived);
            LOG.info("archived jobs=" + archived.size());
        }
        history.deleteOlderThan(now.minusMillis(historyTtlMs));
        return archived.size();
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                int count = tick();
                if (count < batch) {
                    Thread.sleep(intervalMs);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                LOG.log(Level.SEVERE, "archiver tick failed", ex);
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
