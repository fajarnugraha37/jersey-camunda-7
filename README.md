# Sentinel Enforcement Platform

Sentinel Enforcement Platform adalah project latihan enterprise untuk regulatory enforcement dan complex case management. State repo saat ini mencakup phase 0-8 sesuai roadmap utama: foundation, intake, authentication/authorization, case lifecycle, embedded Camunda workflow, evidence intake berbasis MinIO, Kafka reliability, lalu aggregate recommendation/review/decision/sanction/appeal beserta authorization case-level yang lebih ketat. Repo ini juga masih membawa workflow reconciliation tooling yang sebelumnya sudah ditambahkan sebagai hardening slice tambahan.

## Architecture Overview

- Bentuk aplikasi: modular monolith berbasis Maven multi-module.
- Modul yang tersedia:
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
- Boundary saat ini:
  - `sentinel-api` menangani Jersey resource, bean validation, JSON, dan error envelope.
  - `sentinel-application` menangani use case, authorization orchestration, command/query contract, dan workflow/storage port.
  - `sentinel-domain` memegang aggregate, transition rule, dan invariant.
  - `sentinel-persistence` memegang MyBatis adapter dan Liquibase changelog.
  - `sentinel-messaging` memegang Kafka publisher/consumer runtime, outbox polling, retry, dan dead-letter routing.
  - `sentinel-observability` memegang correlation context, request metrics, dan dependency health composition.
  - `sentinel-storage` memegang adapter MinIO untuk evidence storage.
  - `sentinel-workflow` memegang embedded Camunda runtime, BPMN deployment, task query adapter, dan workflow correlation.
  - `sentinel-security` memegang JWT verification Keycloak dan authorization berbasis role, jurisdiction, assigned unit, classification clearance, direct assignment, dan conflict-of-interest.
  - `sentinel-bootstrap` merakit dependency, start server, readiness, dan migration entrypoint.

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

1. Copy `.env.example` menjadi `.env` bila ingin override default local config.
2. Jalankan `make bootstrap`.
3. Jalankan `make up` untuk menyalakan PostgreSQL, Kafka, Redis, Mailpit, MinIO, Keycloak, dan bootstrap bucket MinIO.
4. Jalankan `make migrate` untuk menjalankan migration aplikasi + schema Camunda official, lalu start aplikasi.
5. Jalankan `make seed` bila ingin mengulang bootstrap helper yang idempotent.
6. Jalankan `make smoke-test`.

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

- Topic yang aktif pada phase ini:
  - `case.lifecycle.v1`
  - `case.assignment.v1`
  - `evidence.lifecycle.v1`
  - `decision.lifecycle.v1`
  - `sanction.lifecycle.v1`
  - `appeal.lifecycle.v1`
  - `notification.command.v1`
  - `notification.result.v1`
  - `audit.integration.v1`
- Setiap perubahan domain yang relevan menulis event ke `outbox_event` dalam transaksi yang sama dengan perubahan bisnis.
- Background publisher akan lease row pending dengan `FOR UPDATE SKIP LOCKED`, publish ke Kafka, lalu menandai row sebagai `PUBLISHED`.
- Notification projection dan notification command consumer memproses event melalui `inbox_event` agar duplicate delivery tidak menghasilkan duplicate side effect pada tabel `notification`.
- Event audit domain yang relevan juga dipublikasikan ke `audit.integration.v1`, sehingga audit append-only database dan integration stream tetap sinkron.
- Retry dikirim ke topic `.retry` dan kegagalan berulang dipindahkan ke topic `.dlq`.

Spesifikasi kontrak ada di [docs/api/openapi.yaml](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/api/openapi.yaml). Generated request/response model untuk layer API dibangun dari spec tersebut pada phase `generate-sources`.

Pattern wajib untuk endpoint list ada di [docs/api/list-query-pattern.md](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/api/list-query-pattern.md). Semua list API baru harus mengikuti kombinasi `cursor`, `limit`, `q`, `searchField/searchValue`, dan enum-based `sortBy/sortDirection` dengan SQL dinamis yang aman.

## Authorization Model

- JWT actor lokal sekarang membawa claim `jurisdictions`, `assigned_units`, `case_classifications`, dan `conflicted_actor_ids`.
- Akses resource kasus tidak hanya ditentukan role. Evaluasinya juga mempertimbangkan jurisdiction, assigned unit, direct assignment investigator, classification clearance, dan conflict-of-interest terhadap owner resource.
- `GET /api/v1/cases` dan visibility task workflow memakai aturan otorisasi yang sama, jadi filter list tidak lagi lebih longgar dari `GET /api/v1/cases/{caseId}`.

## Default Runtime Configuration

Konfigurasi default untuk local development ada di `.env.example`. Credential di sana adalah dummy local-only credential dan tidak untuk production.

Untuk local Compose:

- `KEYCLOAK_ISSUER` tetap mengacu ke `http://localhost:8081/realms/sentinel` agar cocok dengan issuer token yang didapat client lokal.
- `KEYCLOAK_JWKS_URL` di container app diarahkan ke `host.docker.internal` agar aplikasi di Docker tetap bisa mengambil JWKS dari Keycloak host port.
- `MINIO_ENDPOINT` untuk host default ke `http://localhost:9000`; di container app diarahkan ke `http://minio:9000`.
- `KAFKA_BOOTSTRAP_SERVERS` untuk host default ke `localhost:29092`; di container app diarahkan ke `kafka:9092`.
- `REDIS_HOST`/`REDIS_PORT` default ke `localhost:6379`; di container app diarahkan ke `redis:6379`.
- `MAILPIT_SMTP_HOST`/`MAILPIT_SMTP_PORT` default ke `localhost:1025`; di container app diarahkan ke `mailpit:1025`.
- `APP_INSTANCE_ID` dipakai sebagai lease owner outbox publisher.
- `OUTBOX_POLL_INTERVAL`, `OUTBOX_LEASE_DURATION`, dan `OUTBOX_BATCH_SIZE` mengatur cadence publisher.
- `NOTIFICATION_CONSUMER_GROUP_ID` dan `NOTIFICATION_MAX_RETRIES` mengatur inbox consumer lokal.
- `NOTIFICATION_FROM_EMAIL` dan `NOTIFICATION_TO_EMAIL` mengatur envelope email lokal untuk dispatch notification.
- `MINIO_EVIDENCE_BUCKET` default ke `sentinel-evidence`.
- `EVIDENCE_UPLOAD_URL_TTL` dan `EVIDENCE_DOWNLOAD_URL_TTL` harus berupa ISO-8601 duration seperti `PT15M`.
- `WORKFLOW_ENGINE_NAME` default ke `sentinel-workflow-engine`.
- `WORKFLOW_INVESTIGATION_ESCALATION_DURATION` mengontrol boundary timer escalation untuk task investigasi dan harus berupa ISO-8601 duration seperti `PT30M`.
- Saat meminta token dan memanggil app dari host, gunakan `localhost` secara konsisten, bukan campuran `localhost` dan `127.0.0.1`, karena issuer JWT diverifikasi secara exact-match.
- BPMN `regulatory-enforcement-case.bpmn` dideploy otomatis saat aplikasi start.
- Camunda runtime start dengan `databaseSchemaUpdate=false`. Schema Camunda harus sudah dibuat lebih dulu melalui `make migrate`.

## Default Users

- `intake-jkt` / `sentinel` dengan role `CASE_INTAKE_OFFICER` dan jurisdiction `JKT`
- `intake-bdg` / `sentinel` dengan role `CASE_INTAKE_OFFICER` dan jurisdiction `BDG`
- `triage-jkt` / `sentinel` dengan role `TRIAGE_OFFICER` dan jurisdiction `JKT`
- `triage-bdg` / `sentinel` dengan role `TRIAGE_OFFICER` dan jurisdiction `BDG`
- `investigator-jkt` / `sentinel` dengan role `INVESTIGATOR` dan jurisdiction `JKT`
- `reviewer-jkt` / `sentinel` dengan role `CASE_REVIEWER` dan jurisdiction `JKT`
- `reviewer-jkt-public` / `sentinel` dengan role `CASE_REVIEWER`, jurisdiction `JKT`, dan clearance hanya `PUBLIC`
- `reviewer-jkt-conflicted` / `sentinel` dengan role `CASE_REVIEWER`, jurisdiction `JKT`, dan conflict terhadap actor `investigator-jkt`
- `decision-jkt` / `sentinel` dengan role `DECISION_MAKER` dan jurisdiction `JKT`
- `appeal-jkt` / `sentinel` dengan role `APPEAL_OFFICER` dan jurisdiction `JKT`
- `supervisor-jkt` / `sentinel` dengan role `SUPERVISOR` dan jurisdiction `JKT`
- `supervisor-jkt-unit-2` / `sentinel` dengan role `SUPERVISOR`, jurisdiction `JKT`, dan assigned unit `JKT-UNIT-2`
- `auditor-jkt` / `sentinel` dengan role `AUDITOR` dan jurisdiction `JKT`
- `system-admin` / `sentinel` dengan role `SYSTEM_ADMIN`

## Testing Strategy

- Unit test untuk application service, domain transition policy, optimistic locking guards, authorization policy, report triage, dan evidence lifecycle.
- Workflow unit test untuk validasi model BPMN dan wiring stage utama.
- Karate suite terhadap aplikasi yang running dibagi menjadi:
  - `make karate-smoke` untuk health, login, report intake, triage, create/get case, dan baseline API readiness.
  - `make karate-regression` untuk seluruh smoke ditambah workflow task happy path, evidence, appeal, maintenance, dan workflow reconciliation yang sudah dianggap regresi utama.
  - `make karate-full` untuk seluruh regression ditambah search/cursor matrix, authorization denial matrix, relationship recursion, locking, duplicate-delivery observability, dan negative paths penting lain terhadap aplikasi yang sedang berjalan.
- Integration test dengan Testcontainers PostgreSQL + Kafka + Keycloak + MinIO untuk:
  - happy path authorized create/get/triage report
  - happy path case lifecycle from create through close
  - investigator visibility filtered to directly assigned cases
  - assigned-unit restriction, classification clearance, dan conflict-of-interest denial pada case/recommendation access
  - workflow task query, claim, completion, cursor, search, sort, and duplicate-completion safety
  - workflow reconciliation query, cursor, search, sort, authorization, auto-repair from runtime/history, and invalid-runtime termination
  - evidence upload session, presigned upload, finalize, get, download, checksum mismatch, and unauthorized download audit
  - outbox reliability saat Kafka unavailable dan inbox deduplication saat duplicate event dikirim ulang
  - `409` invalid transition
  - `409` stale optimistic-lock version
  - `401` tanpa bearer token
  - `403` role salah
  - `403` jurisdiction salah
  - public health endpoint
- Integration test juga menyalakan Redis dan Mailpit agar dependency health, notification dispatch, dan outbox result flow tervalidasi pada runtime nyata.
- Untuk sequence manual menjalankan app lalu Karate, jalankan `make up`, `make migrate`, tunggu `GET /health` menjadi `UP`, lalu pilih suite `make karate-smoke`, `make karate-regression`, atau `make karate-full`.

## Troubleshooting

- Jika aplikasi gagal start, pastikan `DB_URL`, `DB_USERNAME`, dan `DB_PASSWORD` sesuai.
- Jika aplikasi gagal start dengan pesan tidak ada tabel Camunda, jalankan `make migrate` terlebih dahulu.
- Jika request report selalu `401`, cek `KEYCLOAK_ISSUER`, `KEYCLOAK_JWKS_URL`, dan token issuer dari Keycloak.
- Jika token dari host gagal dengan `Access token issuer is invalid`, pastikan URL yang dipakai konsisten dengan `localhost` seperti di `.env.example`.
- Jika upload evidence gagal finalize, cek `MINIO_ENDPOINT`, bucket `MINIO_EVIDENCE_BUCKET`, `Content-Type` upload client, dan checksum SHA-256 yang dikirim saat membuat upload session.
- Jika bucket MinIO belum ada, jalankan `make minio-init`.
- Jika integration test gagal karena Docker, pastikan Docker Desktop aktif.
- Jika notification tidak muncul, cek `outbox_event` status, lalu lihat runbook [docs/runbooks/outbox-stuck.md](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/runbooks/outbox-stuck.md).
- Jika `GET /health` menandai dependency selain `UP`, cek host/port untuk Kafka, Redis, Mailpit, dan workflow sebelum mengasumsikan bug bisnis.
- Jika consumer memindahkan event ke DLQ, lihat runbook [docs/runbooks/dead-letter-events.md](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/runbooks/dead-letter-events.md).
- Jika backlog Kafka meningkat, lihat runbook [docs/runbooks/kafka-backlog.md](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/runbooks/kafka-backlog.md).
- Jika migration gagal, cek changelog di `sentinel-persistence/src/main/resources/db/changelog`.
- Jika workflow task tidak muncul, cek `workflow_instance` table, log startup BPMN deployment, dan pastikan case dibuat lewat API sehingga workflow otomatis dimulai.
- Jika domain state dan workflow state terlihat tidak sinkron, gunakan runbook [docs/runbooks/domain-workflow-mismatch-reconciliation.md](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/runbooks/domain-workflow-mismatch-reconciliation.md).
