# IMPLEMENTATION_PLAN

## Ordered tasks

1. Verify Docker Compose startup and smoke-test flow for the current foundation slice.
2. Wire OpenAPI Generator into compile-time generated models or interfaces.
3. Introduce Keycloak-based authentication and JWT validation.
4. Add centralized authorization rules with jurisdiction-aware access checks.
5. Extend the domain into case lifecycle and optimistic locking.

## Dependencies

- Bootstrap depends on API, application, and persistence modules.
- API depends on application and domain modules.
- Application depends on domain abstractions only.
- Persistence depends on application ports and domain objects.
- Integration tests depend on bootstrap and Docker availability through Testcontainers.

## Acceptance criteria for current increment

- `make compile` succeeds.
- `make unit-test` succeeds.
- `make integration-test` succeeds with PostgreSQL Testcontainers.
- `GET /health` returns application and database health.
- `POST /api/v1/reports` persists a report and returns `201`.
- `GET /api/v1/reports/{reportId}` returns the persisted report.
- Invalid request bodies return the standard error envelope.
- Liquibase migration runs on an empty database.

## Current status

The acceptance criteria above are implemented and locally verified through Maven commands. Docker Compose runtime verification is still pending.
