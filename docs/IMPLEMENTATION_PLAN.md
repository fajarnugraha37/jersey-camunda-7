# IMPLEMENTATION_PLAN

## Ordered tasks

1. Replace the starter single-module layout with a minimal Maven multi-module foundation.
2. Implement bootstrap wiring, health endpoint, and database configuration.
3. Add Liquibase changelog and PostgreSQL-backed report persistence with MyBatis.
4. Expose create/get report API with validation and consistent error envelope.
5. Add unit and integration tests, then verify with Maven and Docker-backed checks.

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
