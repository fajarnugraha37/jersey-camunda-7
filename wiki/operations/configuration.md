# Configuration Reference

All configuration is via environment variables. Parsed by `AppConfiguration.fromEnvironment()`.

---

## Server

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `HTTP_PORT` | Yes | — | HTTP listener port |

## Database

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DB_URL` | Yes | — | JDBC PostgreSQL URL (`jdbc:postgresql://host:port/db`) |
| `DB_USERNAME` | Yes | — | Database user |
| `DB_PASSWORD` | Yes | — | Database password |
| `DB_MAX_POOL_SIZE` | No | `12` | HikariCP max pool size |

## Kafka

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | — | Bootstrap servers (host:port) |

## Redis

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `REDIS_HOST` | Yes | — | Redis host |
| `REDIS_PORT` | Yes | — | Redis port |

## Mailpit (SMTP)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `MAILPIT_SMTP_HOST` | Yes | — | SMTP host |
| `MAILPIT_SMTP_PORT` | Yes | — | SMTP port |

## Notifications

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `NOTIFICATION_FROM_EMAIL` | Yes | — | Sender email address |
| `NOTIFICATION_TO_EMAIL` | Yes | — | Recipient email address |
| `APP_INSTANCE_ID` | No | random UUID | Unique instance ID (for outbox leasing) |
| `OUTBOX_POLL_INTERVAL` | No | `PT2S` | Outbox poll interval (ISO-8601 duration) |
| `OUTBOX_LEASE_DURATION` | No | `PT30S` | Outbox lease duration |
| `OUTBOX_BATCH_SIZE` | No | `20` | Outbox batch size |
| `NOTIFICATION_CONSUMER_GROUP_ID` | No | `sentinel-notification-consumer` | Kafka consumer group |
| `NOTIFICATION_MAX_RETRIES` | No | `3` | Max delivery retry attempts |

## MinIO (Object Storage)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `MINIO_ENDPOINT` | Yes | — | Internal MinIO endpoint |
| `MINIO_PUBLIC_ENDPOINT` | No | value of MINIO_ENDPOINT | Public-facing endpoint (presigned URLs) |
| `MINIO_ACCESS_KEY` | Yes | — | Access key |
| `MINIO_SECRET_KEY` | Yes | — | Secret key |
| `MINIO_EVIDENCE_BUCKET` | Yes | — | Evidence bucket name |
| `EVIDENCE_UPLOAD_URL_TTL` | Yes | — | Presigned upload URL TTL (ISO-8601) |
| `EVIDENCE_DOWNLOAD_URL_TTL` | Yes | — | Presigned download URL TTL (ISO-8601) |

## Keycloak (Authentication)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `KEYCLOAK_ISSUER` | Yes | — | Keycloak realm issuer URL |
| `KEYCLOAK_AUDIENCE` | Yes | — | Expected JWT audience |
| `KEYCLOAK_JWKS_URL` | Yes | — | JWKS certificate endpoint |

## Workflow (Camunda)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `WORKFLOW_ENGINE_NAME` | No | `sentinel-workflow-engine` | Camunda process engine name |
| `WORKFLOW_INVESTIGATION_ESCALATION_DURATION` | Yes | — | Investigation timer duration (ISO-8601) |

---

## `.env.example`

```bash
# Server
HTTP_PORT=8080

# PostgreSQL
POSTGRES_PORT=5432
POSTGRES_DB=sentinel
POSTGRES_USER=sentinel
POSTGRES_PASSWORD=sentinel
DB_URL=jdbc:postgresql://localhost:5432/sentinel
DB_USERNAME=sentinel
DB_PASSWORD=sentinel
DB_MAX_POOL_SIZE=12

# Kafka
KAFKA_PORT=29092
KAFKA_BOOTSTRAP_SERVERS=localhost:29092

# Redis
REDIS_PORT=6379
REDIS_HOST=localhost

# Mailpit
MAILPIT_WEB_PORT=8025
MAILPIT_SMTP_PORT=1025
MAILPIT_SMTP_HOST=localhost

# Notifications
NOTIFICATION_FROM_EMAIL=sentinel@local.test
NOTIFICATION_TO_EMAIL=ops@local.test
APP_INSTANCE_ID=sentinel-local
OUTBOX_POLL_INTERVAL=PT2S
OUTBOX_LEASE_DURATION=PT30S
OUTBOX_BATCH_SIZE=20
NOTIFICATION_CONSUMER_GROUP_ID=sentinel-notification-consumer
NOTIFICATION_MAX_RETRIES=3

# MinIO
MINIO_PORT=9000
MINIO_CONSOLE_PORT=9001
MINIO_ENDPOINT=http://localhost:9000
MINIO_PUBLIC_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=sentinel
MINIO_SECRET_KEY=sentinel-secret
MINIO_EVIDENCE_BUCKET=sentinel-evidence
EVIDENCE_UPLOAD_URL_TTL=PT15M
EVIDENCE_DOWNLOAD_URL_TTL=PT10M

# Keycloak
KEYCLOAK_PORT=8081
KEYCLOAK_ISSUER=http://localhost:8081/realms/sentinel
KEYCLOAK_AUDIENCE=sentinel-api
KEYCLOAK_JWKS_URL=http://localhost:8081/realms/sentinel/protocol/openid-connect/certs

# Workflow
WORKFLOW_ENGINE_NAME=sentinel-workflow-engine
WORKFLOW_INVESTIGATION_ESCALATION_DURATION=PT30M
```

---

## Startup Validation

If any required variable is missing, the application throws:
```
IllegalStateException: Missing required configuration: <KEY>
```
