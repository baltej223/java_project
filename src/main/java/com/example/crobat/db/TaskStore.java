package com.example.crobat.db;

import com.example.crobat.domain.DomainException;
import com.example.crobat.domain.Job;
import com.example.crobat.domain.NotFoundException;
import com.example.crobat.domain.Priority;
import com.example.crobat.domain.RequestSpec;
import com.example.crobat.domain.RetryPolicy;
import com.example.crobat.domain.Task;
import com.example.crobat.domain.TaskStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** The tasks table, plus the scheduler's one-transaction planning operation. */
public class TaskStore {
    private static final String COLUMNS =
            "id, tenant_id, name, cron_expr, timezone, run_at, priority, method, url, secret, "
                    + "timeout_ms, max_attempts, backoff_ms, status, next_run_at, idempotency_key, "
                    + "version, created_at, updated_at";

    public interface JobPlanner {
        List<Job> plan(Task task);
    }

    private final Database database;
    private final SecretsCipher cipher;

    public TaskStore(Database database, SecretsCipher cipher) {
        this.database = database;
        this.cipher = cipher;
    }

    public Task create(Task task, Job firstJob) {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertTask(connection, task);
                if (firstJob != null) {
                    insertJobs(connection, List.of(firstJob), task.getCreatedAt());
                }
                connection.commit();
                return task;
            } catch (SQLException ex) {
                connection.rollback();
                if (ex.getErrorCode() == 1062
                        && task.getIdempotencyKey() != null
                        && !task.getIdempotencyKey().isEmpty()) {
                    Task existing = getByTenantAndIdempotencyKey(
                            task.getTenantId(), task.getIdempotencyKey());
                    if (existing != null) {
                        return existing;
                    }
                }
                throw new DatabaseException("create task", ex);
            } catch (RuntimeException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("create task", ex);
        }
    }

    private void insertTask(Connection connection, Task task) throws SQLException {
        String sql = "INSERT INTO tasks (" + COLUMNS + ") VALUES ("
                + Sql.placeholders(19) + ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, task.getId());
            ps.setString(2, task.getTenantId());
            ps.setString(3, task.getName());
            Sql.setString(ps, 4, Sql.emptyToNull(task.getCron()));
            ps.setString(5, task.getTimezone());
            Sql.setInstant(ps, 6, task.getAt());
            ps.setInt(7, task.getPriority().getValue());
            ps.setString(8, task.getRequest().getMethod());
            ps.setString(9, task.getRequest().getUrl());
            byte[] secret = cipher.seal(task.getRequest());
            if (secret == null) {
                ps.setNull(10, Types.BLOB);
            } else {
                ps.setBytes(10, secret);
            }
            ps.setInt(11, task.getTimeoutMs());
            ps.setInt(12, task.getRetry().getMaxAttempts());
            ps.setInt(13, task.getRetry().getBackoffMs());
            ps.setString(14, task.getStatus().getJsonName());
            Sql.setInstant(ps, 15, task.getNextRunAt());
            Sql.setString(ps, 16, Sql.emptyToNull(task.getIdempotencyKey()));
            ps.setInt(17, task.getVersion());
            Sql.setInstant(ps, 18, task.getCreatedAt());
            Sql.setInstant(ps, 19, task.getUpdatedAt());
            ps.executeUpdate();
        }
    }

    public Task get(String tenantId, String id) {
        String sql = "SELECT " + COLUMNS + " FROM tasks WHERE id = ? AND tenant_id = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("task not found");
                }
                return scanTask(rs);
            }
        } catch (SQLException ex) {
            throw new DatabaseException("get task", ex);
        }
    }

    private Task getByTenantAndIdempotencyKey(String tenantId, String key) {
        String sql = "SELECT " + COLUMNS
                + " FROM tasks WHERE tenant_id = ? AND idempotency_key = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? scanTask(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("get task by idempotency key", ex);
        }
    }

    public List<Task> list(String tenantId, int limit) {
        String sql = "SELECT " + COLUMNS + " FROM tasks "
                + "WHERE tenant_id = ? AND status <> 'deleted' "
                + "ORDER BY created_at DESC LIMIT ?";
        List<Task> tasks = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tasks.add(scanTask(rs));
                }
            }
            return tasks;
        } catch (SQLException ex) {
            throw new DatabaseException("list tasks", ex);
        }
    }

    public void update(Task task) {
        String sql = "UPDATE tasks SET name = ?, cron_expr = ?, timezone = ?, run_at = ?, "
                + "priority = ?, method = ?, url = ?, secret = ?, timeout_ms = ?, "
                + "max_attempts = ?, backoff_ms = ?, status = ?, next_run_at = ?, "
                + "version = version + 1, updated_at = ? "
                + "WHERE id = ? AND tenant_id = ? AND version = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, task.getName());
            Sql.setString(ps, 2, Sql.emptyToNull(task.getCron()));
            ps.setString(3, task.getTimezone());
            Sql.setInstant(ps, 4, task.getAt());
            ps.setInt(5, task.getPriority().getValue());
            ps.setString(6, task.getRequest().getMethod());
            ps.setString(7, task.getRequest().getUrl());
            byte[] secret = cipher.seal(task.getRequest());
            if (secret == null) {
                ps.setNull(8, Types.BLOB);
            } else {
                ps.setBytes(8, secret);
            }
            ps.setInt(9, task.getTimeoutMs());
            ps.setInt(10, task.getRetry().getMaxAttempts());
            ps.setInt(11, task.getRetry().getBackoffMs());
            ps.setString(12, task.getStatus().getJsonName());
            Sql.setInstant(ps, 13, task.getNextRunAt());
            Sql.setInstant(ps, 14, task.getUpdatedAt());
            ps.setString(15, task.getId());
            ps.setString(16, task.getTenantId());
            ps.setInt(17, task.getVersion());
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new DomainException("task changed concurrently, retry");
            }
            task.setVersion(task.getVersion() + 1);
        } catch (SQLException ex) {
            throw new DatabaseException("update task", ex);
        }
    }

    /**
     * Locks active tasks due before the horizon, asks the planner for their
     * jobs, inserts them, and advances next_run_at in one transaction.
     */
    public int scheduleDue(Instant horizon, int limit, JobPlanner planner) {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<Task> tasks = new ArrayList<>();
                String select = "SELECT " + COLUMNS + " FROM tasks "
                        + "WHERE status = 'active' AND next_run_at <= ? "
                        + "ORDER BY next_run_at LIMIT ? FOR UPDATE SKIP LOCKED";
                try (PreparedStatement ps = connection.prepareStatement(select)) {
                    Sql.setInstant(ps, 1, horizon);
                    ps.setInt(2, limit);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            tasks.add(scanTask(rs));
                        }
                    }
                }

                List<Job> jobs = new ArrayList<>();
                for (Task task : tasks) {
                    jobs.addAll(planner.plan(task));
                }
                insertJobs(connection, jobs, Instant.now());

                String update = "UPDATE tasks SET next_run_at = ?, version = version + 1 "
                        + "WHERE id = ?";
                try (PreparedStatement ps = connection.prepareStatement(update)) {
                    for (Task task : tasks) {
                        Sql.setInstant(ps, 1, task.getNextRunAt());
                        ps.setString(2, task.getId());
                        ps.executeUpdate();
                    }
                }
                connection.commit();
                return tasks.size();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                if (ex instanceof SQLException) {
                    throw new DatabaseException("schedule due tasks", ex);
                }
                throw (RuntimeException) ex;
            }
        } catch (SQLException ex) {
            throw new DatabaseException("schedule due tasks", ex);
        }
    }

    private void insertJobs(Connection connection, List<Job> jobs, Instant createdAt)
            throws SQLException {
        int chunkSize = 500;
        int offset = 0;
        while (offset < jobs.size()) {
            int end = Math.min(offset + chunkSize, jobs.size());
            List<Job> chunk = jobs.subList(offset, end);
            String values = Sql.placeholders(14);
            StringBuilder sql = new StringBuilder(
                    "INSERT INTO jobs (id, tenant_id, task_id, scheduled_at, priority, method, "
                            + "url, secret, timeout_ms, max_attempts, backoff_ms, status, "
                            + "next_attempt_at, created_at) VALUES ");
            for (int i = 0; i < chunk.size(); i++) {
                if (i > 0) {
                    sql.append(',');
                }
                sql.append('(').append(values).append(')');
            }
            sql.append(" ON DUPLICATE KEY UPDATE id = id");

            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                int index = 1;
                for (Job job : chunk) {
                    ps.setString(index++, job.getId());
                    ps.setString(index++, job.getTenantId());
                    ps.setString(index++, job.getTaskId());
                    Sql.setInstant(ps, index++, job.getScheduledAt());
                    ps.setInt(index++, job.getPriority().getValue());
                    ps.setString(index++, job.getRequest().getMethod());
                    ps.setString(index++, job.getRequest().getUrl());
                    byte[] secret = cipher.seal(job.getRequest());
                    if (secret == null) {
                        ps.setNull(index++, Types.BLOB);
                    } else {
                        ps.setBytes(index++, secret);
                    }
                    ps.setInt(index++, job.getTimeoutMs());
                    ps.setInt(index++, job.getRetry().getMaxAttempts());
                    ps.setInt(index++, job.getRetry().getBackoffMs());
                    ps.setString(index++, job.getStatus().getJsonName());
                    Sql.setInstant(ps, index++, job.getNextAttemptAt());
                    Sql.setInstant(ps, index++, createdAt);
                }
                ps.executeUpdate();
            }
            offset = end;
        }
    }

    private Task scanTask(ResultSet rs) throws SQLException {
        Task task = new Task();
        task.setId(rs.getString("id"));
        task.setTenantId(rs.getString("tenant_id"));
        task.setName(rs.getString("name"));
        String cron = rs.getString("cron_expr");
        task.setCron(cron == null ? "" : cron);
        task.setTimezone(rs.getString("timezone"));
        task.setAt(Sql.instant(rs, "run_at"));
        task.setPriority(Priority.fromValue(rs.getInt("priority")));
        RequestSpec request = new RequestSpec();
        request.setMethod(rs.getString("method"));
        request.setUrl(rs.getString("url"));
        cipher.open(rs.getBytes("secret"), request);
        task.setRequest(request);
        task.setTimeoutMs(rs.getInt("timeout_ms"));
        task.setRetry(new RetryPolicy(rs.getInt("max_attempts"), rs.getInt("backoff_ms")));
        task.setStatus(TaskStatus.parse(rs.getString("status")));
        task.setNextRunAt(Sql.instant(rs, "next_run_at"));
        task.setIdempotencyKey(rs.getString("idempotency_key"));
        task.setVersion(rs.getInt("version"));
        task.setCreatedAt(Sql.instant(rs, "created_at"));
        task.setUpdatedAt(Sql.instant(rs, "updated_at"));
        return task;
    }

}
