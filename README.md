# Sentinel Enforcement Platform

Sentinel Enforcement Platform is an enterprise training project for regulatory enforcement and complex case management. The current repo state covers phases 0-8 according to the main roadmap: foundation, intake, authentication/authorization, case lifecycle, embedded Camunda workflow, MinIO-based evidence intake, Kafka reliability, and aggregate recommendation/review/decision/sanction/appeal with stricter case-level authorization. The repo also still carries workflow reconciliation tooling that was previously added as an additional hardening slice.

## Architecture Overview

- Application form: modular monolith based on Maven multi-module.
- Available modules:
  - `sentinel-domain`
  - `sentinel-application`
  - `sentinel-api`
  - `sentinel-persistence`
  - `sentinel-messaging`
  - `sentinel-observability`
  - `sentinel-storage`
  - `sentinel-workflow`
  - `sentinel-security`
  - `sentinel-bootstrap`
  - `sentinel-integration-tests`
- Current boundaries:
  - `sentinel-api` handles Jersey resource, bean validation, JSON, and error envelope.
  - `sentinel-application` handles use case, authorization orchestration, command/query contract, and workflow/storage port.
  - `sentinel-domain` holds aggregate, transition rule, and invariant.
  - `sentinel-persistence` holds MyBatis adapter and Liquibase changelog.
  - `sentinel-messaging` holds Kafka publisher/consumer runtime, outbox polling, retry, and dead-letter routing.
  - `sentinel-observability` holds correlation context, request metrics, and dependency health composition.
  - `sentinel-storage` holds MinIO adapter for evidence storage.
  - `sentinel-workflow` holds embedded Camunda runtime, BPMN deployment, task query adapter, and workflow correlation.
  - `sentinel-security` holds JWT verification Keycloak and authorization based on role, jurisdiction, assigned unit, classification clearance, direct assignment, and conflict-of-interest.
  - `sentinel-bootstrap` assembles dependency, starts server, readiness, and migration entrypoint.

## Technology Stack

- Java 21
- Jersey + Grizzly
- MyBatis
- HikariCP
- Liquibase
- MapStruct
- Jackson
- Hibernate Validator
- PostgreSQL
- Apache Kafka
- Embedded Camunda 7
- MinIO
- Keycloak
- Testcontainers
- Docker Compose
- Maven

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker Desktop
- GNU Make

## Local Setup

1. Copy `.env.example` to `.env` if you want to override the default local config.
2. Run `make bootstrap`.
3. Run `make up` to spin up PostgreSQL, Kafka, Redis, Mailpit, MinIO, Keycloak, and bootstrap the MinIO bucket.
4. Run `make migrate` to execute application migrations + official Camunda schemas, and then start the application.
5. Run `make seed` if you want to replay the idempotent bootstrap helper.
6. Run `make smoke-test`.

## Commands

- `make help`
- `make format`
- `make compile`
- `make unit-test`
- `make integration-test`
- `make workflow-test`
- `make messaging-test`
- `make e2e-test`
- `make karate-smoke`
- `make karate-regression`
- `make karate-full`
- `make verify`
- `make openapi-generate`
- `make openapi-validate`
- `make up`
- `make down`
- `make logs`
- `make app-logs`
- `make docker-build`
- `make docker-push-local`
- `make migrate`
- `make rollback ROLLBACK_COUNT=1`
- `make seed`
- `make minio-init`
- `make smoke-test`

## Current API

- `GET /health`
- `POST /api/v1/reports`
- `GET /api/v1/reports/{reportId}`
- `POST /api/v1/reports/{reportId}/triage`
- `POST /api/v1/cases`
- `GET /api/v1/cases`
- `GET /api/v1/cases/{caseId}`
- `POST /api/v1/cases/{caseId}/assignments`
- `POST /api/v1/cases/{caseId}/transitions`
- `GET /api/v1/cases/{caseId}/audit-events`
- `POST /api/v1/cases/{caseId}/evidence/upload-sessions`
- `POST /api/v1/evidence/{evidenceId}/versions/finalize`
- `GET /api/v1/evidence/{evidenceId}`
- `POST /api/v1/evidence/{evidenceId}/download-sessions`
- `GET /api/v1/tasks`
- `POST /api/v1/tasks/{taskId}/claim`
- `POST /api/v1/tasks/{taskId}/complete`
- `GET /api/v1/workflow-reconciliation`
- `POST /api/v1/workflow-reconciliation/{caseId}/actions`

## Messaging Slice

- Topics active in this phase:
  - `case.lifecycle.v1`
  - `case.assignment.v1`
  - `evidence.lifecycle.v1`
  - `decision.lifecycle.v1`
  - `sanction.lifecycle.v1`
  - `appeal.lifecycle.v1`
  - `notification.command.v1`
  - `notification.result.v1`
  - `audit.integration.v1`
- Every relevant domain change writes an event to `outbox_event` within the same transaction as the business change.
- The background publisher will lease pending rows using `FOR UPDATE SKIP LOCKED`, publish them to Kafka, and then mark the rows as `PUBLISHED`.
- The notification projection and notification command consumer process events through `inbox_event` so that duplicate delivery does not result in duplicate side effects on the `notification` table.
- Relevant domain audit events are also published to `audit.integration.v1`, ensuring the append-only audit database and integration stream remain synchronized.
- Retries are sent to the `.retry` topic, and repeated failures are moved to the `.dlq` topic.

Contract specifications are located in [docs/api/openapi.yaml](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/api/openapi.yaml). Generated request/response models for the API layer are built from this spec during the `generate-sources` phase.

The mandatory pattern for list endpoints is available in [docs/api/list-query-pattern.md](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/api/list-query-pattern.md). All new list APIs must adhere to the combination of `cursor`, `limit`, `q`, `searchField/searchValue`, and enum-based `sortBy/sortDirection` using safe dynamic SQL.

## Authorization Model

- The local JWT actor now carries `jurisdictions`, `assigned_units`, `case_classifications`, and `conflicted_actor_ids` claims.
- Case resource access is not determined by role alone. Evaluation also considers jurisdiction, assigned unit, direct investigator assignment, classification clearance, and conflict-of-interest against the resource owner.
- `GET /api/v1/cases` and workflow task visibility share the exact same authorization rules, meaning list filters are no longer more relaxed than `GET /api/v1/cases/{caseId}`.

## Default Runtime Configuration

The default configuration for local development is in `.env.example`. The credentials specified there are dummy, local-only credentials and are not meant for production.

For local Compose:

- `KEYCLOAK_ISSUER` still points to `http://localhost:8081/realms/sentinel` to match the token issuer received by local clients.
- `KEYCLOAK_JWKS_URL` in the app container is routed to `host.docker.internal` so the application inside Docker can still fetch JWKS from Keycloak's host port.
- `MINIO_ENDPOINT` for the host defaults to `http://localhost:9000`; in the app container, it is routed to `http://minio:9000`.
- `KAFKA_BOOTSTRAP_SERVERS` for the host defaults to `localhost:29092`; in the app container, it is routed to `kafka:9092`.
- `REDIS_HOST`/`REDIS_PORT` defaults to `localhost:6379`; in the app container, it is routed to `redis:6379`.
- `MAILPIT_SMTP_HOST`/`MAILPIT_SMTP_PORT` defaults to `localhost:1025`; in the app container, it is routed to `mailpit:1025`.
- `APP_INSTANCE_ID` is used as the outbox publisher lease owner.
- `OUTBOX_POLL_INTERVAL`, `OUTBOX_LEASE_DURATION`, and `OUTBOX_BATCH_SIZE` configure the publisher cadence.
- `NOTIFICATION_CONSUMER_GROUP_ID` and `NOTIFICATION_MAX_RETRIES` govern the local inbox consumer.
- `NOTIFICATION_FROM_EMAIL` and `NOTIFICATION_TO_EMAIL` govern the local email envelope for dispatching notifications.
- `MINIO_EVIDENCE_BUCKET` defaults to `sentinel-evidence`.
- `EVIDENCE_UPLOAD_URL_TTL` and `EVIDENCE_DOWNLOAD_URL_TTL` must be ISO-8601 durations like `PT15M`.
- `WORKFLOW_ENGINE_NAME` defaults to `sentinel-workflow-engine`.
- `WORKFLOW_INVESTIGATION_ESCALATION_DURATION` controls the boundary timer escalation for investigation tasks and must be an ISO-8601 duration like `PT30M`.
- When requesting tokens and calling the app from the host, use `localhost` consistently instead of a mix of `localhost` and `127.0.0.1`, as the JWT issuer is verified via an exact-match comparison.
- The BPMN `regulatory-enforcement-case.bpmn` is deployed automatically on application startup.
- The Camunda runtime starts with `databaseSchemaUpdate=false`. The Camunda schema must be created beforehand using `make migrate`.

## Default Users

- `intake-jkt` / `sentinel` with role `CASE_INTAKE_OFFICER` and jurisdiction `JKT`
- `intake-bdg` / `sentinel` with role `CASE_INTAKE_OFFICER` and jurisdiction `BDG`
- `triage-jkt` / `sentinel` with role `TRIAGE_OFFICER` and jurisdiction `JKT`
- `triage-bdg` / `sentinel` with role `TRIAGE_OFFICER` and jurisdiction `BDG`
- `investigator-jkt` / `sentinel` with role `INVESTIGATOR` and jurisdiction `JKT`
- `reviewer-jkt` / `sentinel` with role `CASE_REVIEWER` and jurisdiction `JKT`
- `reviewer-jkt-public` / `sentinel` with role `CASE_REVIEWER`, jurisdiction `JKT`, and clearance limited to `PUBLIC`
- `reviewer-jkt-conflicted` / `sentinel` with role `CASE_REVIEWER`, jurisdiction `JKT`, and conflict against actor `investigator-jkt`
- `decision-jkt` / `sentinel` with role `DECISION_MAKER` and jurisdiction `JKT`
- `appeal-jkt` / `sentinel` with role `APPEAL_OFFICER` and jurisdiction `JKT`
- `supervisor-jkt` / `sentinel` with role `SUPERVISOR` and jurisdiction `JKT`
- `supervisor-jkt-unit-2` / `sentinel` with role `SUPERVISOR`, jurisdiction `JKT`, and assigned unit `JKT-UNIT-2`
- `auditor-jkt` / `sentinel` with role `AUDITOR` and jurisdiction `JKT`
- `system-admin` / `sentinel` with role `SYSTEM_ADMIN`

## Testing Strategy

- Unit tests for application service, domain transition policy, optimistic locking guards, authorization policy, report triage, and evidence lifecycle.
- Workflow unit tests for BPMN model validation and core stage wiring.
- The Karate suite against the running application is divided into:
  - `make karate-smoke` for health, login, report intake, triage, create/get case, and baseline API readiness.
  - `make karate-regression` for all smoke tests plus workflow task happy paths, evidence, appeal, maintenance, and workflow reconciliation which are already considered primary regressions.
  - `make karate-full` for the entire regression suite plus search/cursor matrix, authorization denial matrix, relationship recursion, locking, duplicate-delivery observability, and other critical negative paths against the running application.
- Integration tests using Testcontainers PostgreSQL + Kafka + Keycloak + MinIO for:
  - happy path authorized create/get/triage report
  - happy path case lifecycle from create through close
  - investigator visibility filtered to directly assigned cases
  - assigned-unit restriction, classification clearance, and conflict-of-interest denial on case/recommendation access
  - workflow task query, claim, completion, cursor, search, sort, and duplicate-completion safety
  - workflow reconciliation query, cursor, search, sort, authorization, auto-repair from runtime/history, and invalid-runtime termination
  - evidence upload session, presigned upload, finalize, get, download, checksum mismatch, and unauthorized download audit
  - outbox reliability when Kafka is unavailable and inbox deduplication when duplicate events are resent
  - `409` invalid transition
  - `409` stale optimistic-lock version
  - `401` missing bearer token
  - `403` wrong role
  - `403` wrong jurisdiction
  - public health endpoint
- Integration tests also spin up Redis and Mailpit so that dependency health, notification dispatch, and outbox result flows are validated in a real runtime.
- For the manual sequence of running the app then Karate, run `make up`, `make migrate`, wait for `GET /health` to turn `UP`, then choose the suite: `make karate-smoke`, `make karate-regression`, or `make karate-full`.

## Troubleshooting

- If the application fails to start, make sure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` are correct.
- If the application fails to start with a message stating that Camunda tables do not exist, run `make migrate` first.
- If report requests consistently return `401`, check `KEYCLOAK_ISSUER`, `KEYCLOAK_JWKS_URL`, and the token issuer from Keycloak.
- If tokens from the host fail with `Access token issuer is invalid`, ensure the URL used is consistent with `localhost` as shown in `.env.example`.
- If evidence upload fails to finalize, check `MINIO_ENDPOINT`, the `MINIO_EVIDENCE_BUCKET` bucket, the client upload `Content-Type`, and the SHA-256 checksum sent when creating the upload session.
- If the MinIO bucket does not exist yet, run `make minio-init`.
- If integration tests fail due to Docker, make sure Docker Desktop is active.
- If notifications do not appear, check the `outbox_event` status, then refer to the runbook [docs/runbooks/outbox-stuck.md](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/runbooks/outbox-stuck.md).
- If `GET /health` marks dependencies other than `UP`, check the host/port configurations for Kafka, Redis, Mailpit, and workflow before assuming a business logic bug.
- If consumers move events to the DLQ, refer to the runbook [docs/runbooks/dead-letter-events.md](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/runbooks/dead-letter-events.md).
- If the Kafka backlog increases, refer to the runbook [docs/runbooks/kafka-backlog.md](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/runbooks/kafka-backlog.md).
- If migrations fail, check the changelog at `sentinel-persistence/src/main/resources/db/changelog`.
- If workflow tasks do not appear, check the `workflow_instance` table, the BPMN deployment startup logs, and ensure cases are created via the API so the workflow starts automatically.
- If domain state and workflow state appear out of sync, use the runbook [docs/runbooks/domain-workflow-mismatch-reconciliation.md](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/runbooks/domain-workflow-mismatch-reconciliation.md).