# Sentinel Enforcement Platform

Sentinel Enforcement Platform adalah project latihan enterprise untuk regulatory enforcement dan complex case management. Fokus increment saat ini adalah foundation vertical slice: health endpoint, koneksi PostgreSQL, Liquibase migration, dan API create/get report.

## Architecture Overview

- Bentuk aplikasi saat ini: modular monolith berbasis Maven multi-module.
- Modul yang sudah dibuat:
  - `sentinel-domain`
  - `sentinel-application`
  - `sentinel-api`
  - `sentinel-persistence`
  - `sentinel-bootstrap`
  - `sentinel-integration-tests`
- Boundary saat ini:
  - `sentinel-api` menangani Jersey resource, validation, JSON, dan error envelope.
  - `sentinel-application` menangani use case create/get report.
  - `sentinel-domain` memegang model domain report.
  - `sentinel-persistence` memegang MyBatis adapter dan Liquibase changelog.
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
2. Jalankan `make up` untuk menyalakan PostgreSQL dan aplikasi.
3. Jalankan `make migrate` bila ingin apply migration manual dari local host.
4. Cek `http://localhost:8080/health`.

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

Spesifikasi kontrak saat ini ada di [docs/api/openapi.yaml](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/api/openapi.yaml).
Generated request/response model untuk layer API dibangun dari spec tersebut pada phase `generate-sources`.

## Default Runtime Configuration

Konfigurasi default untuk local development ada di `.env.example`. Credential di sana adalah dummy local-only credential dan tidak untuk production.

## Testing Strategy

- Unit test untuk application service.
- Integration test dengan Testcontainers PostgreSQL untuk migration dan round-trip HTTP API.

## Troubleshooting

- Jika aplikasi gagal start, pastikan `DB_URL`, `DB_USERNAME`, dan `DB_PASSWORD` sesuai.
- Jika integration test gagal karena Docker, pastikan Docker Desktop aktif.
- Jika migration gagal, cek changelog di `sentinel-persistence/src/main/resources/db/changelog`.
