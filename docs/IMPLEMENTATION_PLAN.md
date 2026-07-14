# IMPLEMENTATION_PLAN

## Ordered tasks

1. Re-verify the current phase 0-5 slice with compile, unit, integration, and verify commands once Maven dependency resolution is available again.
2. Introduce outbox and messaging foundation after the evidence slice is proven stable.
3. Expand assignment and authorization to include assigned units, case classification, and conflict-of-interest checks.
4. Add recommendation, review, decision, sanction, and appeal aggregates so later-state prerequisites stop being policy-only placeholders.
5. Extend hardening with failure-injection coverage, performance review, and operational metrics.

## Acceptance criteria for phase 0-5 slice

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
- Missing token returns `401`.
- Wrong role returns `403`.
- Wrong jurisdiction returns `403`.
- Invalid request bodies return the standard error envelope.
- Liquibase migration runs on an empty database.

## Current status

Code for the acceptance criteria above has been updated in this run, but the post-change verification loop is still pending because Maven dependency resolution was blocked by the environment's network/approval limit. Static review of the changed layers is complete; compile and runtime confirmation still need to be rerun externally or in a less restricted session.
