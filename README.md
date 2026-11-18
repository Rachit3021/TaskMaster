# TaskFlow API

A production-style REST API for team task tracking and collaboration, built with **Java 21** and **Spring Boot 3.5**. It covers the full workflow: accounts and sessions, teams and invitations, projects, tasks with filtering/sorting/search, comments, file attachments, and real-time notifications over Server-Sent Events.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

---

## Table of contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Getting started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Option A — H2 (zero setup)](#option-a--h2-zero-setup)
  - [Option B — PostgreSQL locally](#option-b--postgresql-locally)
  - [Option C — Docker Compose](#option-c--docker-compose)
  - [Demo data](#demo-data)
- [Configuration](#configuration)
- [API guide](#api-guide)
  - [Authentication](#authentication)
  - [Error format](#error-format)
  - [Pagination](#pagination)
  - [Enumerations](#enumerations)
  - [Endpoints](#endpoints)
  - [Task filtering, sorting and search](#task-filtering-sorting-and-search)
  - [Real-time notifications (SSE)](#real-time-notifications-sse)
- [curl walkthrough](#curl-walkthrough)
- [Tests](#tests)
- [Design decisions](#design-decisions)
- [Limitations and next steps](#limitations-and-next-steps)
- [License](#license)

---

## Features

**Accounts and security**

- Registration, login, profile updates and password changes.
- BCrypt password hashing (strength 10).
- Short-lived JWT access tokens (15 minutes, HS256) and opaque refresh tokens (7 days).
- Refresh-token rotation with reuse detection; only SHA-256 hashes are stored.
- Single-session logout and global logout (`logout-all`).
- Changing a password revokes every refresh token for that user.
- Stateless Spring Security filter chain with uniform JSON `401`/`403` responses.

**Teams and projects**

- Team creation with an owner, admins and members.
- Shareable invite codes (rotatable) and email invitations with expiring tokens.
- Role hierarchy: `OWNER` → `ADMIN` → `MEMBER`, with guarded membership checks.
- Projects belong to a team and can be archived; archived projects reject new tasks.

**Tasks**

- Full CRUD with title, description, status, priority, due date, assignee and labels.
- Assignment restricted to team members; unassignment via explicit `null`.
- Automatic `completedAt` handling when moving in and out of `DONE`.
- Overdue indicator calculated per task.
- Filtering by status, priority, assignee, due-date range, overdue flag and free-text search.
- Whitelisted sorting with direction validation.
- Cross-team "assigned to me" view.

**Collaboration**

- Comments with author-only editing and author/manager deletion.
- Attachments stored on the local filesystem with content-type allowlisting, blocked executable extensions, size limits and sanitized file names.
- Persisted in-app notifications for assignments, updates, completions, comments, invitations and joins.
- Live notification delivery over SSE with heartbeat support.

**Operational quality**

- Bean validation on every request DTO and constrained query parameter.
- One consistent JSON error contract with field-level validation details.
- OpenAPI 3 documentation and Swagger UI.
- Flyway migrations for PostgreSQL; Hibernate schema validation in that profile.
- Actuator health/info endpoints.
- 24 HTTP-level integration tests plus GitHub Actions CI.

> AI-generated task descriptions were intentionally left out of scope. Everything below is fully implemented.

---

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.5.16 (Web, Data JPA, Security, Validation, Actuator) |
| Build | Maven (with Maven Wrapper) |
| Databases | H2 (local default and tests), PostgreSQL 16 (production-style profile) |
| Migrations | Flyway |
| Auth | Spring Security, JJWT 0.12, BCrypt, opaque refresh tokens |
| Docs | springdoc-openapi 2.8 + Swagger UI |
| Tests | JUnit 5, Spring Boot Test, MockMvc, AssertJ |
| Containers | Dockerfile (multi-stage) and Docker Compose |

---

## Architecture

```
┌──────────────┐        HTTPS / JSON        ┌─────────────────────────────┐
│   Client     │ ─────────────────────────► │  Spring Security filter     │
│ web / mobile │ ◄───────────────────────── │  (JWT bearer authentication)│
└──────────────┘        SSE stream          └──────────────┬──────────────┘
                                                           │
                                            ┌──────────────▼──────────────┐
                                            │        REST controllers     │
                                            │  auth · users · teams ·     │
                                            │  projects · tasks ·         │
                                            │  comments · attachments ·   │
                                            │  notifications              │
                                            └──────────────┬──────────────┘
                                                           │
                                            ┌──────────────▼──────────────┐
                                            │         Services            │
                                            │ business rules, permissions │
                                            │ notifications, storage      │
                                            └───────┬──────────────┬──────┘
                                                    │              │
                                     ┌──────────────▼───┐   ┌──────▼──────────────┐
                                     │  JPA repositories │   │ Local file storage  │
                                     └────────┬──────────┘   └─────────────────────┘
                                              │
                              ┌───────────────▼────────────────┐
                              │ H2 (dev/test) or PostgreSQL    │
                              │ Flyway migrations in postgres  │
                              └────────────────────────────────┘
```

**Domain model**

```
User ──< TeamMember >── Team ──< Project ──< Task ──< Comment
                         │                     │
                         └──< Invitation       └──< Attachment
User ──< RefreshToken
User ──< Notification
```

- A `Team` has exactly one `OWNER`, any number of `ADMIN`s and `MEMBER`s.
- Every `Project` belongs to one `Team`; every `Task` belongs to one `Project`.
- Comments, attachments and notifications are scoped to tasks/teams and inherit access checks from them.

---

## Project structure

```
src/main/java/com/taskflow/
├── auth/            # registration, login, refresh rotation, logout
├── user/            # profile, password, teammate search
├── team/            # teams, memberships, roles, invitations
├── project/         # projects and archive state
├── task/            # task CRUD, filters, specifications, sorting
├── comment/         # task discussions
├── attachment/      # upload/download/storage
├── notification/    # persisted notifications + SSE
├── security/        # JWT filter/service, principals, 401/403 handlers
├── config/          # security, CORS, OpenAPI, storage, seeding
└── common/          # pagination envelope, errors, after-commit helper

src/main/resources/
├── application.yml             # default H2 setup
├── application-postgres.yml    # PostgreSQL + Flyway
└── db/migration/V1__init.sql   # PostgreSQL schema

src/test/java/com/taskflow/
├── IntegrationTestSupport.java
├── AuthIntegrationTest.java
├── TeamIntegrationTest.java
├── TaskIntegrationTest.java
├── CommentAttachmentIntegrationTest.java
└── NotificationIntegrationTest.java
```

---

## Getting started

### Prerequisites

- **JDK 21 or newer.** The project targets Java 21.
- Maven is **not** required — use the wrapper (`./mvnw` or `mvnw.cmd` on Windows).
- Optional: Docker Desktop for the PostgreSQL/container workflows.

The examples below use the Unix wrapper. On Windows PowerShell use `.\mvnw.cmd` instead of `./mvnw`.

### Option A — H2 (zero setup)

The default profile uses a file-backed H2 database at `./data/taskflow` and creates/updates the schema automatically. No database installation is needed.

```bash
./mvnw spring-boot:run
```

The API starts at `http://localhost:8080`.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`

To start from an empty database, stop the app and delete the `data/` directory.

### Option B — PostgreSQL locally

1. Create a database and user (defaults match the app):

   ```sql
   CREATE DATABASE taskflow;
   CREATE USER taskflow WITH PASSWORD 'taskflow';
   GRANT ALL PRIVILEGES ON DATABASE taskflow TO taskflow;
   ```

2. Run with the `postgres` profile:

   ```bash
   SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run
   ```

   On PowerShell:

   ```powershell
   $env:SPRING_PROFILES_ACTIVE='postgres'; .\mvnw.cmd spring-boot:run
   ```

Flyway applies `V1__init.sql` on first start and Hibernate runs in `validate` mode, so schema drift fails fast.

Connection settings can be overridden with `DB_HOST`, `DB_PORT`, `POSTGRES_DB`, `POSTGRES_USER` and `POSTGRES_PASSWORD`.

### Option C — Docker Compose

```bash
cp .env.example .env       # adjust secrets first
docker compose up --build
```

This starts:

- `taskflow-db` — PostgreSQL 16 with a persistent volume and health check.
- `taskflow-api` — the application on port `8080`, waiting for the database to become healthy.

Uploads are stored in a named volume. Stop everything with `docker compose down` (add `-v` to delete the database and upload volumes).

### Demo data

Set `SEED_DATA=true` (or `app.seed.enabled=true`) on first start to create a demo workspace. Seeding is skipped when any user already exists.

| User | Email | Password | Role |
| --- | --- | --- | --- |
| Demo User | `demo@taskflow.dev` | `Demo@12345` | Team owner |
| Maya Sharma | `maya@taskflow.dev` | `Demo@12345` | Team admin |
| Arjun Patel | `arjun@taskflow.dev` | `Demo@12345` | Team member |

The seeded team is **TaskFlow Product** with invite code `TASKFLOW01`, two projects, several tasks, labels and comments.

---

## Configuration

All settings are environment-overridable.

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP port |
| `SPRING_PROFILES_ACTIVE` | `dev` | Use `postgres` for PostgreSQL |
| `DB_HOST` / `DB_PORT` | `localhost` / `5432` | PostgreSQL host and port |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `taskflow` / `taskflow` / `taskflow` | Database credentials |
| `JWT_SECRET` | dev-only value | HMAC secret, **at least 32 bytes** |
| `JWT_ACCESS_TTL` | `15m` | Access-token lifetime |
| `JWT_REFRESH_TTL` | `7d` | Refresh-token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Comma-separated allowed origins |
| `UPLOAD_DIR` | `./data/uploads` | Attachment storage directory |
| `MAX_FILE_SIZE_BYTES` | `10485760` | Per-file upload limit (10 MB) |
| `SEED_DATA` | `false` | Enable demo seeding on first start |

Never deploy with the default `JWT_SECRET`. Generate one with `openssl rand -base64 48`.

---

## API guide

Base path: `/api/v1`

### Authentication

1. `POST /auth/register` or `POST /auth/login` returns:

   ```json
   {
     "accessToken": "<jwt>",
     "refreshToken": "<opaque-token>",
     "tokenType": "Bearer",
     "expiresIn": 900,
     "user": { "id": "…", "name": "…", "email": "…", "role": "USER" }
   }
   ```

2. Send the access token on every protected request:

   ```http
   Authorization: Bearer <accessToken>
   ```

3. When the access token expires, call `POST /auth/refresh` with the refresh token. The old refresh token is revoked and a new pair is returned. Reusing a revoked token returns `401`.

4. `POST /auth/logout` revokes one refresh token. `POST /auth/logout-all` revokes every refresh token for the current user.

Access tokens are stateless and expire quickly; revocation applies to refresh tokens. Clients should discard access tokens on logout and rely on the short TTL.

### Error format

Every failure uses the same envelope:

```json
{
  "timestamp": "2026-11-18T12:34:56.789Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/auth/register",
  "fieldErrors": {
    "email": "email must be a valid address",
    "password": "password must contain at least one letter and one number"
  }
}
```

Common codes: `VALIDATION_FAILED`, `BAD_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `METHOD_NOT_ALLOWED`, `PAYLOAD_TOO_LARGE`, `INTERNAL_ERROR`.

### Pagination

List endpoints return a stable envelope:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false,
  "hasPrevious": false
}
```

`page` is zero-based. `size` must be between 1 and 100.

### Enumerations

| Enum | Values |
| --- | --- |
| `TaskStatus` | `BACKLOG`, `TODO`, `IN_PROGRESS`, `IN_REVIEW`, `DONE` |
| `TaskPriority` | `LOW`, `MEDIUM`, `HIGH`, `URGENT` |
| `ProjectStatus` | `ACTIVE`, `ARCHIVED` |
| `TeamRole` | `OWNER`, `ADMIN`, `MEMBER` |
| `InvitationStatus` | `PENDING`, `ACCEPTED`, `REVOKED`, `EXPIRED` |
| `NotificationType` | `TASK_ASSIGNED`, `TASK_UPDATED`, `TASK_COMPLETED`, `TASK_COMMENTED`, `TEAM_INVITATION`, `TEAM_MEMBER_JOINED` |

### Endpoints

All routes except `auth/register`, `auth/login`, `auth/refresh`, `auth/logout`, health and documentation require a bearer token.

#### Authentication

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/auth/register` | Create an account and issue tokens (`201`) |
| `POST` | `/auth/login` | Log in |
| `POST` | `/auth/refresh` | Rotate a refresh token |
| `POST` | `/auth/logout` | Revoke one refresh token (`204`) |
| `POST` | `/auth/logout-all` | Revoke all refresh tokens for the caller (`204`) |

#### Users

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/users/me` | Current profile |
| `PATCH` | `/users/me` | Update name, bio, avatar URL |
| `PUT` | `/users/me/password` | Change password (`204`) and revoke other sessions |
| `GET` | `/users/search?q=` | Search people who share a team with you |

#### Teams and members

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/teams` | Create a team (caller becomes `OWNER`) |
| `GET` | `/teams` | List your teams (`q`, `page`, `size`) |
| `GET` | `/teams/{teamId}` | Get a team |
| `PATCH` | `/teams/{teamId}` | Update name/description (owner/admin) |
| `DELETE` | `/teams/{teamId}` | Delete a team and everything inside it (owner) |
| `POST` | `/teams/{teamId}/invite-code/rotate` | Rotate the invite code (owner/admin) |
| `POST` | `/teams/join` | Join with `{"inviteCode": "..."}` |
| `POST` | `/teams/{teamId}/invitations` | Invite an email address (owner/admin) |
| `GET` | `/teams/{teamId}/invitations` | List team invitations (owner/admin) |
| `GET` | `/teams/invitations/mine` | Invitations addressed to you |
| `POST` | `/teams/invitations/{token}/accept` | Accept an invitation |
| `DELETE` | `/teams/{teamId}/invitations/{invitationId}` | Revoke a pending invitation (owner/admin) |
| `GET` | `/teams/{teamId}/members` | List members |
| `PATCH` | `/teams/{teamId}/members/{userId}` | Change a member's role (owner only) |
| `DELETE` | `/teams/{teamId}/members/{userId}` | Remove a member, or leave the team |

The invite code is only returned to owners and admins.

#### Projects

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/teams/{teamId}/projects` | Create a project |
| `GET` | `/teams/{teamId}/projects` | List projects (`status`, `page`, `size`) |
| `GET` | `/projects/{projectId}` | Get a project |
| `PATCH` | `/projects/{projectId}` | Update name/description/status (creator, owner or admin) |
| `DELETE` | `/projects/{projectId}` | Delete a project and its tasks (creator, owner or admin) |

#### Tasks

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/projects/{projectId}/tasks` | Create a task |
| `GET` | `/projects/{projectId}/tasks` | List/filter/sort/search tasks |
| `GET` | `/tasks/assigned-to-me` | Tasks assigned to you across all teams |
| `GET` | `/tasks/{taskId}` | Get a task |
| `PATCH` | `/tasks/{taskId}` | Update fields (`title`, `description`, `status`, `priority`, `dueDate`, `labels`) |
| `PATCH` | `/tasks/{taskId}/assignee` | Assign a team member or unassign with `null` |
| `POST` | `/tasks/{taskId}/complete` | Mark as `DONE` |
| `DELETE` | `/tasks/{taskId}` | Delete (creator, project creator, owner or admin) |

#### Comments

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/tasks/{taskId}/comments` | List comments in chronological order |
| `POST` | `/tasks/{taskId}/comments` | Add a comment (`201`) |
| `PATCH` | `/comments/{commentId}` | Edit your own comment |
| `DELETE` | `/comments/{commentId}` | Delete your comment, or moderate as owner/admin |

#### Attachments

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/tasks/{taskId}/attachments` | List attachments |
| `POST` | `/tasks/{taskId}/attachments` | Upload `multipart/form-data` with field `file` (`201`) |
| `GET` | `/attachments/{attachmentId}/download` | Download with `Content-Disposition` |
| `DELETE` | `/attachments/{attachmentId}` | Delete your upload, or moderate as owner/admin |

Allowed content types include PNG, JPEG, GIF, WebP, PDF, plain text, CSV, ZIP, JSON, Word, Excel and OpenDocument formats. Executable/script extensions (`.exe`, `.bat`, `.ps1`, `.js`, `.jar`, etc.) are rejected. The default limit is 10 MB.

#### Notifications

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/notifications` | List notifications (`unreadOnly`, `page`, `size`) |
| `GET` | `/notifications/unread-count` | `{"unreadCount": n}` |
| `POST` | `/notifications/{id}/read` | Mark one notification read |
| `POST` | `/notifications/read-all` | Mark all read, returns `{"updated": n}` |
| `GET` | `/notifications/stream` | SSE stream of live notifications |

### Task filtering, sorting and search

`GET /projects/{projectId}/tasks` and `GET /tasks/assigned-to-me` accept:

| Parameter | Example | Notes |
| --- | --- | --- |
| `status` | `TODO,IN_PROGRESS` | Repeatable or comma-separated |
| `priority` | `HIGH,URGENT` | Repeatable or comma-separated |
| `assigneeId` | UUID | Project task list only |
| `q` | `weekly report` | Case-insensitive title/description search |
| `dueBefore` | `2026-12-01` | Inclusive |
| `dueAfter` | `2026-11-01` | Inclusive |
| `overdue` | `true` | Due date before today and status is not `DONE` |
| `sort` | `dueDate,asc` | Whitelisted field + `asc`/`desc` |
| `page`, `size` | `0`, `20` | Zero-based page, 1–100 items |

Sortable fields: `createdAt`, `updatedAt`, `dueDate`, `priority`, `title`, `status`. Invalid fields or directions return `400`. The default sort is `createdAt,desc` for project lists and `dueDate,asc` for "assigned to me".

### Real-time notifications (SSE)

```http
GET /api/v1/notifications/stream
Authorization: Bearer <accessToken>
Accept: text/event-stream
```

Browsers cannot add headers to `EventSource`, so the endpoint also accepts `?access_token=<jwt>`.

Events:

- `connected` — sent immediately after subscribing.
- `notification` — JSON notification payload (same shape as the REST resource).
- Comment frames (`:heartbeat`) every 25 seconds keep proxies from closing idle connections.

Notifications are persisted before being emitted, and emission happens only **after the database transaction commits**, so clients never receive rolled-back events. Connections are held in memory: for horizontal scaling, replace the registry with Redis pub/sub or another shared broker.

---

## curl walkthrough

```bash
# 1. Register
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ada Lovelace","email":"ada@example.com","password":"Password123"}'

# 2. Log in (use the accessToken from the response)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","password":"Password123"}' | jq -r .accessToken)

# 3. Create a team
TEAM=$(curl -s -X POST http://localhost:8080/api/v1/teams \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Platform","description":"Platform engineering"}')
TEAM_ID=$(echo "$TEAM" | jq -r .id)
INVITE_CODE=$(echo "$TEAM" | jq -r .inviteCode)

# 4. Create a project
PROJECT_ID=$(curl -s -X POST http://localhost:8080/api/v1/teams/$TEAM_ID/projects \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Reporting","description":"Weekly metrics"}' | jq -r .id)

# 5. Create a task
TASK_ID=$(curl -s -X POST http://localhost:8080/api/v1/projects/$PROJECT_ID/tasks \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Ship the weekly report","description":"Aggregate completion metrics",
       "status":"TODO","priority":"HIGH","dueDate":"2026-12-01","labels":["backend","api"]}' \
  | jq -r .id)

# 6. Filter, sort and search
curl -s "http://localhost:8080/api/v1/projects/$PROJECT_ID/tasks?status=TODO,HIGH&priority=HIGH&q=report&sort=dueDate,asc" \
  -H "Authorization: Bearer $TOKEN"

# 7. Comment
curl -s -X POST http://localhost:8080/api/v1/tasks/$TASK_ID/comments \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"body":"First draft is ready for review."}'

# 8. Upload an attachment
curl -s -X POST http://localhost:8080/api/v1/tasks/$TASK_ID/attachments \
  -H "Authorization: Bearer $TOKEN" -F 'file=@report.txt;type=text/plain'

# 9. Complete the task
curl -s -X POST http://localhost:8080/api/v1/tasks/$TASK_ID/complete \
  -H "Authorization: Bearer $TOKEN"

# 10. Stream notifications (keep this running in another terminal)
curl -N "http://localhost:8080/api/v1/notifications/stream?access_token=$TOKEN"
```

Join an existing team with its invite code:

```bash
curl -s -X POST http://localhost:8080/api/v1/teams/join \
  -H "Authorization: Bearer $OTHER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"inviteCode\":\"$INVITE_CODE\"}"
```

---

## Tests

Run the full suite:

```bash
./mvnw verify
```

The integration tests start the real Spring context against an in-memory H2 database (`MODE=PostgreSQL`) and exercise endpoints through MockMvc, including authentication, role enforcement, validation failures and notification fan-out.

| Test class | Coverage |
| --- | --- |
| `AuthIntegrationTest` | Registration, login, profile updates, refresh rotation/reuse, logout, global logout, password change, unauthenticated access |
| `TeamIntegrationTest` | Teams, invitations, join codes, roles, removals, invite-code visibility, cascade deletion |
| `TaskIntegrationTest` | CRUD, assignment, completion/reopen, filtering, search, sorting whitelist, membership guards, archived projects |
| `CommentAttachmentIntegrationTest` | Comment lifecycle and moderation, upload/download/delete permissions, file validation and name sanitization |
| `NotificationIntegrationTest` | Assignment/comment/completion/invitation/join notifications, unread counts, read state, recipient scoping, paging validation |

The suite is deliberately **not** wrapped in a single test-wide transaction: each HTTP request commits like it does in production, which keeps bulk updates and cascade deletes visible to subsequent requests. Tests stay independent by creating unique users and their own teams.

GitHub Actions (`.github/workflows/ci.yml`) runs `./mvnw -B verify` on every push and pull request.

---

## Design decisions

- **Team → Project → Task hierarchy.** This gives a clear ownership boundary for permissions, notifications and cascading deletes.
- **JWT access tokens + opaque refresh tokens.** Access tokens stay stateless and short-lived; refresh tokens are random, hashed at rest and rotated on use, which limits the damage of a leaked token.
- **PostgreSQL as the production database, H2 for zero-setup development.** H2 runs in PostgreSQL compatibility mode for tests, so SQL behaves consistently.
- **Flyway owns the PostgreSQL schema.** Hibernate `validate` prevents accidental drift; the default H2 profile uses `ddl-auto=update` for developer convenience.
- **Server-side authorization on every resource.** Team membership is checked centrally by `TeamMembershipGuard`; project/task/comment/attachment endpoints never rely on the client to scope IDs.
- **Consistent API contracts.** Every list uses the same pagination envelope, every error uses the same JSON shape, and sort fields are explicitly whitelisted.
- **Notifications are persisted first.** SSE is a delivery optimization, not the source of truth, so disconnected clients can catch up through the REST list endpoints.
- **Files live outside the database.** Attachments are stored on disk with random names, while metadata lives in PostgreSQL; deleting a task/team cleans up its files after commit.
- **Pragmatic test strategy.** HTTP-level tests cover real security filters, JSON serialization, validation and transactions without mocking the application internals.

---

## Limitations and next steps

- SSE connections are tracked in memory, limiting live delivery to a single application instance. Use Redis pub/sub, Kafka or another broker for horizontal scaling.
- Attachments use local filesystem storage; object storage (S3/GCS) would be the next step for multi-node deployments.
- Access tokens cannot be revoked before expiry (15 minutes). A token-version/denylist mechanism would shorten that window further.
- There is no email delivery yet: invitation tokens are returned to managers and surfaced in the invitee's in-app notifications.
- Search is a simple case-insensitive `LIKE`; PostgreSQL full-text search or a dedicated index would scale better for large workspaces.
- Rate limiting, audit logging and refresh-token device management are not implemented.
- No frontend is included; the API is documented through Swagger UI and this README.

---

## License

Released under the [MIT License](LICENSE). Contributions are welcome — see
[CONTRIBUTING.md](CONTRIBUTING.md) for the workflow and [SECURITY.md](SECURITY.md)
for private vulnerability reporting.
