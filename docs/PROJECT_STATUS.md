# PROJECT_STATUS

## Current phase

Phase 5 evidence and MinIO slice implemented in code. Full post-change verification is pending because Maven dependency resolution and test execution were blocked by the current environment's network/approval limit during this run.

## Completed capabilities

- Repository foundation, module split, Makefile, Dockerfile, Docker Compose, and health endpoint are in place.
- PostgreSQL-backed report create/get plus Keycloak JWT auth, centralized authorization, and jurisdiction enforcement are implemented.
- Report triage is now implemented and case creation requires a triaged source report.
- Case lifecycle aggregate, assignment, status history, optimistic locking, audit append, and cursor-based list APIs are implemented.
- Embedded Camunda runtime, BPMN deployment, task list/claim/complete flow, escalation timer, workflow correlation, and reconciliation tooling are implemented.
- Storage-backed evidence metadata, upload session creation, MinIO presigned upload URL generation, server-side finalize verification, immutable evidence versioning, checksum enforcement, and download-session authorization/audit are implemented.
- OpenAPI contract now includes report triage and evidence endpoints, and generated API models remain the source for request/response DTOs.
- Local runtime wiring now includes MinIO configuration and bucket bootstrap helpers.

## Remaining gaps beyond phase 5

- Messaging, transactional outbox, inbox idempotency, Kafka retry, and dead-letter handling are not implemented yet.
- Recommendation, review, decision publication, sanction, and appeal aggregates are not implemented yet.
- Broader hardening work such as load/performance review, failure injection coverage, and metrics/dashboard work remains incomplete.

## Verification status

- Previous workflow-oriented baseline had passed compile and integration verification before this evidence increment.
- The current evidence increment has not been recompiled or retested in this run because Maven could not resolve dependencies without external network access and the required escalation request was rejected by the environment usage limit.
- Static review was completed across application, persistence, API contract, bootstrap wiring, Docker Compose, and test coverage additions.

## Infrastructure status

- Compose definition now includes PostgreSQL, MinIO, Keycloak, MinIO bucket bootstrap, and the application container.
- `.env.example` now includes MinIO endpoint, credential, bucket, and presigned URL TTL configuration.

## Next recommended task

Re-run `make format`, `make compile`, `make unit-test`, `make integration-test`, and `make verify` in an environment that can resolve Maven dependencies, then fix any compile/runtime regressions surfaced by generated-model or MinIO integration mismatches before moving to Kafka reliability work.
