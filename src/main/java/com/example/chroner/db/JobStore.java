package com.example.chroner.db;

import com.example.chroner.domain.FairPick;
import com.example.chroner.domain.Job;
import com.example.chroner.domain.JobStatus;
import com.example.chroner.domain.NotFoundException;
import com.example.chroner.domain.Priority;
import com.example.chroner.domain.RequestSpec;
import com.example.chroner.domain.RetryPolicy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** The jobs table: the hot table and the queue. */
public class JobStore {
    private static final String COLUMNS =
            "id, tenant_id, task_id, scheduled_at, priority, method, url, secret, "
                    + "timeout_ms, max_attempts, backoff_ms, status, attempt, next_attempt_at, "
                    + "worker_id, lease_expires_at, http_status, error, response_body, "
                    + "created_at, started_at, finished_at";

    private final Database database;
    private final SecretsCipher cipher;

    public JobStore(Database database, SecretsCipher cipher) {
        this.database = database;
        this.cipher = cipher;
    }

    public List<Job> claim(String workerId, int n, Instant horizon, Instant now) {
        for (int attempt = 1; ; attempt++) {
            try {
                return claimOnce(workerId, n, horizon, now);
            } catch (SQLException ex) {
                if (attempt < 5 && isRetryable(ex)) {
                    sleep(attempt * 5L);
                    continue;
                }
                throw new DatabaseException("claim jobs", ex);
            }
        }
    }

    private List<Job> claimOnce(String workerId, int n, Instant horizon, Instant now)
            throws SQLException {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<FairPick.Candidate> candidates = new ArrayList<>();
                candidates.addAll(candidates(connection,
                        "next_attempt_at", horizon, Math.max(1, n / 10)));
                candidates.addAll(candidates(connection,
                        "priority, next_attempt_at", horizon, 4 * n));

                List<String> ids = FairPick.pick(candidates, n);
                if (ids.isEmpty()) {
                    connection.commit();
                    return new ArrayList<>();
                }

                String in = Sql.placeholders(ids.size());
                String update = "UPDATE jobs SET status = 'running', worker_id = ?, "
                        + "attempt = attempt + 1, "
                        + "lease_expires_at = DATE_ADD(GREATEST(next_attempt_at, ?), "
                        + "INTERVAL ((timeout_ms + 30000) * 1000) MICROSECOND) "
                        + "WHERE id IN (" + in + ")";
                try (PreparedStatement ps = connection.prepareStatement(update)) {
                    int index = 1;
                    ps.setString(index++, workerId);
                    Sql.setInstant(ps, index++, now);
                    for (String id : ids) {
                        ps.setString(index++, id);
                    }
                    ps.executeUpdate();
                }

                List<Job> jobs = queryJobs(connection,
                        "SELECT " + COLUMNS + " FROM jobs WHERE id IN (" + in + ")",
                        ids.toArray(new String[0]));
                connection.commit();
                return jobs;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                if (ex instanceof SQLException) {
                    throw (SQLException) ex;
                }
                throw (RuntimeException) ex;
            }
        }
    }

    private List<FairPick.Candidate> candidates(
            Connection connection, String order, Instant horizon, int limit)
            throws SQLException {
        String sql = "SELECT id, tenant_id FROM jobs "
                + "WHERE status = 'pending' AND next_attempt_at <= ? "
                + "ORDER BY " + order + " LIMIT ? FOR UPDATE SKIP LOCKED";
        List<FairPick.Candidate> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            Sql.setInstant(ps, 1, horizon);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new FairPick.Candidate(
                            rs.getString("id"), rs.getString("tenant_id")));
                }
            }
        }
        return out;
    }

    public boolean saveResult(Job job, String workerId) {
        for (int attempt = 1; ; attempt++) {
            try {
                return saveResultOnce(job, workerId);
            } catch (SQLException ex) {
                if (attempt < 5 && isRetryable(ex)) {
                    sleep(attempt * 5L);
                    continue;
                }
                throw new DatabaseException("save job result", ex);
            }
        }
    }

    private boolean saveResultOnce(Job job, String workerId) throws SQLException {
        String sql = "UPDATE jobs SET status = ?, next_attempt_at = ?, worker_id = NULL, "
                + "lease_expires_at = NULL, http_status = ?, error = ?, response_body = ?, "
                + "started_at = ?, finished_at = ? "
                + "WHERE id = ? AND status = 'running' AND worker_id = ? AND attempt = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, job.getStatus().getJsonName());
            Sql.setInstant(ps, 2, job.getNextAttemptAt() == null
                    ? job.getScheduledAt() : job.getNextAttemptAt());
            if (job.getHttpStatus() == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, job.getHttpStatus());
            }
            Sql.setString(ps, 4, truncate(job.getError()));
            Sql.setString(ps, 5, truncate(job.getResponseBody()));
            Sql.setInstant(ps, 6, job.getStartedAt());
            Sql.setInstant(ps, 7, job.getFinishedAt());
            ps.setString(8, job.getId());
            ps.setString(9, workerId);
            ps.setInt(10, job.getAttempt());
            return ps.executeUpdate() == 1;
        }
    }

    public int releaseExpired(Instant now, int limit) {
        String sql = "UPDATE jobs SET "
                + "status = IF(attempt >= max_attempts, 'dead', 'pending'), "
                + "finished_at = IF(attempt >= max_attempts, ?, NULL), "
                + "next_attempt_at = ?, worker_id = NULL, lease_expires_at = NULL, "
                + "error = 'lease expired: executor lost' "
                + "WHERE status = 'running' AND lease_expires_at < ? LIMIT ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            Sql.setInstant(ps, 1, now);
            Sql.setInstant(ps, 2, now);
            Sql.setInstant(ps, 3, now);
            ps.setInt(4, limit);
            return ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("release expired leases", ex);
        }
    }

    public List<Job> listArchivable(
            Instant succeededBefore, Instant deadBefore, int limit) {
        String sql = "(SELECT " + COLUMNS + " FROM jobs "
                + "WHERE status = 'succeeded' AND finished_at < ? LIMIT ?) "
                + "UNION ALL "
                + "(SELECT " + COLUMNS + " FROM jobs "
                + "WHERE status = 'dead' AND finished_at < ? LIMIT ?)";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            Sql.setInstant(ps, 1, succeededBefore);
            ps.setInt(2, limit - limit / 2);
            Sql.setInstant(ps, 3, deadBefore);
            ps.setInt(4, limit / 2);
            try (ResultSet rs = ps.executeQuery()) {
                List<Job> jobs = new ArrayList<>();
                while (rs.next()) {
                    jobs.add(scanJob(rs));
                }
                return jobs;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("list archivable jobs", ex);
        }
    }

    public void deleteArchived(List<Job> jobs) {
        String sql = "DELETE FROM jobs WHERE id = ? AND status = ? AND attempt = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Job job : jobs) {
                ps.setString(1, job.getId());
                ps.setString(2, job.getStatus().getJsonName());
                ps.setInt(3, job.getAttempt());
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new DatabaseException("delete archived jobs", ex);
        }
    }

    public Job get(String tenantId, String id) {
        String sql = "SELECT " + COLUMNS
                + " FROM jobs WHERE id = ? AND tenant_id = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("job not found");
                }
                return scanJob(rs);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("get job", ex);
        }
    }

    public List<Job> listForTask(String tenantId, String taskId, int limit) {
        String sql = "SELECT " + COLUMNS + " FROM jobs "
                + "WHERE tenant_id = ? AND task_id = ? "
                + "ORDER BY scheduled_at DESC LIMIT ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, taskId);
            ps.setInt(3, limit);
            return readJobs(ps);
        } catch (SQLException ex) {
            throw new DatabaseException("list jobs for task", ex);
        }
    }

    public List<Job> listDead(String tenantId, int limit) {
        String sql = "SELECT " + COLUMNS + " FROM jobs "
                + "WHERE tenant_id = ? AND status = 'dead' "
                + "ORDER BY finished_at DESC LIMIT ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setInt(2, limit);
            return readJobs(ps);
        } catch (SQLException ex) {
            throw new DatabaseException("list dead jobs", ex);
        }
    }

    public void retryDead(String tenantId, String id, Instant now) {
        String sql = "UPDATE jobs SET status = 'pending', next_attempt_at = ?, "
                + "max_attempts = attempt + 1, finished_at = NULL "
                + "WHERE id = ? AND tenant_id = ? AND status = 'dead'";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            Sql.setInstant(ps, 1, now);
            ps.setString(2, id);
            ps.setString(3, tenantId);
            if (ps.executeUpdate() == 0) {
                throw new NotFoundException("no dead job with that id in the DLQ");
            }
        } catch (SQLException ex) {
            throw new DatabaseException("retry dead job", ex);
        }
    }

    private List<Job> readJobs(PreparedStatement ps) throws SQLException {
        List<Job> jobs = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                jobs.add(scanJob(rs));
            }
        }
        return jobs;
    }

    private List<Job> queryJobs(Connection connection, String sql, String[] ids)
            throws SQLException {
        List<Job> jobs = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < ids.length; i++) {
                ps.setString(i + 1, ids[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    jobs.add(scanJob(rs));
                }
            }
        }
        return jobs;
    }

    private Job scanJob(ResultSet rs) throws SQLException {
        Job job = new Job();
        job.setId(rs.getString("id"));
        job.setTenantId(rs.getString("tenant_id"));
        job.setTaskId(rs.getString("task_id"));
        job.setScheduledAt(Sql.instant(rs, "scheduled_at"));
        job.setPriority(Priority.fromValue(rs.getInt("priority")));
        RequestSpec request = new RequestSpec();
        request.setMethod(rs.getString("method"));
        request.setUrl(rs.getString("url"));
        cipher.open(rs.getBytes("secret"), request);
        job.setRequest(request);
        job.setTimeoutMs(rs.getInt("timeout_ms"));
        job.setRetry(new RetryPolicy(rs.getInt("max_attempts"), rs.getInt("backoff_ms")));
        job.setStatus(JobStatus.parse(rs.getString("status")));
        job.setAttempt(rs.getInt("attempt"));
        job.setNextAttemptAt(Sql.instant(rs, "next_attempt_at"));
        job.setWorkerId(rs.getString("worker_id"));
        job.setLeaseExpiresAt(Sql.instant(rs, "lease_expires_at"));
        job.setHttpStatus(Sql.integer(rs, "http_status"));
        job.setError(rs.getString("error"));
        job.setResponseBody(rs.getString("response_body"));
        job.setCreatedAt(Sql.instant(rs, "created_at"));
        job.setStartedAt(Sql.instant(rs, "started_at"));
        job.setFinishedAt(Sql.instant(rs, "finished_at"));
        return job;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static boolean isRetryable(SQLException ex) {
        return ex.getErrorCode() == 1213
                || ex.getErrorCode() == 1205
                || "40001".equals(ex.getSQLState());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
