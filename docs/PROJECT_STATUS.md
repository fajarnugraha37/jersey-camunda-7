# PROJECT_STATUS

## Current phase

Phase 8 is fully implemented and reverified in this run, including assigned-unit authorization, case classification clearance, conflict-of-interest checks, observability/runtime hardening, notification command/result flow, and the supporting OpenAPI, Keycloak claim, persistence, and integration-test updates.

## Completed capabilities

- Repository foundation, module split, Makefile, Dockerfile, Docker Compose, and health endpoint are in place.
- PostgreSQL-backed report create/get plus Keycloak JWT auth, centralized authorization, and jurisdiction enforcement are implemented.
- Report triage is now implemented and case creation requires a triaged source report.
- Case lifecycle aggregate, assignment, status history, optimistic locking, audit append, and cursor-based list APIs are implemented.
- Embedded Camunda runtime, BPMN deployment, task list/claim/complete flow, escalation timer, workflow correlation, and reconciliation tooling are implemented.
- Storage-backed evidence metadata, upload session creation, MinIO presigned upload URL generation, server-side finalize verification, immutable evidence versioning, checksum enforcement, and download-session authorization/audit are implemented.
- Transactional outbox tables, MyBatis-backed outbox/inbox/notification persistence, Kafka publisher, notification consumer, retry topics, and dead-letter routing are implemented for the current case/evidence slice.
- Recommendation, review, decision, sanction, and appeal aggregates are implemented for the current workflow path.
- Case and workflow read/write authorization now considers role, jurisdiction, direct assignment, assigned unit, case classification, and conflict-of-interest claims from Keycloak.
- `GET /api/v1/cases` now supports classification-aware filtering while still using cursor pagination, quick search, targeted search, and whitelisted sort semantics.
- `GET /health` now returns dependency-level readiness for database, Kafka, Redis, Mailpit, and workflow engine.
- Notification projection now emits `notification.command.v1`, persists notification delivery status transitions, and publishes `notification.result.v1` plus `audit.integration.v1` for verified runtime side effects.
- OpenAPI contract now includes report triage and evidence endpoints, and generated API models remain the source for request/response DTOs.
- Local runtime wiring now includes Kafka, Redis, Mailpit, and MinIO configuration plus bucket/bootstrap helpers.

## Remaining gaps beyond phase 8

- Existing workflow start semantics still rely on compensation rather than an outbox-backed workflow-start intent.
- Later-state business prerequisites for recommendation/review/decision/sanction/appeal are still lighter than the master target because some supporting aggregates and enforcement-monitoring detail are not implemented yet.
- Broader hardening work such as load/performance review, failure injection coverage, and metrics/dashboard work remains incomplete.

## Verification status

- `mvn -q spotless:apply` passed.
- `mvn -q test` passed.
- `mvn -q -pl sentinel-integration-tests -am verify` passed.
- `mvn -q verify` passed for the full reactor.

## Infrastructure status

- Compose definition now includes PostgreSQL, Kafka, Redis, Mailpit, MinIO, Keycloak, MinIO bucket bootstrap, and the application container.
- `.env.example` now includes Kafka bootstrap, Redis host/port, Mailpit SMTP host/port, notification sender/recipient, outbox cadence, consumer retry, MinIO endpoint, credential, bucket, and presigned URL TTL configuration.

## Next recommended task

Move to the next aggregate-deepening increment: strengthen later-state business prerequisites, enforcement monitoring, and remaining failure-injection hardening on top of the now-green phase 8 baseline.
