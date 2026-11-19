package com.example.chroner.config;

import java.net.InetAddress;
import java.util.Base64;

/** Environment variables, with defaults for local development. */
public class Config {
    private final String mysqlUrl;
    private final String mysqlUser;
    private final String mysqlPassword;
    private final String secretsKeyBase64;
    private final String jwtSecret;
    private final int taskApiPort;
    private final int jobApiPort;
    private final int echoPort;
    private final long schedulerIntervalMs;
    private final long schedulerLookaheadMs;
    private final int schedulerBatch;
    private final int executorWorkers;
    private final long executorPollMs;
    private final String workerId;
    private final String executorAllowCidrs;
    private final String webRoot;
    private final long authTokenTtlSeconds;
    private final long reconcilerIntervalMs;
    private final int reconcilerBatch;
    private final long archiverIntervalMs;
    private final int archiverBatch;
    private final long archiveSucceededAfterMs;
    private final long archiveDeadAfterMs;
    private final long historyTtlMs;

    public Config() {
        mysqlUrl = env("MYSQL_URL",
                "jdbc:mysql://localhost:3306/chroner"
                        + "?useSSL=false&allowPublicKeyRetrieval=true"
                        + "&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        mysqlUser = env("MYSQL_USER", "chroner");
        mysqlPassword = env("MYSQL_PASSWORD", "chroner");
        secretsKeyBase64 = env("SECRETS_KEY", "");
        jwtSecret = env("JWT_SECRET", "");
        taskApiPort = intEnv("TASK_API_PORT", 8080);
        jobApiPort = intEnv("JOB_API_PORT", 8081);
        echoPort = intEnv("ECHO_PORT", 8083);
        schedulerIntervalMs = longEnv("SCHEDULER_INTERVAL_MS", 5_000);
        schedulerLookaheadMs = longEnv("SCHEDULER_LOOKAHEAD_MS", 30_000);
        schedulerBatch = intEnv("SCHEDULER_BATCH", 500);
        executorWorkers = intEnv("EXECUTOR_WORKERS", 64);
        executorPollMs = longEnv("EXECUTOR_POLL_MS", 500);
        workerId = env("WORKER_ID", defaultWorkerId());
        executorAllowCidrs = env("EXECUTOR_ALLOW_CIDRS", "");
        webRoot = env("WEB_ROOT", "frontend/dist");
        authTokenTtlSeconds = longEnv("AUTH_TOKEN_TTL_SECONDS", 86_400);
        reconcilerIntervalMs = longEnv("RECONCILER_INTERVAL_MS", 5_000);
        reconcilerBatch = intEnv("RECONCILER_BATCH", 1000);
        archiverIntervalMs = longEnv("ARCHIVER_INTERVAL_MS", 10_000);
        archiverBatch = intEnv("ARCHIVER_BATCH", 500);
        archiveSucceededAfterMs = longEnv("ARCHIVE_SUCCEEDED_AFTER_MINUTES", 60) * 60_000L;
        archiveDeadAfterMs = longEnv("ARCHIVE_DEAD_AFTER_HOURS", 336) * 3_600_000L;
        historyTtlMs = longEnv("HISTORY_TTL_DAYS", 30) * 86_400_000L;
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    private static int intEnv(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(name + " must be an integer");
        }
    }

    private static long longEnv(String name, long defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(name + " must be a number");
        }
    }

    private static String defaultWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName()
                    + "-" + ProcessHandle.current().pid();
        } catch (Exception ex) {
            return "worker-" + ProcessHandle.current().pid();
        }
    }

    public byte[] requireSecretsKey() {
        if (secretsKeyBase64 == null || secretsKeyBase64.isBlank()) {
            throw new IllegalStateException("SECRETS_KEY is required");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(secretsKeyBase64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("SECRETS_KEY must be base64");
        }
        if (key.length != 32) {
            throw new IllegalStateException(
                    "SECRETS_KEY must be 32 bytes, base64-encoded (openssl rand -base64 32)");
        }
        return key;
    }

    public String requireJwtSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required");
        }
        return jwtSecret;
    }

    public String getMysqlUrl() {
        return mysqlUrl;
    }

    public String getMysqlUser() {
        return mysqlUser;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public int getTaskApiPort() {
        return taskApiPort;
    }

    public int getJobApiPort() {
        return jobApiPort;
    }

    public int getEchoPort() {
        return echoPort;
    }

    public long getSchedulerIntervalMs() {
        return schedulerIntervalMs;
    }

    public long getSchedulerLookaheadMs() {
        return schedulerLookaheadMs;
    }

    public int getSchedulerBatch() {
        return schedulerBatch;
    }

    public int getExecutorWorkers() {
        return executorWorkers;
    }

    public long getExecutorPollMs() {
        return executorPollMs;
    }

    public String getWorkerId() {
        return workerId;
    }

    public String getExecutorAllowCidrs() {
        return executorAllowCidrs;
    }

    public String getWebRoot() {
        return webRoot;
    }

    public long getAuthTokenTtlSeconds() {
        return authTokenTtlSeconds;
    }

    public long getReconcilerIntervalMs() {
        return reconcilerIntervalMs;
    }

    public int getReconcilerBatch() {
        return reconcilerBatch;
    }

    public long getArchiverIntervalMs() {
        return archiverIntervalMs;
    }

    public int getArchiverBatch() {
        return archiverBatch;
    }

    public long getArchiveSucceededAfterMs() {
        return archiveSucceededAfterMs;
    }

    public long getArchiveDeadAfterMs() {
        return archiveDeadAfterMs;
    }

    public long getHistoryTtlMs() {
        return historyTtlMs;
    }
}
