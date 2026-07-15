# IMPLEMENTATION_PLAN

## Ordered tasks

1. Extend workflow, messaging, and storage flows so later-state prerequisites stop being policy-only placeholders.
2. Add deeper enforcement-monitoring and obligation-tracking behavior for the post-decision path.
3. Extend hardening with failure-injection coverage, performance review, and operational metrics.

## Acceptance criteria for phase 0-8 slice

- `make compile` succeeds.
- `make unit-test` succeeds.
- `make integration-test` succeeds with PostgreSQL + Keycloak + MinIO Testcontainers.
- `POST /api/v1/reports` persists a report and returns `201` for an authorized intake officer.
- `GET /api/v1/reports/{reportId}` returns the persisted report for an authorized actor.
- `POST /api/v1/reports/{reportId}/triage` transitions the report to `TRIAGED` with optimistic locking.
- `POST /api/v1/cases` only creates a case from a triaged report and starts a correlated Camunda workflow instance.
- `GET /api/v1/cases` returns cursor-paged case results and filters investigator visibility to directly assigned cases.
- `POST /api/v1/cases/{caseId}/assignments` updates current assignment with optimistic locking and writes assignment audit.
- `POST /api/v1/cases/{caseId}/transitions` enforces role-aware state transitions, optimistic locking, and append-only status history.
- `GET /api/v1/cases/{caseId}/audit-events` returns audit events for authorized roles with cursor pagination, quick search, targeted search, and whitelisted sorting.
- `GET /api/v1/tasks` returns cursor-paged workflow tasks with quick search, targeted search, and whitelisted sort semantics.
- `POST /api/v1/tasks/{taskId}/claim` enforces role-aware task claim semantics and returns `409` for conflicting claims.
- `POST /api/v1/tasks/{taskId}/complete` advances the workflow path without double-applying domain side effects when duplicate completion requests arrive.
- `GET /api/v1/workflow-reconciliation` and `POST /api/v1/workflow-reconciliation/{caseId}/actions` operate correctly for supervisor-visible mismatch handling.
- `POST /api/v1/cases/{caseId}/evidence/upload-sessions` returns a presigned MinIO upload URL for an authorized actor.
- `POST /api/v1/evidence/{evidenceId}/versions/finalize` verifies object existence, size, media type, and SHA-256 before activating the evidence version.
- `GET /api/v1/evidence/{evidenceId}` returns active evidence metadata and latest version details for an authorized actor.
- `POST /api/v1/evidence/{evidenceId}/download-sessions` enforces authorization, returns a presigned download URL for authorized actors, and audits denied access.
- Domain writes that emit messaging side effects persist `outbox_event` rows in the same transaction as the business change.
- Kafka outage does not roll back successful business commits for case/evidence writes; pending outbox rows remain retryable.
- Duplicate event delivery results in at most one notification side effect because `inbox_event` enforces consumer idempotency.
- Case and workflow authorization enforce jurisdiction, direct assignment, assigned unit, classification clearance, and conflict-of-interest checks consistently.
- `GET /api/v1/cases` accepts classification-aware list filtering without breaking cursor scope validation.
- Missing token returns `401`.
- Wrong role returns `403`.
- Wrong jurisdiction returns `403`.
- Invalid request bodies return the standard error envelope.
- Liquibase migration runs on an empty database.

## Current status

All phase 0-8 acceptance criteria remain implemented, and the post-change verification loop is complete in this run. The phase 8 regression loop uncovered and fixed two real defects before the final green run: a malformed MyBatis dynamic SQL branch in case listing and stale integration-test unit identifiers that no longer matched the new assigned-unit authorization model.
