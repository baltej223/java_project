package com.example.crobat.db;

import com.example.crobat.config.Config;
import com.example.crobat.domain.Job;
import com.example.crobat.domain.JobStatus;
import com.example.crobat.domain.Priority;
import com.example.crobat.domain.RequestSpec;
import com.example.crobat.domain.Result;
import com.example.crobat.domain.RetryPolicy;
import com.example.crobat.domain.Task;
import com.example.crobat.services.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against a dedicated MySQL test database. Enable with:
 *   RUN_DB_TESTS=1 MYSQL_URL=jdbc:mysql://localhost:3306/crobat_test...
 */
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "1")
class DbIntegrationTest {
    private static Database database;
    private static SecretsCipher cipher;
    private static TaskStore tasks;
    private static JobStore jobs;
    private static HistoryStore history;
    private static TaskService taskService;
    private static String tenant;

    @BeforeAll
    static void setup() {
        Config config = new Config();
        database = new Database(config);
        database.ping();
        cipher = new SecretsCipher(config.requireSecretsKey());
        tasks = new TaskStore(database, cipher);
        jobs = new JobStore(database, cipher);
        history = new HistoryStore(database);
        taskService = new TaskService(tasks);
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (tenant == null) {
            return;
        }
        try (Connection connection = database.getConnection()) {
            for (String table : List.of("job_history", "jobs", "tasks")) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM " + table + " WHERE tenant_id = ?")) {
                    ps.setString(1, tenant);
                    ps.executeUpdate();
                }
            }
        }
    }

    @Test
    void createClaimFenceArchiveAndIdempotency() {
        tenant = "test-" + UUID.randomUUID();
        Task task = new Task();
        task.setName("integration");
        task.setPriority(Priority.HIGH);
        task.setRequest(new RequestSpec("POST", "http://8.8.8.8/hook",
                Map.of("Authorization", "Bearer secret"), "body"));
        task.setTimeoutMs(1000);
        task.setRetry(new RetryPolicy(2, 500));

        Task created = taskService.create(tenant, "idem-" + tenant, task);
        assertNotNull(created.getId());

        Task secondInput = new Task();
        secondInput.setName("integration");
        secondInput.setPriority(Priority.HIGH);
        secondInput.setRequest(new RequestSpec("POST", "http://8.8.8.8/hook",
                Map.of("Authorization", "Bearer secret"), "body"));
        secondInput.setTimeoutMs(1000);
        secondInput.setRetry(new RetryPolicy(2, 500));

        Task repeated = taskService.create(tenant, "idem-" + tenant, secondInput);
        assertEquals(created.getId(), repeated.getId());

        Task edit = new Task();
        edit.setName("integration-edited");
        edit.setPriority(Priority.HIGH);
        edit.setRequest(new RequestSpec("POST", "http://8.8.8.8/hook", Map.of(), ""));
        edit.setTimeoutMs(1000);
        edit.setRetry(new RetryPolicy(2, 500));
        Task updated = taskService.update(tenant, created.getId(), edit, true);
        assertEquals("integration-edited", updated.getName());
        assertEquals("Bearer secret",
                taskService.get(tenant, created.getId()).getRequest().getHeaders().get("Authorization"));

        List<Job> planned = jobs.listForTask(tenant, created.getId(), 10);
        assertEquals(1, planned.size());
        Job plannedJob = planned.get(0);
        assertEquals("Bearer secret", plannedJob.getRequest().getHeaders().get("Authorization"));

        Instant now = Instant.now();
        List<Job> claimed = jobs.claim("test-worker", 100,
                now.plusSeconds(60), now);
        Job claimedJob = null;
        for (Job job : claimed) {
            if (job.getId().equals(plannedJob.getId())) {
                claimedJob = job;
                break;
            }
        }
        assertNotNull(claimedJob, "our job was not claimed");
        assertEquals(1, claimedJob.getAttempt());
        assertEquals(JobStatus.RUNNING, claimedJob.getStatus());

        Result result = new Result();
        result.setHttpStatus(200);
        result.setStartedAt(Instant.now());
        result.setFinishedAt(Instant.now());
        claimedJob.apply(result);

        assertFalse(jobs.saveResult(claimedJob, "other-worker"));
        assertTrue(jobs.saveResult(claimedJob, "test-worker"));
        assertEquals(JobStatus.SUCCEEDED,
                jobs.get(tenant, claimedJob.getId()).getStatus());

        List<Job> archivable = jobs.listArchivable(
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(60),
                100);
        boolean found = false;
        for (Job job : archivable) {
            if (job.getId().equals(claimedJob.getId())) {
                found = true;
            }
        }
        assertTrue(found);
        history.archive(archivable);
        jobs.deleteArchived(archivable);

        Job archived = history.get(tenant, claimedJob.getId());
        assertEquals(JobStatus.SUCCEEDED, archived.getStatus());
        assertTrue(archived.getRequest().getHeaders().isEmpty());
        assertEquals("", archived.getRequest().getBody());
    }
    @Test
    void concurrentClaimsAreDisjoint() throws Exception {
        tenant = "test-" + UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            createNowTask("parallel-" + i);
        }
        Instant now = Instant.now();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<List<Job>> first = pool.submit(() -> {
                start.await();
                return jobs.claim("worker-a", 10, now.plusSeconds(60), now);
            });
            Future<List<Job>> second = pool.submit(() -> {
                start.await();
                return jobs.claim("worker-b", 10, now.plusSeconds(60), now);
            });
            start.countDown();

            Set<String> seen = new HashSet<>();
            int total = 0;
            for (Job job : first.get(10, TimeUnit.SECONDS)) {
                assertTrue(seen.add(job.getId()), "job claimed twice");
                total++;
            }
            for (Job job : second.get(10, TimeUnit.SECONDS)) {
                assertTrue(seen.add(job.getId()), "job claimed twice");
                total++;
            }
            assertEquals(10, total);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void expiredLeaseIsReturnedToPending() throws Exception {
        tenant = "test-" + UUID.randomUUID();
        Task created = createNowTask("lease");
        Job planned = jobs.listForTask(tenant, created.getId(), 10).get(0);

        Instant now = Instant.now();
        List<Job> claimed = jobs.claim("worker-a", 10, now.plusSeconds(60), now);
        boolean ours = claimed.stream().anyMatch(j -> j.getId().equals(planned.getId()));
        assertTrue(ours);

        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE jobs SET lease_expires_at = ? WHERE id = ?")) {
            ps.setTimestamp(1, java.sql.Timestamp.from(now.minusSeconds(10)));
            ps.setString(2, planned.getId());
            ps.executeUpdate();
        }

        assertTrue(jobs.releaseExpired(now, 100) >= 1);
        Job released = jobs.get(tenant, planned.getId());
        assertEquals(JobStatus.PENDING, released.getStatus());
        assertEquals(1, released.getAttempt());
    }

    private Task createNowTask(String name) {
        Task task = new Task();
        task.setName(name);
        task.setPriority(Priority.NORMAL);
        task.setRequest(new RequestSpec("GET", "http://8.8.8.8/hook", Map.of(), ""));
        task.setTimeoutMs(1000);
        task.setRetry(new RetryPolicy(2, 500));
        return taskService.create(tenant, "key-" + name + "-" + UUID.randomUUID(), task);
    }

}
