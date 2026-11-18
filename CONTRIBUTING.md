# Contributing to TaskFlow

Thanks for taking the time to contribute. This document covers the local
workflow, coding expectations and the checks a pull request must pass.

## Prerequisites

- JDK 21 or newer (the build targets Java 21)
- No Maven installation is required — the repository ships the Maven Wrapper
- Docker is optional and only needed for the PostgreSQL profile

## Local setup

```bash
git clone https://github.com/Rachit3021/TaskMaster.git
cd TaskMaster
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

The default profile uses a file-based H2 database, so nothing else is needed.
See the [README](README.md#getting-started) for the PostgreSQL and Docker
Compose options, demo credentials and environment variables.

## Workflow

1. Fork the repository or create a branch from `main`
   (`feat/short-description`, `fix/short-description`, `docs/short-description`).
2. Make a focused change. Avoid unrelated refactors in the same pull request.
3. Add or update tests when behaviour changes.
4. Run the full verification build:

   ```bash
   ./mvnw -B verify              # Windows: .\mvnw.cmd -B verify
   ```

5. Commit with an imperative, scoped message, for example
   `feat(tasks): support filtering by multiple assignees`.
6. Open a pull request against `main` and describe the motivation, the approach
   and any trade-offs.

## Coding guidelines

- Keep the existing package layout: one feature package (`auth`, `team`,
  `project`, `task`, ...) with controller, service, repository, entity and DTOs.
- Put business rules in services, not controllers; controllers stay thin.
- Validate request payloads with Bean Validation annotations and throw the
  project's typed exceptions (`NotFoundException`, `ConflictException`, ...) so
  the `GlobalExceptionHandler` produces a consistent error shape.
- Do not expose entities directly from controllers — map to response DTOs.
- Keep new endpoints RESTful and document them with springdoc annotations where
  they are not self-explanatory.
- Never commit secrets. Use environment variables; `.env.example` documents the
  supported names.
- Format with the conventions already in the codebase (4 spaces, no wildcard
  imports, constructor injection).

## Tests

Integration tests live in `src/test/java/com/taskflow` and run against an
in-memory H2 database with the `test` profile. They exercise the full HTTP
stack, so new endpoints should normally get a MockMvc test covering the happy
path plus authorization and validation failures.

## Reporting bugs and security issues

Regular bugs and feature requests are welcome as GitHub issues. Please do not
open a public issue for a security vulnerability — follow
[SECURITY.md](SECURITY.md) instead.

By contributing you agree that your contributions are licensed under the
[MIT License](LICENSE).
