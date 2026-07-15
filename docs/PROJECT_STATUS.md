# PROJECT_STATUS

## Current phase

Phase 6 is fully implemented and reverified in this run, including the evidence regression fix for the MinIO-backed evidence + Kafka reliability slice.

## Completed capabilities

- Repository foundation, module split, Makefile, Dockerfile, Docker Compose, and health endpoint are in place.
- PostgreSQL-backed report create/get plus Keycloak JWT auth, centralized authorization, and jurisdiction enforcement are implemented.
- Report triage is now implemented and case creation requires a triaged source report.
- Case lifecycle aggregate, assignment, status history, optimistic locking, audit append, and cursor-based list APIs are implemented.
- Embedded Camunda runtime, BPMN deployment, task list/claim/complete flow, escalation timer, workflow correlation, and reconciliation tooling are implemented.
- Storage-backed evidence metadata, upload session creation, MinIO presigned upload URL generation, server-side finalize verification, immutable evidence versioning, checksum enforcement, and download-session authorization/audit are implemented.
- Transactional outbox tables, MyBatis-backed outbox/inbox/notification persistence, Kafka publisher, notification consumer, retry topics, and dead-letter routing are implemented for the current case/evidence slice.
- OpenAPI contract now includes report triage and evidence endpoints, and generated API models remain the source for request/response DTOs.
- Local runtime wiring now includes Kafka + MinIO configuration and bucket/bootstrap helpers.

## Remaining gaps beyond phase 6

- Recommendation, review, decision publication, sanction, and appeal aggregates are not implemented yet.
- Existing workflow start semantics still rely on compensation rather than an outbox-backed workflow-start intent.
- Broader hardening work such as load/performance review, failure injection coverage, and metrics/dashboard work remains incomplete.

## Verification status

- `mvn -q -DskipTests compile` passed.
- `mvn -q test` passed.
- `mvn -q -pl sentinel-integration-tests -am "-Dit.test=EvidenceApiIT" verify` passed after the routing and enum-alignment fix.
- `mvn -q verify` passed for the full reactor after the phase 6 fixes.
- The phase 6 Makefile surface no longer leaves `e2e-test`, `docker-push-local`, or `rollback` as placeholders.

## Infrastructure status

- Compose definition now includes PostgreSQL, Kafka, MinIO, Keycloak, MinIO bucket bootstrap, and the application container.
- `.env.example` now includes Kafka bootstrap, outbox cadence, consumer retry, MinIO endpoint, credential, bucket, and presigned URL TTL configuration.

## Next recommended task

Move to phase 7 aggregates: recommendation, review, decision publication, sanction, and appeal.
