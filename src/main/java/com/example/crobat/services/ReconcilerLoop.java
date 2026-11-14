package com.example.crobat.services;

import com.example.crobat.db.JobStore;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Returns running jobs with an expired lease to pending (or dead). */
public class ReconcilerLoop implements Runnable {
    private static final Logger LOG = Logger.getLogger(ReconcilerLoop.class.getName());

    private final JobStore jobs;
    private final long intervalMs;
    private final int batch;

    public ReconcilerLoop(JobStore jobs, long intervalMs, int batch) {
        this.jobs = jobs;
        this.intervalMs = intervalMs;
        this.batch = batch;
    }

    public int tick() {
        return jobs.releaseExpired(Instant.now(), batch);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                int released = tick();
                if (released > 0) {
                    LOG.warning("released expired leases jobs=" + released);
                }
                if (released < batch) {
                    Thread.sleep(intervalMs);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                LOG.log(Level.SEVERE, "reconciler tick failed", ex);
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
