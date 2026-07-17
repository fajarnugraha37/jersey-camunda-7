---
type: Playbook
title: Operations and Runbooks
description: Operational procedures, troubleshooting guides, configuration reference, and default user accounts for the Sentinel Enforcement Platform.
tags: [operations, runbooks, troubleshooting, configuration]
---

# Operations &amp; Runbooks

## Local Development Commands

See the [architecture overview](/openwiki/architecture/overview.md) for module responsibilities, and the [testing strategy](/openwiki/testing/strategy.md) for how these commands map to test suites.

### Infrastructure Lifecycle
```bash
make up        # Start PostgreSQL, Kafka, Redis, MinIO, Keycloak, Mailpit
make down      # Stop all containers
make restart   # Restart containers
make reset     # Stop and remove volumes (destroys data)
make logs      # Tail container logs
make app-logs  # Tail app container logs
```

### Build &amp; Test
```bash
make bootstrap          # Restore Maven dependencies
make compile            # Compile all modules
make unit-test          # Run unit tests
make integration-test   # Run Testcontainers integration tests
make workflow-test      # Workflow-focused tests
make messaging-test     # Messaging-focused tests
make e2e-test           # Full integration test suite
make karate-smoke       # Karate smoke suite (running app needed)
make karate-regression  # Karate regression suite
make karate-full        # Karate full coverage suite
make verify             # Full Maven verify
```

### Database
```bash
make migrate            # Run Liquibase + Camunda migration, then start app
make rollback COUNT=1   # Rollback N Liquibase changesets
make db-shell           # Open psql shell
make db-status          # Check PostgreSQL container
make db-reset           # Stop, remove volume, restart PostgreSQL
```

### Other
```bash
make seed               # Idempotent bootstrap (MinIO bucket init)
make smoke-test         # Hit GET /health endpoint
make kafka-topics       # List Kafka topics
make minio-init         # Ensure evidence bucket exists
make bpmn-validate      # Validate BPMN models
make format             # Apply Spotless formatting
```

## Runbooks

### 1. Camunda Schema Migration
**File:** `/docs/runbooks/camunda-embedded-schema-migration.md`

1. Run `make up` (ensure PostgreSQL is running)
2. Run `make migrate` &mdash; runs Liquibase + Camunda schema migration, then starts app
3. Wait for startup, verify `GET /health` returns 200
4. Schema creation is one-way; recreate DB on failure

**Never set `databaseSchemaUpdate=true` in production**. Schema changes are explicit via `CamundaSchemaMigrator`.

### 2. Outbox Stuck
**File:** `/docs/runbooks/outbox-stuck.md`

**Symptom:** Events not being published to Kafka despite successful business commits.

**Check:**
1. Kafka reachability: `docker compose ps kafka`
2. Pending rows: `SELECT count(*) FROM outbox_event WHERE status = 'PENDING'`
3. Leases: `SELECT * FROM outbox_event WHERE lease_expires_at IS NOT NULL`
4. Errors: Check `last_error` column in `outbox_event`

**Resolution:**
- Ensure Kafka is healthy
- Verify `OUTBOX_POLL_INTERVAL` and `OUTBOX_BATCH_SIZE` configuration
- **Never delete outbox rows manually** &mdash; business commits depend on them

### 3. Kafka Backlog
**File:** `/docs/runbooks/kafka-backlog.md`

**Check:**
- List topics: `make kafka-topics`
- Inspect consumer lag
- Verify consumer is alive

**Root causes:** Consumer can't keep up, or a poison event blocks processing.

### 4. Dead-Letter Events
**File:** `/docs/runbooks/dead-letter-events.md`

1. Consume `*.dlq` topics to see failed events
2. Check `inbox_event` table for failed attempts
3. Fix root cause (schema issue, invalid payload)
4. Re-publish the event to the original topic

**Never delete DLQ messages without a documented recovery plan.**

### 5. MinIO Evidence Storage Issues
**File:** `/docs/runbooks/minio-evidence-storage.md`

**Symptom:** `503 Service Unavailable` on evidence endpoints.

**Check:**
1. `docker compose ps minio`
2. `make minio-init` to ensure bucket exists
3. Verify `MINIO_*` and `EVIDENCE_*_URL_TTL` env vars
4. **Finalization rejects** if SHA-256 checksum, size, or content-type don't match

### 6. Domain-Workflow Mismatch
**File:** `/docs/runbooks/domain-workflow-mismatch-reconciliation.md`

**Symptom:** Case exists in application but workflow instance is missing or out of sync.

**Resolution via API:**
1. `GET /api/v1/workflow-reconciliation` &mdash; list mismatches (supervisor/admin only)
2. `POST /api/v1/workflow-reconciliation/{caseId}/actions`
   - `AUTO_REPAIR` &mdash; recreate correlation row from runtime
   - `TERMINATE_RUNTIME` &mdash; end orphaned process for terminal cases

## Configuration Reference

**File:** `.env.example`

| Variable | Default | Purpose |
|---|---|---|
| `HTTP_PORT` | `8080` | Application HTTP port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/sentinel` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `sentinel` | DB user |
| `DB_PASSWORD` | `sentinel` | DB password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Kafka broker |
| `REDIS_HOST` / `REDIS_PORT` | `localhost:6379` | Redis |
| `MAILPIT_SMTP_HOST` / `MAILPIT_SMTP_PORT` | `localhost:1025` | SMTP (Mailpit) |
| `MINIO_ENDPOINT` | `http://localhost:9000` | MinIO |
| `MINIO_PUBLIC_ENDPOINT` | `http://localhost:9000` | Public MinIO URL |
| `MINIO_EVIDENCE_BUCKET` | `sentinel-evidence` | Evidence bucket |
| `EVIDENCE_UPLOAD_URL_TTL` | `PT15M` | Upload URL lifetime |
| `EVIDENCE_DOWNLOAD_URL_TTL` | `PT10M` | Download URL lifetime |
| `KEYCLOAK_ISSUER` | `http://localhost:8081/realms/sentinel` | Keycloak issuer |
| `KEYCLOAK_JWKS_URL` | `http://localhost:8081/realms/sentinel/protocol/openid-connect/certs` | JWKS endpoint |
| `APP_INSTANCE_ID` | `sentinel-local` | Outbox lease owner |
| `OUTBOX_POLL_INTERVAL` | `PT2S` | Outbox poll cadence |
| `OUTBOX_LEASE_DURATION` | `PT30S` | Outbox lease duration |
| `OUTBOX_BATCH_SIZE` | `20` | Outbox batch size |
| `WORKFLOW_ENGINE_NAME` | `sentinel-workflow-engine` | Camunda engine name |
| `WORKFLOW_INVESTIGATION_ESCALATION_DURATION` | `PT30M` | Investigation timeout |

## Default Users

All passwords are `sentinel`. Keycloak realm: `/deployment/keycloak/realm/sentinel-realm.json`.

| Username | Role | Jurisdiction |
|---|---|---|
| `intake-jkt` | CASE_INTAKE_OFFICER | JKT |
| `intake-bdg` | CASE_INTAKE_OFFICER | BDG |
| `triage-jkt` | TRIAGE_OFFICER | JKT |
| `triage-bdg` | TRIAGE_OFFICER | BDG |
| `investigator-jkt` | INVESTIGATOR | JKT |
| `reviewer-jkt` | CASE_REVIEWER | JKT |
| `reviewer-jkt-public` | CASE_REVIEWER (PUBLIC clearance only) | JKT |
| `reviewer-jkt-conflicted` | CASE_REVIEWER (conflicted with investigator-jkt) | JKT |
| `decision-jkt` | DECISION_MAKER | JKT |
| `appeal-jkt` | APPEAL_OFFICER | JKT |
| `supervisor-jkt` | SUPERVISOR | JKT |
| `supervisor-jkt-unit-2` | SUPERVISOR (JKT-UNIT-2) | JKT |
| `auditor-jkt` | AUDITOR | JKT |
| `system-admin` | SYSTEM_ADMIN | &mdash; |

## Docker Compose Services

**File:** `docker-compose.yaml`

| Service | Image | Purpose |
|---|---|---|
| `postgres` | `postgres:18.3-alpine` | Primary database |
| `kafka` | `confluentinc/cp-kafka:7.8.1` | Event streaming |
| `redis` | `redis:7.2.7-alpine` | Cache / session |
| `minio` | `minio:RELEASE.2025-09-07` | Object storage |
| `minio-init` | `minio/mc:latest` | Bucket bootstrap |
| `keycloak` | `keycloak:26.6` | Identity provider |
| `mailpit` | `axllent/mailpit` | SMTP testing |
| `app` | (Dockerfile) | Sentinel application |
