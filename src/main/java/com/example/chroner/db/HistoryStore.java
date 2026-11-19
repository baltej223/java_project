package com.example.chroner.db;

import com.example.chroner.domain.Job;
import com.example.chroner.domain.JobStatus;
import com.example.chroner.domain.NotFoundException;
import com.example.chroner.domain.Priority;
import com.example.chroner.domain.RequestSpec;
import com.example.chroner.domain.RetryPolicy;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Finished jobs. The Java version uses a MySQL table instead of Cassandra. */
public class HistoryStore {
    private static final String COLUMNS =
            "id, tenant_id, task_id, scheduled_at, priority, method, url, timeout_ms, "
                    + "max_attempts, backoff_ms, status, attempt, http_status, error, "
                    + "created_at, started_at, finished_at, archived_at";

    private final Database database;

    public HistoryStore(Database database) {
        this.database = database;
    }

    public void archive(List<Job> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO job_history (" + COLUMNS + ") VALUES ("
                + Sql.placeholders(18) + ") ON DUPLICATE KEY UPDATE id = id";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            Instant now = Instant.now();
            for (Job job : jobs) {
                ps.setString(1, job.getId());
                ps.setString(2, job.getTenantId());
                ps.setString(3, job.getTaskId());
                Sql.setInstant(ps, 4, job.getScheduledAt());
                ps.setInt(5, job.getPriority().getValue());
                ps.setString(6, job.getRequest().getMethod());
                ps.setString(7, sanitizeUrl(job.getRequest().getUrl()));
                ps.setInt(8, job.getTimeoutMs());
                ps.setInt(9, job.getRetry().getMaxAttempts());
                ps.setInt(10, job.getRetry().getBackoffMs());
                ps.setString(11, job.getStatus().getJsonName());
                ps.setInt(12, job.getAttempt());
                if (job.getHttpStatus() == null) {
                    ps.setNull(13, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(13, job.getHttpStatus());
                }
                Sql.setString(ps, 14, job.getError());
                Sql.setInstant(ps, 15, job.getCreatedAt());
                Sql.setInstant(ps, 16, job.getStartedAt());
                Sql.setInstant(ps, 17, job.getFinishedAt());
                Sql.setInstant(ps, 18, now);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException ex) {
            throw new DatabaseException("archive jobs", ex);
        }
    }

    public Job get(String tenantId, String id) {
        String sql = "SELECT " + COLUMNS
                + " FROM job_history WHERE id = ? AND tenant_id = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("job not found");
                }
                return scan(rs);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("get archived job", ex);
        }
    }

    public List<Job> listForTask(String tenantId, String taskId, int limit) {
        String sql = "SELECT " + COLUMNS + " FROM job_history "
                + "WHERE tenant_id = ? AND task_id = ? "
                + "ORDER BY scheduled_at DESC LIMIT ?";
        List<Job> jobs = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, taskId);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    jobs.add(scan(rs));
                }
            }
            return jobs;
        } catch (SQLException ex) {
            throw new DatabaseException("list archived jobs", ex);
        }
    }

    public int deleteOlderThan(Instant cutoff) {
        String sql = "DELETE FROM job_history WHERE archived_at < ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            Sql.setInstant(ps, 1, cutoff);
            return ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseException("delete old history", ex);
        }
    }

    private Job scan(ResultSet rs) throws SQLException {
        Job job = new Job();
        job.setId(rs.getString("id"));
        job.setTenantId(rs.getString("tenant_id"));
        job.setTaskId(rs.getString("task_id"));
        job.setScheduledAt(Sql.instant(rs, "scheduled_at"));
        job.setPriority(Priority.fromValue(rs.getInt("priority")));
        RequestSpec request = new RequestSpec();
        request.setMethod(rs.getString("method"));
        request.setUrl(rs.getString("url"));
        job.setRequest(request);
        job.setTimeoutMs(rs.getInt("timeout_ms"));
        job.setRetry(new RetryPolicy(rs.getInt("max_attempts"), rs.getInt("backoff_ms")));
        job.setStatus(JobStatus.parse(rs.getString("status")));
        job.setAttempt(rs.getInt("attempt"));
        job.setHttpStatus(Sql.integer(rs, "http_status"));
        job.setError(rs.getString("error"));
        job.setCreatedAt(Sql.instant(rs, "created_at"));
        job.setStartedAt(Sql.instant(rs, "started_at"));
        job.setFinishedAt(Sql.instant(rs, "finished_at"));
        return job;
    }

    /** Drops userinfo, query, and fragment so tokens in URLs are not archived. */
    static String sanitizeUrl(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            URI clean = new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    null,
                    null);
            return clean.toString();
        } catch (Exception ex) {
            int cut = url.length();
            for (char marker : new char[]{'?', '#'}) {
                int index = url.indexOf(marker);
                if (index >= 0 && index < cut) {
                    cut = index;
                }
            }
            return url.substring(0, cut);
        }
    }
}
