---
type: Runtime Configuration
title: Runtime Configuration
description: All environment-variable-based configuration for the Sentinel Enforcement Platform. No configuration files (application.properties / application.yaml) are used.
tags: [sentinel, configuration, environment, env-vars, docker]
---

# Runtime Configuration

The Sentinel Enforcement Platform is configured **entirely via environment variables**. There are no `application.properties`, `application.yaml`, or any other configuration files. All configuration is consumed by `AppConfiguration.fromEnvironment()` at `/sentinel-bootstrap/src/main/java/com/sentinel/enforcement/bootstrap/AppConfiguration.java`.

## Canonical Source

The canonical reference for all configuration is `/.env.example` (39 lines). This file documents every environment variable with its default value. The `AppConfiguration` record at `/sentinel-bootstrap/src/main/java/com/sentinel/enforcement/bootstrap/AppConfiguration.java` (lines 7–37) consumes these.

```bash
# Copy and customize:
cp .env.example .env
# Docker Compose loads .env automatically; for local native runs, export manually or use a shell script.
```

## Configuration Table

| Env var | Description | Default | Required | Module |
|---|---|---|---|---|
| `HTTP_PORT` | HTTP listener port | `8080` | Yes | Bootstrap |
| `POSTGRES_PORT` | PostgreSQL container port | `5432` | No (Docker) | Docker |
| `POSTGRES_DB` | PostgreSQL database name | `sentinel` | No (Docker) | Docker |
| `POSTGRES_USER` | PostgreSQL user | `sentinel` | No (Docker) | Docker |
| `POSTGRES_PASSWORD` | PostgreSQL password | `sentinel` | No (Docker) | Docker |
| `DB_URL` | JDBC connection URL | `jdbc:postgresql://localhost:5432/sentinel` | Yes | Persistence |
| `DB_USERNAME` | JDBC username | `sentinel` | Yes | Persistence |
| `DB_PASSWORD` | JDBC password | `sentinel` | Yes | Persistence |
| `DB_MAX_POOL_SIZE` | HikariCP max pool size | `12` | No | Persistence |
| `KAFKA_PORT` | Kafka container port | `29092` | No (Docker) | Docker |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | `localhost:29092` | Yes | Messaging |
| `REDIS_PORT` | Redis container port | `6379` | No (Docker) | Docker |
| `REDIS_HOST` | Redis host | `localhost` | Yes | Observability |
| `MAILPIT_WEB_PORT` | Mailpit web UI port | `8025` | No (Docker) | Docker |
| `MAILPIT_SMTP_PORT` | Mailpit SMTP port | `1025` | Yes | Messaging |
| `MAILPIT_SMTP_HOST` | Mailpit SMTP host | `localhost` | Yes | Messaging |
| `NOTIFICATION_FROM_EMAIL` | Sender email for notifications | `sentinel@local.test` | Yes | Messaging |
| `NOTIFICATION_TO_EMAIL` | Default recipient email | `ops@local.test` | Yes | Messaging |
| `APP_INSTANCE_ID` | Unique instance identifier | Auto-generated UUID | No | Messaging |
| `OUTBOX_POLL_INTERVAL` | Outbox poll loop interval (ISO 8601 duration) | `PT2S` | No | Messaging |
| `OUTBOX_LEASE_DURATION` | Outbox lease duration (ISO 8601) | `PT30S` | No | Messaging |
| `OUTBOX_BATCH_SIZE` | Max outbox rows per poll cycle | `20` | No | Messaging |
| `NOTIFICATION_CONSUMER_GROUP_ID` | Kafka consumer group ID | `sentinel-notification-consumer` | No | Messaging |
| `NOTIFICATION_MAX_RETRIES` | Max retry attempts before DLQ | `3` | No | Messaging |
| `MINIO_PORT` | MinIO S3-compatible API port | `9000` | No (Docker) | Docker |
| `MINIO_CONSOLE_PORT` | MinIO web console port | `9001` | No (Docker) | Docker |
| `MINIO_ENDPOINT` | MinIO server endpoint (internal) | `http://localhost:9000` | Yes | Storage |
| `MINIO_PUBLIC_ENDPOINT` | MinIO endpoint exposed to clients | Same as `MINIO_ENDPOINT` | No | Storage |
| `MINIO_ACCESS_KEY` | MinIO access key (root user) | `sentinel` | Yes | Storage |
| `MINIO_SECRET_KEY` | MinIO secret key (root password) | `sentinel-secret` | Yes | Storage |
| `MINIO_EVIDENCE_BUCKET` | MinIO bucket for evidence objects | `sentinel-evidence` | Yes | Storage |
| `EVIDENCE_UPLOAD_URL_TTL` | Presigned upload URL TTL (ISO 8601) | `PT15M` | Yes | Storage |
| `EVIDENCE_DOWNLOAD_URL_TTL` | Presigned download URL TTL (ISO 8601) | `PT10M` | Yes | Storage |
| `KEYCLOAK_PORT` | Keycloak container port | `8081` | No (Docker) | Docker |
| `KEYCLOAK_ISSUER` | Keycloak JWT issuer URL | `http://localhost:8081/realms/sentinel` | Yes | Security |
| `KEYCLOAK_AUDIENCE` | Expected JWT audience claim | `sentinel-api` | Yes | Security |
| `KEYCLOAK_JWKS_URL` | JWKS endpoint for token verification | `http://localhost:8081/realms/sentinel/protocol/openid-connect/certs` | Yes | Security |
| `WORKFLOW_ENGINE_NAME` | Camunda process engine name | `sentinel-workflow-engine` | No | Workflow |
| `WORKFLOW_INVESTIGATION_ESCALATION_DURATION` | BPMN escalation timer duration (ISO 8601) | `PT30M` | Yes | Workflow |

Source: `/.env.example` (all 39 entries), `AppConfiguration.java` lines 43–76.

## AppConfiguration.java Record

The `AppConfiguration` record at `/sentinel-bootstrap/src/main/java/com/sentinel/enforcement/bootstrap/AppConfiguration.java` (lines 7–37) has 31 fields mapped from environment variables:

```java
public record AppConfiguration(
    int httpPort,
    String dbUrl, String dbUsername, String dbPassword, int dbMaxPoolSize,
    String kafkaBootstrapServers,
    String redisHost, int redisPort,
    String mailpitSmtpHost, int mailpitSmtpPort,
    String notificationFromEmail, String notificationToEmail,
    String appInstanceId,
    Duration outboxPollInterval, Duration outboxLeaseDuration, int outboxBatchSize,
    String notificationConsumerGroupId, int notificationMaxRetries,
    String minioEndpoint, String minioPublicEndpoint,
    String minioAccessKey, String minioSecretKey, String minioEvidenceBucket,
    Duration evidenceUploadUrlTtl, Duration evidenceDownloadUrlTtl,
    String keycloakIssuer, String keycloakAudience, String keycloakJwksUrl,
    String workflowEngineName, Duration workflowInvestigationEscalationDuration) { ... }
```

**Construction**: `AppConfiguration.fromEnvironment()` reads `System.getenv()` (line 40). Required env vars throw `IllegalStateException` if missing (line 81). Optional vars use `getOrDefault()` with documented defaults.

## Docker Compose Environment Variable Mapping

The `/docker-compose.yaml` file maps host `.env` variables to container environments. Key translation notes:

| .env var | Docker Compose usage | Line(s) |
|---|---|---|
| `POSTGRES_DB/USER/PASSWORD` | Mapped directly to Postgres env | 7–9 |
| `DB_URL` | **Overridden** to `jdbc:postgresql://postgres:5432/${POSTGRES_DB}` | 158 |
| `KAFKA_BOOTSTRAP_SERVERS` | **Overridden** to `kafka:9092` (container hostname) | 161 |
| `REDIS_HOST` | **Overridden** to `redis` (container hostname) | 162 |
| `REDIS_PORT` | **Overridden** to `6379` (container internal) | 163 |
| `MAILPIT_SMTP_HOST` | **Overridden** to `mailpit` (container hostname) | 164 |
| `MAILPIT_SMTP_PORT` | **Overridden** to `1025` (container internal) | 165 |
| `MINIO_ENDPOINT` | **Overridden** to `http://minio:9000` | 174 |
| `MINIO_PUBLIC_ENDPOINT` | Uses host-facing `localhost:${MINIO_PORT}` | 175 |
| `KEYCLOAK_ISSUER` | Uses host-facing `localhost:${KEYCLOAK_PORT}` | 181 |
| `KEYCLOAK_JWKS_URL` | Uses `host.docker.internal` for host loopback | 183 |

Source: `/docker-compose.yaml` lines 156–185 (app service environment block).

**Important**: When running locally via Maven (not Docker), the `LOCAL_RUNTIME_ENV` variable in `/Makefile` (line 4) sets all defaults for the local shell environment.

## Important Notes

- **No configuration files**: The application does not use `application.properties`, `application.yaml`, or any framework config loader. All configuration is explicitly mapped from env vars in `AppConfiguration.fromEnvironment()`.
- **All durations** are ISO 8601 format (e.g., `PT15M`, `PT30S`). Parsed via `java.time.Duration.parse()`.
- **Container hostname overrides**: When running inside Docker Compose, several host-based values (`DB_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `MINIO_ENDPOINT`) are replaced with Docker internal hostnames. Local Maven runs use `localhost` equivalents.
- **Secrets**: `DB_PASSWORD`, `MINIO_SECRET_KEY`, `POSTGRES_PASSWORD` are plaintext in `.env.example` for local development only. Production deployments should use a secrets manager.
