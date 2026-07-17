---
type: Reference
title: Messaging and Storage Integration
description: Event-driven messaging infrastructure (transactional outbox, Kafka, notifications) and object storage (MinIO evidence lifecycle) in the Sentinel Enforcement Platform.
tags: [messaging, kafka, outbox, minio, storage, observability]
---

# Messaging &amp; Storage Integration

## Transactional Outbox Pattern

Sentinel uses the **transactional outbox** pattern to guarantee reliable event delivery without distributed transactions.

The outbox is wired into the [application service layer](/openwiki/architecture/overview.md#application-layer). When a [domain aggregate](/openwiki/domain/concepts.md) is mutated, the application service writes both the business data and an `outbox_event` row in the same database transaction.

### Flow

```
Application Service
  &rarr; writes business data + outbox_event row (same DB transaction)
  &rarr; KafkaOutboxPublisher (background, periodic)
    &rarr; SELECT ... FROM outbox_event WHERE status='PENDING' ORDER BY created_at
       FOR UPDATE SKIP LOCKED LIMIT batch_size
    &rarr; publish to Kafka topic
    &rarr; UPDATE outbox_event SET status='PUBLISHED'
```

### Key Components

| Component | File | Responsibility |
|---|---|---|
| `OutboxRepository` (interface) | `sentinel-application/.../messaging/OutboxRepository.java` | Port for outbox persistence |
| `OutboxRepositoryMyBatisAdapter` | `sentinel-persistence/.../messaging/OutboxRepositoryMyBatisAdapter.java` | MyBatis implementation |
| `KafkaOutboxPublisher` | `sentinel-messaging/.../KafkaOutboxPublisher.java` | Background publisher with lease-based polling |
| `InboxRepository` (interface) | `sentinel-application/.../messaging/InboxRepository.java` | Port for inbox persistence |
| `InboxRepositoryMyBatisAdapter` | `sentinel-persistence/.../messaging/InboxRepositoryMyBatisAdapter.java` | Idempotent consumer tracking |
| `KafkaNotificationConsumer` | `sentinel-messaging/.../KafkaNotificationConsumer.java` | Consumes notification.command.v1 |

### Resilience Properties

- **Business commit independence**: Kafka outage does not roll back successful business commits. Pending outbox rows remain retryable.
- **Idempotent consumer**: `inbox_event` table prevents duplicate side effects from duplicate Kafka deliveries.
- **Lease-based polling**: `APP_INSTANCE_ID` + `lease_expires_at` prevent duplicate outbox processing in multi-instance deployments.
- **Retry + DLQ**: Failed events go to `.retry` topic; repeated failures move to `.dlq` topic.

## Kafka Topics

| Topic | Purpose |
|---|---|
| `case.lifecycle.v1` | Case state transition events |
| `case.assignment.v1` | Case assignment events |
| `evidence.lifecycle.v1` | Evidence lifecycle events |
| `decision.lifecycle.v1` | Decision lifecycle events |
| `sanction.lifecycle.v1` | Sanction lifecycle events |
| `appeal.lifecycle.v1` | Appeal lifecycle events |
| `notification.command.v1` | Notification dispatch commands |
| `notification.result.v1` | Notification delivery results |
| `audit.integration.v1` | Audit events for external integration |

Source: `MessagingTopics.java` (`sentinel-application/.../messaging/`)

## Notification Consumer

```
Kafka: notification.command.v1
  &rarr; KafkaNotificationConsumer
    &rarr; inbox_event deduplication
    &rarr; NotificationCommandHandler (retry logic)
    &rarr; NotificationEmailSender (SMTP via Mailpit)
    &rarr; notification.result.v1 published
    &rarr; audit.integration.v1 published
```

Configuration: `NOTIFICATION_CONSUMER_GROUP_ID`, `NOTIFICATION_MAX_RETRIES`, `NOTIFICATION_FROM_EMAIL`, `NOTIFICATION_TO_EMAIL`.

## MinIO Evidence Storage

### Architecture

```
EvidenceApplicationService
  &rarr; EvidenceStoragePort (interface)
    &rarr; MinioEvidenceStorageAdapter
      &rarr; minioClient (internal operations)
      &rarr; presigningMinioClient (presigned URL generation)
```

**File:** `/sentinel-storage/src/main/java/.../storage/MinioEvidenceStorageAdapter.java`

### Two-Client Design

- `minioClient` &mdash; for bucket management, stat object, get object stream
- `presigningMinioClient` &mdash; separate client with explicit `us-east-1` region for presigned URL generation
- Split internal/public endpoints via `MinioEvidenceStorageAdapter(endpoint, publicEndpoint, accessKey, secretKey)` constructor

### Evidence Lifecycle

1. **Upload Session**: Authorized actor requests upload &rarr; presigned PUT URL returned
2. **Client Upload**: File uploaded directly to MinIO (server never proxies the bytes)
3. **Finalize**: Server verifies object existence, SHA-256 checksum, size, and content-type
4. **Download Session**: Authorized actor requests download &rarr; presigned GET URL returned (audit logged)
5. **Versioning**: Immutable versions; new upload = new version

### Configuration

| Env Var | Default | Description |
|---|---|---|
| `MINIO_ENDPOINT` | `http://localhost:9000` | Internal MinIO endpoint |
| `MINIO_PUBLIC_ENDPOINT` | `http://localhost:9000` | Public-facing endpoint for presigned URLs |
| `MINIO_EVIDENCE_BUCKET` | `sentinel-evidence` | Evidence bucket name |
| `EVIDENCE_UPLOAD_URL_TTL` | `PT15M` | Upload URL expiration (ISO-8601) |
| `EVIDENCE_DOWNLOAD_URL_TTL` | `PT10M` | Download URL expiration (ISO-8601) |

### Error Classification

- `NoSuchKey` / `NoSuchObject` &rarr; `EvidenceObjectMissingException`
- All other MinIO errors &rarr; `EvidenceStorageUnavailableException`

Source: `MinioEvidenceStorageAdapter.java`, runbook at `/docs/runbooks/minio-evidence-storage.md`.

## Observability

### Health Checks

`GET /health` returns composite status from `CompositeHealthStatusService`:

| Check | Implementation | Type |
|---|---|---|
| Database | `DatabaseDependencyHealthCheck` &mdash; `DataSource.getConnection().isValid(2)` | JDBC |
| Kafka / Redis / MinIO / Mailpit | `SocketDependencyHealthCheck` &mdash; TCP socket connect | Socket |
| Workflow Engine | `WorkflowDependencyHealthCheck` &mdash; engine readiness supplier | Supplier |

### Request Metrics

`RequestMetricsFilter` (Jersey filter) records:
- `HTTP_REQUEST_TOTAL` &mdash; per method+path+status
- `HTTP_REQUEST_ERROR_TOTAL` &mdash; error responses
- `HTTP_REQUEST_DURATION_MS` &mdash; request timing

`MetricsRecorder` interface has one in-memory implementation (`InMemoryMetricsRecorder`).

### Correlation Context

`CorrelationContext` manages SLF4J MDC-based correlation IDs. `sanitizeOrGenerate(candidate)` validates with regex or generates UUID.

Source: `/sentinel-observability/src/main/java/com/sentinel/enforcement/observability/`.

## Source Map

| Module | Key Paths |
|---|---|
| Messaging contracts | `/sentinel-application/src/main/java/.../application/messaging/` |
| Kafka runtime | `/sentinel-messaging/src/main/java/.../messaging/` |
| Messaging persistence | `/sentinel-persistence/src/main/java/.../persistence/messaging/` |
| Storage adapter | `/sentinel-storage/src/main/java/.../storage/MinioEvidenceStorageAdapter.java` |
| Observability | `/sentinel-observability/src/main/java/.../observability/` |
| DB changelog (messaging) | `/sentinel-persistence/src/main/resources/db/changelog/releases/0005-messaging.yaml` |
| DB changelog (evidence) | `/sentinel-persistence/src/main/resources/db/changelog/releases/0004-evidence.yaml` |
