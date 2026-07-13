# IMPLEMENTATION_PLAN

## Ordered tasks

1. Extend the domain into case lifecycle and optimistic locking.
2. Add case status history and audit foundation for lifecycle changes.
3. Start the first workflow orchestration slice after lifecycle rules are stable.
4. Add storage-backed evidence intake with presigned upload and finalize flow.
5. Introduce outbox and messaging foundation after case transitions are stable.

## Dependencies

- Bootstrap depends on API, application, and persistence modules.
- API depends on application and domain modules.
- Application depends on domain abstractions only.
- Persistence depends on application ports and domain objects.
- Integration tests depend on bootstrap and Docker availability through Testcontainers.

## Acceptance criteria for current increment

- `make compile` succeeds.
- `make unit-test` succeeds.
- `make integration-test` succeeds with PostgreSQL + Keycloak Testcontainers.
- `GET /health` returns application and database health.
- `POST /api/v1/reports` persists a report and returns `201` for an authorized intake officer.
- `GET /api/v1/reports/{reportId}` returns the persisted report for an authorized reader.
- Missing token returns `401`.
- Wrong role returns `403`.
- Wrong jurisdiction returns `403`.
- Invalid request bodies return the standard error envelope.
- Liquibase migration runs on an empty database.
- Keycloak local realm bootstrap provides reusable dummy users for local development.

## Current status

The acceptance criteria above are implemented and locally verified through Maven commands and Testcontainers. OpenAPI-generated models are part of the build, report endpoints are protected by JWT bearer auth, and authorization is enforced centrally in the application layer.
