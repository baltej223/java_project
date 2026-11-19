# Crobat Java

A Java rewrite of the Go **Crobat** project: cron as a service, with no message
broker. The queue is the MySQL `jobs` table, claimed with
`SELECT ... FOR UPDATE SKIP LOCKED`.

## Stack

- Java 17, Maven
- JDK `com.sun.net.httpserver.HttpServer` (no Spring)
- Plain JDBC + MySQL 8
- Jackson
- cron-utils
- React 18, TypeScript, Vite, Tailwind CSS, React Router
- JUnit 5

## Quick start

```bash
cp .env.example .env
mvn package
docker compose up -d --build
```

Open **http://localhost:8080** for the console and sign in:

```text
Tenant:   acme
Username: admin
Password: admin123
```

The demo user is seeded by `deploy/mysql/schema.sql`. Change it before sharing
a deployment.

The CLI still uses a token directly:

```bash
export JWT_SECRET=dev-only-jwt-secret-change-me
export CROBAT_TOKEN=$(java -jar target/crobat-java-1.0.jar cli token acme)
```

```bash
java -jar target/crobat-java-1.0.jar cli task create \
  -name hello -url http://echo/hello -priority critical

java -jar target/crobat-java-1.0.jar cli task list
java -jar target/crobat-java-1.0.jar cli job dlq
```

The app runs all roles in one JVM. It also accepts one role at a time:

```bash
java -jar target/crobat-java-1.0.jar task-api
java -jar target/crobat-java-1.0.jar job-api
java -jar target/crobat-java-1.0.jar scheduler
java -jar target/crobat-java-1.0.jar executor
java -jar target/crobat-java-1.0.jar reconciler
java -jar target/crobat-java-1.0.jar archiver
java -jar target/crobat-java-1.0.jar echo
```

The console is served by `all` mode on port 8080. The single-role `task-api`
and `job-api` modes are API-only.

## Web console

The React app is in `frontend/` and is served by the Java `HttpServer`; there is
no Node process in production.

| Page | What it does |
|---|---|
| `/login` | tenant + username + password login |
| Overview | task counts, DLQ count, recent tasks |
| Tasks | list, edit, pause, resume, delete |
| New task | create now / at / cron tasks with priority, timeout, retry, headers, body |
| Edit task | change the configuration; existing jobs keep their plan |
| Task detail | task configuration and job history, manual retry for dead jobs |
| DLQ | dead jobs and retry |

The login endpoint returns the same HS256 JWT used by the CLI, and the browser
stores it in `localStorage`. Protected pages redirect to `/login` when the token
is missing or expired. The console and API are same-origin on port 8080, so
there is no CORS setup.

Editing never returns stored headers or body. The edit form keeps the existing
encrypted secrets unless you uncheck **Keep the existing encrypted headers and
body**.

Frontend development:

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173, proxies /tasks, /jobs, /dlq to :8080
```

Production build for a locally run jar:

```bash
cd frontend
npm run build
cd ..
WEB_ROOT=frontend/dist java -jar target/crobat-java-1.0.jar all
```

`docker compose build` runs `npm ci` and `npm run build` in a Node stage and
copies `frontend/dist` into the runtime image.

## APIs

All routes except `GET /healthz` and `POST /auth/login` require
`Authorization: Bearer <HS256 JWT>`. The tenant is the token's `sub` claim.

In `all` mode, port 8080 serves both the task routes and the job routes so the
console can be same-origin. Port 8081 still exposes the job API separately.

| Method and path | Port |
|---|---|
| `POST /tasks`, `GET /tasks`, `GET /tasks/{id}`, `PUT /tasks/{id}` | 8080 |
| `POST /tasks/{id}/pause`, `POST /tasks/{id}/resume`, `DELETE /tasks/{id}` | 8080 |
| `POST /auth/login` | 8080 (public) |
| `GET /jobs/{id}`, `POST /jobs/{id}/retry` | 8080 and 8081 |
| `GET /tasks/{id}/jobs?limit=n`, `GET /dlq` | 8080 and 8081 |
| `GET /healthz` | both |
| `GET /`, `/assets/*` | 8080 (console) |

Example task:

```json
{
  "name": "daily-report",
  "cron": "0 9 * * *",
  "timezone": "Europe/Madrid",
  "priority": "high",
  "request": {
    "method": "POST",
    "url": "https://api.example.com/run",
    "headers": {"Authorization": "Bearer ..."},
    "body": "{}"
  },
  "timeout_ms": 10000,
  "retry": {"max_attempts": 3, "backoff_ms": 1000}
}
```

Use `"at": "2026-12-01T09:00:00Z"` for a one-shot task, or omit both `cron`
and `at` to run now.

## Build and test

```bash
mvn test
mvn package

cd frontend
npm install
npm run build
```

Unit tests need no database. The MySQL integration test is disabled by default:

```bash
RUN_DB_TESTS=1 \
MYSQL_URL='jdbc:mysql://localhost:3306/crobat_test?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
MYSQL_USER=crobat MYSQL_PASSWORD=crobat-dev-only \
SECRETS_KEY='ZGV2LW9ubHktc2VjcmV0cy1rZXktMzItYnl0ZXMhISE=' \
mvn test
```

The test database must have the tables from `deploy/mysql/schema.sql` without
the `CREATE DATABASE crobat; USE crobat;` lines, or use a copy of the schema
for `crobat_test`.

## Design notes

- **No broker.** A job is written to MySQL before it can run.
- **Leases live in the job row.** The reconciler returns expired leases to
  `pending` or `dead`.
- **`attempt` is a fencing token.** Every result write checks
  `id + status='running' + worker_id + attempt`.
- **Jobs copy their task's request.** Editing a task never changes a job that
  is already planned.
- **Secrets are AES-256-GCM at rest.** Headers and bodies are never returned by
  the API, logged, or archived.
- **Login users are hashed.** Passwords use PBKDF2-HMAC-SHA256; the JWT is the
  same HS256 token the CLI can mint. The demo credentials are for local use.
- **History is a MySQL table** (`job_history`) instead of Cassandra. The hot
  row is deleted only after history is written.
- **SSRF guard** blocks loopback, private, link-local, CGNAT, and special
  ranges; only ports 80 and 443; no proxy; no redirects. Java's built-in
  client cannot hook the dialer, so the check runs immediately before the
  request (documented residual DNS-rebinding risk).
- **The console is static.** React Router pages are served from `index.html`;
  the Java side only serves files and the existing JSON API.

See `impl.doc` for the full design and `AGENTS.md` for the project rules.
