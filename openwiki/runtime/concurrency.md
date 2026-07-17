---
type: Architecture
title: Concurrency
description: Concurrency model for the Sentinel Enforcement Platform — optimistic locking, outbox lease locking, Camunda job executor threading, Grizzly thread pool, and multi-instance safety guarantees.
tags: [sentinel, runtime, concurrency, locking, threads, optimistic]
---

# Concurrency

The Sentinel Enforcement Platform uses several concurrency control mechanisms across the domain, persistence, messaging, and workflow layers. No thread pools beyond the Grizzly HTTP worker pool (opaque, configured by default), the background messaging daemon threads, and the embedded Camunda job executor thread pool are used.

## Optimistic Concurrency Control

Every domain aggregate root carries a `version` field that enforces optimistic locking:

- **Aggregates with version fields:** `Report`, `CaseRecord`, `Evidence`, `EvidenceUploadSession`, `Recommendation`, `Decision`, `Appeal`, `Sanction`
- **Mechanism:** The domain `validateExpectedVersion()` method compares the expected version (passed from the client or derived from the read) with the aggregate's current version. On mismatch, a `*ConflictException` is thrown.
- **Persistence:** MyBatis mappers include `WHERE id = #{id} AND version = #{expectedVersion}` in UPDATE statements. If the row count is zero, the adapter throws a `ConflictException`.
- **HTTP mapping:** `ConflictExceptionMapper` maps domain conflict exceptions to HTTP 409 Conflict responses.
- **Scope:** Each aggregate is versioned independently. There are no cross-aggregate version checks or distributed locks.

**Source:** `sentinel-domain/src/main/java/.../domain/casefile/CaseRecord.java` (version field), `sentinel-persistence/src/main/java/.../persistence/CaseMyBatisMapper.java` (version in WHERE clause)

## Outbox Lease Locking

The outbox publisher uses database-level pessimistic locking to prevent duplicate outbox processing in multi-instance deployments:

- **Lock type:** `SELECT ... FROM outbox_event WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT ? FOR UPDATE SKIP LOCKED`
- **Lease mechanism:** Once locked, the row status transitions to `CLAIMED`. After successful Kafka publish, status becomes `PUBLISHED`.
- **Retry backoff:** Configurable via `OUTBOX_POLL_INTERVAL_MS` and `OUTBOX_BATCH_SIZE` environment variables.
- **Dead-letter:** Rows that exceed the retry threshold transition to `FAILED` status.

**Source:** `sentinel-persistence/src/main/java/.../persistence/OutboxRepositoryMyBatisAdapter.java`, `sentinel-messaging/src/main/java/.../messaging/KafkaOutboxPublisher.java`

## Camunda Job Executor Concurrency

The embedded Camunda 7 process engine manages its own internal thread pool for executing BPMN jobs asynchronously:

- **Configuration:** Defined in `CamundaConfiguration.java`. Default thread pool settings apply.
- **Job types:** Timer boundary events, asynchronous continuations, escalation timers.
- **Synchronization:** Workflow state is reconciled with domain state via `WorkflowReconciliationApplicationService`, not via direct synchronous two-phase commit.
- **Readiness:** A `WorkflowReadinessProbe` verifies the process engine has started before accepting task queries.

**Source:** `sentinel-workflow/src/main/java/.../workflow/CamundaConfiguration.java`, `sentinel-workflow/src/main/java/.../workflow/WorkflowRuntime.java`

## Grizzly HTTP Worker Thread Pool

The Grizzly HTTP server manages an internal worker thread pool for processing HTTP requests:

- **Configuration:** Default Grizzly settings; no custom thread pool configuration is applied in `ApplicationRuntime`.
- **Thread model:** One request thread per connection. Long-running or blocking operations (e.g., database queries, Kafka publishes) occupy request threads.
- **Known gap:** The Grizzly thread pool size, queue depth, and rejection behavior are not explicitly configured by the application.

## Database Connection Pooling

HikariCP manages the PostgreSQL connection pool used by MyBatis:

- **Configuration:** `DATA_SOURCE_MAX_POOL_SIZE` (default 20), `DATA_SOURCE_MIN_IDLE` (default 5).
- **Transaction lifecycle:** `MyBatisTransactionManager` opens a session on the current thread for each application service call. The session is committed or rolled back, then closed.
- **Thread safety:** `MyBatisSessionContext` holds the session in a `ThreadLocal`. Each request thread gets its own session.

**Source:** `sentinel-bootstrap/src/main/java/.../bootstrap/AppConfiguration.java`, `sentinel-persistence/src/main/java/.../persistence/MyBatisTransactionManager.java`, `sentinel-persistence/src/main/java/.../persistence/MyBatisSessionContext.java`

## Multi-Instance Safety

The platform is designed to run as a single instance. Key observations:

- **Outbox `FOR UPDATE SKIP LOCKED`** — This locking mechanism would allow safe multi-instance outbox processing, but no multi-instance deployment is configured.
- **Optimistic locking** — Prevents data corruption if two instances attempt to modify the same aggregate simultaneously; the second instance receives HTTP 409.
- **Camunda embedded** — The embedded Camunda engine co-locates with the application JVM. Multiple instances would each run their own engine, sharing the same database tables. Camunda's internal locking handles job execution coordination.
- **No distributed cache or session replication** — Redis is available in the Docker Compose stack but is not currently used.

## Knowledge Gaps

- Grizzly HTTP thread pool is unconfigured; default settings are used. Production sizing guidelines are unknown.
- The platform has not been tested in a multi-instance deployment configuration.
- Camunda job executor thread pool defaults are not documented.

## Source References

- `sentinel-domain/src/main/java/.../domain/casefile/CaseRecord.java` — Version field for optimistic locking
- `sentinel-persistence/src/main/java/.../persistence/CaseMyBatisMapper.java` — WHERE version in UPDATE
- `sentinel-persistence/src/main/java/.../persistence/OutboxRepositoryMyBatisAdapter.java` — FOR UPDATE SKIP LOCKED
- `sentinel-messaging/src/main/java/.../messaging/KafkaOutboxPublisher.java` — Outbox polling and leasing
- `sentinel-workflow/src/main/java/.../workflow/CamundaConfiguration.java` — Camunda job executor settings
- `sentinel-workflow/src/main/java/.../workflow/WorkflowRuntime.java` — Process engine runtime management
- `sentinel-bootstrap/src/main/java/.../bootstrap/AppConfiguration.java` — HikariCP and runtime settings
- `sentinel-persistence/src/main/java/.../persistence/MyBatisTransactionManager.java` — Transaction lifecycle
- `sentinel-persistence/src/main/java/.../persistence/MyBatisSessionContext.java` — ThreadLocal session management
- `sentinel-bootstrap/src/main/java/.../bootstrap/ApplicationRuntime.java` — Grizzly server startup, wiring
- `/openwiki/runtime/asynchronous-processing.md` — Threading model for background jobs
- `/openwiki/data/consistency.md` — Data consistency mechanisms
