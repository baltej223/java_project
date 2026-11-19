-- Chroner Java schema. All times are UTC and use DATETIME(3).
-- Loaded by the MySQL container on first start.

CREATE DATABASE IF NOT EXISTS chroner;
USE chroner;

CREATE TABLE IF NOT EXISTS tasks (
  id              CHAR(36)     NOT NULL PRIMARY KEY,
  tenant_id       VARCHAR(64)  NOT NULL,
  name            VARCHAR(255) NOT NULL,
  cron_expr       VARCHAR(255) NULL,
  timezone        VARCHAR(64)  NOT NULL DEFAULT 'UTC',
  run_at          DATETIME(3)  NULL,
  priority        TINYINT      NOT NULL,
  method          VARCHAR(10)  NOT NULL,
  url             VARCHAR(2048) NOT NULL,
  secret          BLOB NULL,
  timeout_ms      INT          NOT NULL,
  max_attempts    INT          NOT NULL,
  backoff_ms      INT          NOT NULL,
  status          ENUM('active','paused','deleted') NOT NULL,
  next_run_at     DATETIME(3)  NULL,
  idempotency_key VARCHAR(255) NULL,
  version         INT          NOT NULL,
  created_at      DATETIME(3)  NOT NULL,
  updated_at      DATETIME(3)  NOT NULL,
  KEY idx_tenant (tenant_id, created_at),
  KEY idx_due (status, next_run_at),
  UNIQUE KEY uq_idem (tenant_id, idempotency_key)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS jobs (
  id               CHAR(36)     NOT NULL PRIMARY KEY,
  tenant_id        VARCHAR(64)  NOT NULL,
  task_id          CHAR(36)     NOT NULL,
  scheduled_at     DATETIME(3)  NOT NULL,
  priority         TINYINT      NOT NULL,
  method           VARCHAR(10)  NOT NULL,
  url              VARCHAR(2048) NOT NULL,
  secret           BLOB NULL,
  timeout_ms       INT          NOT NULL,
  max_attempts     INT          NOT NULL,
  backoff_ms       INT          NOT NULL,
  status           ENUM('pending','running','succeeded','dead') NOT NULL,
  attempt          INT          NOT NULL DEFAULT 0,
  next_attempt_at  DATETIME(3)  NOT NULL,
  worker_id        VARCHAR(64)  NULL,
  lease_expires_at DATETIME(3)  NULL,
  http_status      INT          NULL,
  error            VARCHAR(1024) NULL,
  response_body    VARCHAR(1024) NULL,
  created_at       DATETIME(3)  NOT NULL,
  started_at       DATETIME(3)  NULL,
  finished_at      DATETIME(3)  NULL,
  KEY idx_claim   (status, priority, next_attempt_at),
  KEY idx_oldest  (status, next_attempt_at),
  KEY idx_lease   (status, lease_expires_at),
  KEY idx_archive (status, finished_at),
  KEY idx_dlq     (tenant_id, status),
  KEY idx_task    (tenant_id, task_id, scheduled_at),
  UNIQUE KEY uq_task_run (task_id, scheduled_at)
) ENGINE=InnoDB;

-- The Java version's replacement for Cassandra. Append-only until the
-- 30-day cleanup, and never contains request headers, request bodies,
-- response bodies, or the secret blob.
CREATE TABLE IF NOT EXISTS job_history (
  id           CHAR(36)     NOT NULL PRIMARY KEY,
  tenant_id    VARCHAR(64)  NOT NULL,
  task_id      CHAR(36)     NOT NULL,
  scheduled_at DATETIME(3)  NOT NULL,
  priority     TINYINT      NOT NULL,
  method       VARCHAR(10)  NOT NULL,
  url          VARCHAR(2048) NOT NULL,
  timeout_ms   INT          NOT NULL,
  max_attempts INT          NOT NULL,
  backoff_ms   INT          NOT NULL,
  status       ENUM('succeeded','dead') NOT NULL,
  attempt      INT          NOT NULL,
  http_status  INT          NULL,
  error        VARCHAR(1024) NULL,
  created_at   DATETIME(3)  NOT NULL,
  started_at   DATETIME(3)  NULL,
  finished_at  DATETIME(3)  NULL,
  archived_at  DATETIME(3)  NOT NULL,
  KEY idx_history_task (tenant_id, task_id, scheduled_at),
  KEY idx_history_old (archived_at)
) ENGINE=InnoDB;

-- Login users. Passwords are PBKDF2-HMAC-SHA256, never plaintext.
CREATE TABLE IF NOT EXISTS users (
  tenant_id     VARCHAR(64) NOT NULL,
  username      VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at    DATETIME(3) NOT NULL,
  PRIMARY KEY (tenant_id, username)
) ENGINE=InnoDB;

-- Local demo login: tenant "acme", user "admin", password "admin123".
-- Change this before sharing a deployment.
INSERT INTO users (tenant_id, username, password_hash, created_at)
VALUES ('acme', 'admin',
        'pbkdf2_sha256$120000$AAECAwQFBgcICQoLDA0ODw==$qTnZDrKJXHYwEV6wcbHPg/fmXZ17uxBBfQDmEgaSE3I=',
        UTC_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE username = username;
