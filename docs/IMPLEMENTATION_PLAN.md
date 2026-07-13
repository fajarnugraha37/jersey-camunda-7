# IMPLEMENTATION_PLAN

## Ordered tasks

1. Start the first workflow orchestration slice after lifecycle rules are stable.
2. Add storage-backed evidence intake with presigned upload and finalize flow.
3. Introduce outbox and messaging foundation after case transitions are stable.
4. Expand assignment and authorization to include assigned units, case classification, and conflict-of-interest checks.
5. Add decision, sanction, and appeal aggregates so later-status prerequisites stop being policy-only placeholders.

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
- `POST /api/v1/cases` creates a case from an existing report and returns `201` for an authorized triage officer.
- `GET /api/v1/cases/{caseId}` returns the persisted case for an authorized actor.
- `GET /api/v1/cases` returns cursor-paged case results and filters investigator visibility to directly assigned cases.
- `POST /api/v1/cases/{caseId}/assignments` updates current assignment with optimistic locking and writes assignment audit.
- `POST /api/v1/cases/{caseId}/transitions` enforces role-aware state transitions, optimistic locking, and append-only status history.
- `GET /api/v1/cases/{caseId}/audit-events` returns audit events for authorized auditor or supervisor roles.
- Liquibase migration creates the case lifecycle schema and concurrency-safe case number function on an empty database.
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

The acceptance criteria above are implemented and locally verified through Maven commands and Testcontainers. OpenAPI-generated models are part of the build, report and case endpoints are protected by JWT bearer auth, optimistic locking is enforced for case mutations, and authorization is enforced centrally in the application layer with direct-assignment filtering for investigator reads.
