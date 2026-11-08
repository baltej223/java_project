# AGENTS.md — Java Crobat

This file tells any coding agent (or teammate) how to work in this repository.

## What this repository is

- A Java implementation of **Crobat**, a multi-tenant cron/HTTP job scheduler.
- `impl.doc` is the design document. `README.md` is the quick start.
- `crobat/` is the original **Go** implementation (reference only, ignored by
  git). Do not edit it and do not import it.
- The Java project is a single Maven module in this directory.

## Current phase

The v1 implementation is complete: domain, MySQL stores, six service roles,
REST APIs, CLI, Docker stack, and tests. Continue with small, tested changes.

## Product in one paragraph

A tenant creates a **task** (run now, at a date, or on a cron expression) that
calls an HTTP endpoint. The scheduler turns due tasks into **jobs**. Executors
claim jobs from MySQL with `SELECT ... FOR UPDATE SKIP LOCKED`, call the
endpoint, and save the result. Failures retry with exponential backoff;
exhausted jobs go to a DLQ. Finished jobs move to history. The queue is a MySQL
table; there is no Kafka, RabbitMQ, or Redis.

## Non-negotiable behavior

1. A task is configuration; a job is one execution and **copies** the task's
   request at plan time.
2. The `jobs` table is the queue. No message broker.
3. Claims use `FOR UPDATE SKIP LOCKED` and commit **before** any HTTP call.
4. Every claim increments `attempt`; every result write is guarded by
   `id + status = 'running' + worker_id + attempt`.
5. A lease lives in the job row; the reconciler returns expired leases to
   `pending` or `dead`.
6. Priority order is `critical`, `high`, `normal`, `low`; low priority must not
   starve.
7. Retry backoff is `backoff_ms * 2^(attempt-1)`, capped at 1 hour.
8. Each attempt sends `Idempotency-Key` (job id), `X-Scheduled-At`, and
   `X-Attempt`.
9. The tenant comes only from the JWT `sub` claim.
10. Request headers/body are encrypted at rest and never returned, logged, or
    archived.
11. Task creation is idempotent on `(tenant_id, Idempotency-Key)`.
12. History is written before the hot row is deleted.

## Stack

| Area | Choice |
|---|---|
| Language | Java 17 |
| Build | Maven, one module |
| Web | `com.sun.net.httpserver.HttpServer` from the JDK |
| JSON | Jackson, **snake_case** property names |
| Database | Plain JDBC + MySQL Connector/J |
| Cron parsing | `cron-utils` |
| Tests | JUnit 5 |
| Logging | `java.util.logging.Logger` |
| Deployment | One runnable JAR + Docker Compose |

**Do not add:** Spring/Spring Boot, Hibernate/JPA, Lombok, records, sealed
classes, virtual threads, reactive APIs, Kafka, RabbitMQ, Redis, Cassandra,
Kubernetes, a frontend.

Use ordinary Java that a second-year student can read: explicit classes,
fields, constructors, getters/setters, `for` loops over streams, named
exceptions, `java.time.Instant`, one public class per file, small methods.

## Commands

```bash
mvn test                 # unit tests, no database
mvn package              # target/crobat-java-1.0.jar
java -jar target/crobat-java-1.0.jar all

docker compose up -d --build
docker compose logs -f app echo
```

The app also runs one role at a time: `task-api`, `job-api`, `scheduler`,
`executor`, `reconciler`, `archiver`, `echo`.

MySQL integration test:

```bash
RUN_DB_TESTS=1 \
MYSQL_URL='jdbc:mysql://localhost:3306/crobat_test?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
MYSQL_USER=crobat MYSQL_PASSWORD=crobat-dev-only \
SECRETS_KEY='ZGV2LW9ubHktc2VjcmV0cy1rZXktMzItYnl0ZXMhISE=' \
mvn test
```

## Repository layout

```
pom.xml
Dockerfile
docker-compose.yml
deploy/mysql/schema.sql
src/main/java/com/example/crobat/
    Main.java
    config/Config.java
    domain/        enums, Task, Job, Result, cron, backoff, fair pick
    db/            Database, SecretsCipher, TaskStore, JobStore, HistoryStore
    services/      TaskService, JobService, four background loops
    http/          Json, Jwt, Router, TaskApi, JobApi, EchoApi
    call/          HttpCaller, SsrfGuard, Cidr
    cli/           Cli
src/test/java/com/example/crobat/...
```

## Coding rules

- SQL belongs only in `db/` classes. Services do not build SQL.
- Every SQL value is a `PreparedStatement` parameter. Never concatenate user
  input.
- Use UTC everywhere: `Instant`, `Timestamp.from(instant)`, and
  `serverTimezone=UTC`.
- Never hold a database transaction open while making an HTTP call.
- Do not catch an exception and continue as if the operation succeeded.
- Log the cause of internal errors, but return only
  `{"error":"internal error"}` to clients.
- Never log request headers, request bodies, response bodies, or full URLs with
  query strings.
- Use `UUID.randomUUID().toString()` for ids.
- Use `MessageDigest.isEqual` for signature comparison.
- Keep `domain` free of JDBC, HTTP, and config code.
- Add a comment only where the reason is not obvious (locks, fencing, leases).

## Database invariants

- `tasks.version` is optimistic concurrency for edits. The scheduler bumps it.
- `jobs.attempt` is the fencing token. It increases on every claim.
- `jobs.scheduled_at` is the promised run time; `next_attempt_at` is when the
  next attempt may fire.
- `jobs.lease_expires_at` is `GREATEST(next_attempt_at, now) + timeout + 30s`.
- `UNIQUE(task_id, scheduled_at)` makes planning the same run twice a no-op.
- A result write affecting 0 rows means the job is no longer ours. That is
  correct, not an error.
- Archive order: insert history first, delete hot row second, guarded by
  `status` and `attempt`.

## Git workflow

Remote: `git@github.com:baltej223/java_project.git`

- Use plain `git`; do not use `gh`.
- Commit with `timegit`, not `git commit`, when a backdated history is
  requested. Stage with `git add`, then `timegit new -m "<message>" "<time>"`.
- Keep `crobat/` ignored; it is the reference implementation, not part of the
  Java deliverable.

## Definition of done

A change is done only when:

- the requested behavior works end to end;
- the narrowest relevant test or manual scenario was run;
- secrets are not exposed in JSON or logs;
- a failed HTTP call leaves the job in `pending` or `dead`, never lost;
- `mvn test` passes;
- `impl.doc` is updated if behavior or structure changed.
