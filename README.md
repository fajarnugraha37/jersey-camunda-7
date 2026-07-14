# Sentinel Enforcement Platform

Sentinel Enforcement Platform adalah project latihan enterprise untuk regulatory enforcement dan complex case management. Fokus increment saat ini sudah mencakup foundation, authentication/authorization, Phase 3 case lifecycle vertical slice, dan Phase 4 workflow orchestration slice berbasis embedded Camunda 7 dengan task API, workflow correlation, timer escalation, optimistic locking, status history, assignment history, dan audit foundation.

## Architecture Overview

- Bentuk aplikasi saat ini: modular monolith berbasis Maven multi-module.
- Modul yang sudah dibuat:
  - `sentinel-domain`
  - `sentinel-application`
  - `sentinel-api`
  - `sentinel-persistence`
  - `sentinel-workflow`
  - `sentinel-security`
  - `sentinel-bootstrap`
  - `sentinel-integration-tests`
- Boundary saat ini:
  - `sentinel-api` menangani Jersey resource, validation, JSON, dan error envelope.
  - `sentinel-application` menangani use case create/get report dan authorization contract.
  - `sentinel-domain` memegang model domain report.
  - `sentinel-persistence` memegang MyBatis adapter dan Liquibase changelog.
  - `sentinel-workflow` memegang embedded Camunda runtime, BPMN deployment, workflow correlation, task query adapter, dan escalation delegate.
  - `sentinel-security` memegang JWT verification Keycloak dan role/jurisdiction authorization.
  - `sentinel-bootstrap` merakit dependency, start server, dan menjalankan migration.

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
2. Jalankan `make up` untuk menyalakan PostgreSQL, Keycloak, dan aplikasi.
3. Jalankan `make migrate` bila ingin apply migration manual dari local host.
4. Cek `http://localhost:8080/health`.
5. Ambil token dari Keycloak realm `sentinel` bila ingin memanggil endpoint report yang sudah diproteksi.

## Commands

- `make help`
- `make format`
- `make compile`
- `make unit-test`
- `make integration-test`
- `make verify`
- `make openapi-generate`
- `make openapi-validate`
- `make up`
- `make down`
- `make logs`
- `make migrate`
- `make smoke-test`

## Current API

- `GET /health`
- `POST /api/v1/reports`
- `GET /api/v1/reports/{reportId}`
- `POST /api/v1/cases`
- `GET /api/v1/cases`
- `GET /api/v1/cases/{caseId}`
- `POST /api/v1/cases/{caseId}/assignments`
- `POST /api/v1/cases/{caseId}/transitions`
- `GET /api/v1/cases/{caseId}/audit-events`
- `GET /api/v1/tasks`
- `POST /api/v1/tasks/{taskId}/claim`
- `POST /api/v1/tasks/{taskId}/complete`

`/health` tetap public. Endpoint report, case, dan workflow task memerlukan bearer JWT dari Keycloak dan menerapkan authorization berbasis role, jurisdiction, dan direct assignment untuk actor investigator-only.

Spesifikasi kontrak saat ini ada di [docs/api/openapi.yaml](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/api/openapi.yaml).
Generated request/response model untuk layer API dibangun dari spec tersebut pada phase `generate-sources`.

Pattern wajib untuk endpoint list ada di [docs/api/list-query-pattern.md](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/api/list-query-pattern.md). Semua list API baru harus mengikuti kombinasi `cursor`, `limit`, `q`, `searchField/searchValue`, dan enum-based `sortBy/sortDirection` dengan SQL dinamis yang aman, termasuk bila source datanya bukan MyBatis row langsung seperti task list Camunda.

## Default Runtime Configuration

Konfigurasi default untuk local development ada di `.env.example`. Credential di sana adalah dummy local-only credential dan tidak untuk production.

Untuk local Compose:

- `KEYCLOAK_ISSUER` tetap mengacu ke `http://localhost:8081/realms/sentinel` agar cocok dengan issuer token yang didapat client lokal.
- `KEYCLOAK_JWKS_URL` di container app diarahkan ke `host.docker.internal` agar aplikasi di Docker tetap bisa mengambil JWKS dari Keycloak host port.
- `WORKFLOW_ENGINE_NAME` default ke `sentinel-workflow-engine`.
- `WORKFLOW_INVESTIGATION_ESCALATION_DURATION` mengontrol boundary timer escalation untuk task investigasi dan harus berupa ISO-8601 duration seperti `PT30M`.
- Saat meminta token dan memanggil app dari host, gunakan `localhost` secara konsisten, bukan campuran `localhost` dan `127.0.0.1`, karena issuer JWT diverifikasi secara exact-match.
- BPMN `regulatory-enforcement-case.bpmn` dideploy otomatis saat aplikasi start. Tidak ada deployment step terpisah karena Camunda runtime berjalan embedded di aplikasi.

## Default Users

- `intake-jkt` / `sentinel` dengan role `CASE_INTAKE_OFFICER` dan jurisdiction `JKT`
- `intake-bdg` / `sentinel` dengan role `CASE_INTAKE_OFFICER` dan jurisdiction `BDG`
- `triage-jkt` / `sentinel` dengan role `TRIAGE_OFFICER` dan jurisdiction `JKT`
- `triage-bdg` / `sentinel` dengan role `TRIAGE_OFFICER` dan jurisdiction `BDG`
- `investigator-jkt` / `sentinel` dengan role `INVESTIGATOR` dan jurisdiction `JKT`
- `reviewer-jkt` / `sentinel` dengan role `CASE_REVIEWER` dan jurisdiction `JKT`
- `decision-jkt` / `sentinel` dengan role `DECISION_MAKER` dan jurisdiction `JKT`
- `appeal-jkt` / `sentinel` dengan role `APPEAL_OFFICER` dan jurisdiction `JKT`
- `supervisor-jkt` / `sentinel` dengan role `SUPERVISOR` dan jurisdiction `JKT`
- `auditor-jkt` / `sentinel` dengan role `AUDITOR` dan jurisdiction `JKT`
- `system-admin` / `sentinel` dengan role `SYSTEM_ADMIN`

## Testing Strategy

- Unit test untuk application service, domain transition policy, optimistic locking guards, dan authorization policy.
- Workflow unit test untuk validasi model BPMN dan wiring stage utama.
- Integration test dengan Testcontainers PostgreSQL + Keycloak untuk:
  - happy path authorized create/get report
  - happy path case lifecycle from create through close
  - investigator visibility filtered to directly assigned cases
  - workflow task query, claim, completion, cursor, search, sort, and duplicate-completion safety
  - `409` invalid transition
  - `409` stale optimistic-lock version
  - `401` tanpa bearer token
  - `403` role salah
  - `403` jurisdiction salah
  - public health endpoint

## Troubleshooting

- Jika aplikasi gagal start, pastikan `DB_URL`, `DB_USERNAME`, dan `DB_PASSWORD` sesuai.
- Jika request report selalu `401`, cek `KEYCLOAK_ISSUER`, `KEYCLOAK_JWKS_URL`, dan token issuer dari Keycloak.
- Jika token dari host gagal dengan `Access token issuer is invalid`, pastikan URL yang dipakai konsisten dengan `localhost` seperti di `.env.example`.
- Jika integration test gagal karena Docker, pastikan Docker Desktop aktif.
- Jika migration gagal, cek changelog di `sentinel-persistence/src/main/resources/db/changelog`.
- Jika workflow task tidak muncul, cek `workflow_instance` table, log startup BPMN deployment, dan pastikan case dibuat lewat API sehingga workflow otomatis dimulai.
