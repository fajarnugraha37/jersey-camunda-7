---
type: Asynchronous Processing
title: Asynchronous Processing Model
description: The Sentinel Enforcement Platform's asynchronous processing model — an intentionally synchronous monolith using hand-rolled daemon threads for transactional outbox publishing and Kafka consumption, with Camunda's internal job executor for BPMN timer/async continuations.
tags: [sentinel, runtime, async, threading, outbox, kafka, camunda, blocking-io]
---

# Asynchronous Processing

## Asynchronous Processing Scope

```mermaid
flowchart TB
    subgraph HTTP[HTTP Request Thread]
        A[JAX-RS Resource] --> B[ApplicationService]
        B --> C[Domain Logic]
        C --> D[Repository<br/>MyBatis SELECT/UPDATE]
        C --> E[OutboxRepository<br/>INSERT into outbox_event]
    end

    subgraph Outbox[Outbox Publisher - Daemon Thread]
        F[KafkaOutboxPublisher<br/>poll loop] --> G[SELECT ... FOR UPDATE<br/>SKIP LOCKED]
        G --> H[Kafka producer.send()]
        H --> I[MARK PUBLISHED]
    end

    subgraph KafkaConsumer[Notification Consumer - Daemon Thread]
        J[KafkaNotificationConsumer<br/>poll loop] --> K[consumer.poll()]
        K --> L[INSERT into inbox_event<br/>idempotent dedup]
        L --> M[KafkaNotificationHandler]
        M --> N[Email dispatch<br/>Jakarta Mail]
    end

    subgraph Camunda[Camunda Job Executor - Internal Thread Pool]
        O[BPMN Timer Event] --> P[JobExecutor<br/>acquisition loop]
        P --> Q[Execute JavaDelegate]
    end

    E -.->|polls outbox_event table| F
    E -.->|Kafka topic| J
    B -.->|start/ signal| O

    style A stroke:#333,stroke-width:2px
    style F stroke:#666,stroke-width:1px
    style J stroke:#666,stroke-width:1px
    style O stroke:#666,stroke-width:1px
```

The Sentinel Enforcement Platform is designed as an **intentionally synchronous modular monolith**. All application logic — REST request handling, domain operations, persistence — runs on the calling thread. There is:

- **No** `ExecutorService`, thread pool abstraction, or scheduled task framework
- **No** reactive programming (no Project Reactor, RxJava, or WebFlux)
- **No** `CompletableFuture` or `@Async` annotations
- **No** coroutines or virtual-thread-per-task model (JDK 21 platform threads are used but not with structured concurrency)
- **No** `@Scheduled` or cron scheduler

The only asynchronous boundaries are:

1. **Transactional outbox publisher** — a hand-rolled daemon thread that polls the `outbox_event` table and publishes events to Kafka
2. **Kafka notification consumer** — a hand-rolled daemon thread that polls Kafka topics and dispatches to inbox/notification handlers
3. **Camunda BPM job executor** — the embedded workflow engine's internal thread pool for BPMN timer events and asynchronous continuations

All I/O is blocking: JDBC database calls, Kafka sync `send()` with `Future#get()`, SMTP email delivery via Jakarta Mail, and MinIO HTTP calls.

**Source evidence:**
- `/sentinel-messaging/src/main/java/com/sentinel/enforcement/messaging/KafkaOutboxPublisher.java`
- `/sentinel-messaging/src/main/java/com/sentinel/enforcement/messaging/KafkaNotificationConsumer.java`
- `/sentinel-messaging/src/main/java/com/sentinel/enforcement/messaging/MessagingRuntime.java`
- `/sentinel-workflow/src/main/java/com/sentinel/enforcement/workflow/CaseWorkflowAdapter.java`
- No `ExecutorService` import found in any sentinel-module source. Verified via `rg "ExecutorService|ThreadPool|CompletableFuture|@Async|@Scheduled|Reactive|Flux|Mono|RxJava"` returning zero matches in application/domain/persistence/workflow code.

## Async Flow Inventory

| Async Flow | Trigger | Mechanism | Queue or Scheduler | Ownership | Backpressure | Retry or Error | Cancellation | Durability | Evidence |
|---|---|---|---|---|---|---|---|---|---|
| Outbox publishing | `OutboxRepository.enqueue()` → `KafkaOutboxPublisher` loop | Daemon thread (`Thread.ofPlatform().daemon(true)` polls DB table `SELECT ... FOR UPDATE SKIP LOCKED` | PostgreSQL `outbox_event` table with lease mechanism | `sentinel-messaging` | Lease-based (lease expiry prevents duplicate claim) | Yes — `publish_attempts` counter, `last_error` column, `NOTIFICATION_MAX_RETRIES` env var, dead-letter via `MessagingRuntime` | Via lease expiry (orphaned events are reclaimed) | Transactional outbox pattern — events written in same DB transaction as aggregate changes | `KafkaOutboxPublisher.java`, `MessagingMyBatisMapper.java` |
| Kafka notification consumption | Kafka consumer poll loop | Daemon thread (`Thread.ofPlatform().daemon(true)` polls Kafka `sentinel-notification` topic | Kafka consumer group rebalance + `inbox_event` table for idempotency | `sentinel-messaging` | Kafka consumer poll bounded by `max.poll.records` (default 500) | Yes — inbox deduplication (`uk_inbox_event_consumer_event`), unprocessed events remain in inbox | Via consumer group rebalance (uncommitted offsets are redelivered) | Idempotent inbox pattern — events stored in `inbox_event` table with unique constraint | `KafkaNotificationConsumer.java`, `InboxRepositoryMyBatisAdapter.java` |
| Camunda job execution | BPMN timer event or async continuation | Camunda internal `JobExecutor` thread pool (opaque, configurable via `camunda.cfg`) | Camunda `ACT_RU_JOB` table | `sentinel-workflow` (embedded Camunda engine) | Configurable `maxJobsPerAcquisition` (default 3) | Yes — Camunda retry with `RETRIES_` column, configurable `retryTimeCycle` in BPMN | Via Camunda incident handling (manual or programmatic) | BPMN engine persistent state — `ACT_RU_EXECUTION`, `ACT_RU_JOB` tables | `CamundaConfiguration.java`, BPMN files at `/sentinel-workflow/src/main/resources/bpmn/` |

## Futures Promises Callbacks and Coroutines

**Not Observed.** The Sentinel codebase does not use `CompletableFuture`, `FutureTask`, `Promise`, callback interfaces, or Java 21 virtual threads/virtual-thread builders in application code.

The only exception is the Kafka producer `send()` call, which returns a `Future<RecordMetadata>` on which `.get()` is called synchronously (blocking wait). No callback or chaining pattern is used:

```java
// KafkaOutboxPublisher.java — blocking send with get()
producer.send(producerRecord).get(5, TimeUnit.SECONDS);
```

**Evidence:** Verified via repository-wide search — zero uses of `CompletableFuture`, `FutureTask`, `CompletionStage`, `callable`, or virtual-thread builder imports outside test dependencies.

## Reactive Pipelines and Event Loops

**Not Observed.** No reactive streams (`Publisher`, `Subscriber`, `Subscription`), no Project Reactor (`Flux`, `Mono`), no RxJava (`Observable`, `Single`), and no event-loop framework (Netty, Vert.x, Grizzly async I/O are not used). The Grizzly HTTP server is configured for blocking request handling.

## Local Queues Background Tasks and Detached Work

### Outbox Publisher (Background Daemon Thread)

The `KafkaOutboxPublisher` runs a continuous loop on a single daemon thread:

```java
// KafkaOutboxPublisher.java (context)
Thread publisherThread = Thread.ofPlatform()
    .daemon(true)
    .name("outbox-publisher")
    .start(() -> {
        while (!Thread.interrupted()) {
            try {
                // 1. Claim pending events: SELECT ... FROM outbox_event
                //    WHERE status = 'PENDING' AND available_at <= now()
                //    ORDER BY occurred_at LIMIT 20 FOR UPDATE SKIP LOCKED
                // 2. For each event: serialize, send to Kafka, mark PUBLISHED
                // 3. Sleep POLL_INTERVAL_MS
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    });
```

- Batch size: 20 events per poll cycle
- Poll interval: configurable via `OUTBOX_POLL_INTERVAL_MS` (default 1000ms)
- Lease mechanism prevents duplicate processing across restarts
- Loss of MDC context (see [context-propagation.md](context-propagation.md))

### Kafka Notification Consumer (Background Daemon Thread)

The `KafkaNotificationConsumer` runs a continuous poll loop on a single daemon thread:

```java
// KafkaNotificationConsumer.java (context)
Thread consumerThread = Thread.ofPlatform()
    .daemon(true)
    .name("notification-consumer")
    .start(() -> {
        while (!Thread.interrupted()) {
            // 1. consumer.poll(Duration.ofSeconds(1))
            // 2. For each record: idempotent insert into inbox_event,
            //    then dispatch to KafkaNotificationHandler
            // 3. consumer.commitSync()
        }
    });
```

**Source:** `KafkaNotificationConsumer.java`, `InboxRepositoryMyBatisAdapter.java`

### Maintenance Operations (Synchronous On-Demand)

The `MaintenanceOperationApplicationService` executes bulk database operations synchronously on the calling thread. There is no background scheduler for these operations — they are triggered via the REST endpoint `POST /api/v1/operations/recalculate-overdue-sanctions`. See [job-catalog.md](/openwiki/processing/job-catalog.md).

## Ownership Lifecycle and Durability

| Component | Created By | Destroyed By | Durability Guarantee |
|---|---|---|---|
| Outbox publisher thread | `MessagingRuntime.start()` | `MessagingRuntime.stop()` (daemon thread — JVM exit kills it) | Events durable in PostgreSQL outbox table before Kafka publish |
| Kafka consumer thread | `MessagingRuntime.start()` | `MessagingRuntime.stop()` (daemon thread — JVM exit kills it) | Consumer offsets committed after processing; inbox_event provides idempotent dedup |
| Camunda job executor | Camunda engine startup (inside `ProcessEngineConfiguration`) | Camunda engine shutdown | BPMN state persisted in ACT_* tables |

**Key observation:** The outbox publisher and Kafka consumer are daemon threads. If the JVM exits before they complete a publish/consume cycle, in-flight events may remain in `PENDING` status (outbox) or be re-delivered on next consumer group rebalance (Kafka).

**Source:** `MessagingRuntime.java`

## Backpressure Queue Limits and Rejection

| Flow | Backpressure Mechanism | Limit | Rejection Behaviour |
|---|---|---|---|
| Outbox publishing | Lease-based — `FOR UPDATE SKIP LOCKED` + lease expiry prevents double-claim | 20 events per poll cycle, 1 thread | Events remain `PENDING` in outbox table; next poll cycle retries |
| Kafka consumption | `max.poll.records` consumer config | 500 records per poll | Uncommitted offsets → redelivery on rebalance |
| Camunda job execution | `maxJobsPerAcquisition` | 3 jobs per acquisition cycle (default) | Jobs remain in `ACT_RU_JOB` table for next acquisition |
| REST API request handling | Grizzly HTTP worker thread pool (default) | Opaque — configured via Grizzly/Jersey | HTTP 503 or connection refusal at the network level |

There is no application-level backpressure (reactive streams, bounded queues, or circuit breakers).

**Source:** `MessagingRuntime.java`, `KafkaOutboxPublisher.java`, Camunda configuration in `/sentinel-workflow/src/main/java/`

## Errors Retry Cancellation and Timeouts

### Outbox Publishing Retry
- `publish_attempts` column tracks retry count per event
- `NOTIFICATION_MAX_RETRIES` environment variable (default 3) caps retry attempts
- `last_error` column records the last failure reason
- Events exceeding max retries are **not automatically dead-lettered** — they remain in `PENDING` status with `last_error` set; the `MessagingRuntime` exposes a `moveToDeadLetter()` method
- Kafka `send()` has a 5-second timeout: `producer.send(record).get(5, TimeUnit.SECONDS)`

### Kafka Consumer Retry
- Consumer polls with `Duration.ofSeconds(1)` timeout
- Failed message processing does not commit the offset; next poll cycle re-fetches
- Persistent failures stall the consumer — no circuit breaker
- `inbox_event` deduplication prevents duplicate side-effects

### Camunda Job Retry
- Camunda internal retry mechanism with `RETRIES_` column on `ACT_RU_JOB`
- `retryTimeCycle` configured per BPMN element
- Exhausted retries create an **incident** (visible in Camunda Cockpit and `ACT_RU_INCIDENT`)
- `WorkflowReconciliationApplicationService` can identify and resolve stuck workflow instances

### Timeouts
- Kafka producer `send()`: 5 seconds for metadata response
- Outbox poll interval: 1 second (configurable via `OUTBOX_POLL_INTERVAL_MS`)
- No application-level timeout for database queries (rely on PostgreSQL `statement_timeout` and `lock_timeout` if configured at the connection level)

**Source:** `MessagingRuntime.java`, `KafkaOutboxPublisher.java`, Camunda configuration

## Shutdown Process Death and Recovery

### Graceful Shutdown
The `ApplicationRuntime` class in `sentinel-bootstrap` registers a JVM shutdown hook:

```java
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    // 1. MessagingRuntime.stop() → interrupt daemon threads
    // 2. Camunda ProcessEngine.close() → release DB connections
    // 3. HikariDataSource.close() → drain connection pool
}));
```

**Source:** `/sentinel-bootstrap/src/main/java/com/sentinel/enforcement/bootstrap/ApplicationRuntime.java`

### Process Death Recovery
- **Outbox events:** On restart, `KafkaOutboxPublisher` claims all `PENDING` events where `available_at <= now()` and lease has expired. No data loss — events are durable in the DB table.
- **Kafka consumer:** On restart, consumer group rebalancing triggers re-delivery of uncommitted offsets. The `inbox_event` unique constraint prevents duplicate processing.
- **Camunda jobs:** On engine restart, Camunda re-acquires jobs from `ACT_RU_JOB` where retries remain. Jobs with exhausted retries remain as incidents.
- **In-flight transactions:** If the JVM crashes mid-transaction, the database auto-rolls back uncommitted changes (ACID). No application-level compensation is implemented.

### Daemon Thread Behaviour
Both messaging threads are daemon threads: `Thread.ofPlatform().daemon(true)`. The JVM exits when only daemon threads remain. This means:
- The JVM can exit while an outbox publish or Kafka consume cycle is in progress
- In-flight operations are not awaited during shutdown
- The shutdown hook attempts clean stop, but `Thread.interrupt()` on a daemon thread in a blocking I/O call may not be immediate

**Source:** `KafkaOutboxPublisher.java`, `KafkaNotificationConsumer.java`, `ApplicationRuntime.java`

## Asynchronous Processing Catalog

| Async Flow | Trigger | Mechanism | Queue or Scheduler | Ownership | Backpressure | Retry or Error | Cancellation | Durability | Evidence |
|---|---|---|---|---|---|---|---|---|---|
| Outbox publishing | `OutboxRepository.enqueue()` | Daemon thread polls `outbox_event` with `FOR UPDATE SKIP LOCKED` | PostgreSQL `outbox_event` table, lease-based | `sentinel-messaging` | Lease-based (lease expiry prevents duplicate claim) | `publish_attempts` counter, `NOTIFICATION_MAX_RETRIES`, `last_error` column, manual dead-letter | Lease expiry reclaims orphaned events | Transactional outbox pattern — events durable in DB | `KafkaOutboxPublisher.java`, `MessagingMyBatisMapper.java` |
| Kafka notification consumption | Kafka consumer poll | Daemon thread polls `sentinel-notification` topic | Kafka consumer group + `inbox_event` dedup table | `sentinel-messaging` | `max.poll.records=500` | Inbox dedup via `uk_inbox_event_consumer_event`, unprocessed events persist in inbox | Consumer group rebalance redelivers | Idempotent inbox pattern | `KafkaNotificationConsumer.java`, `InboxRepositoryMyBatisAdapter.java` |
| Camunda job execution | BPMN timer / async continuation | Camunda internal `JobExecutor` thread pool | `ACT_RU_JOB` table | `sentinel-workflow` | `maxJobsPerAcquisition=3` | Camunda retry with `RETRIES_`, `retryTimeCycle` in BPMN, incident on exhaustion | Camunda incident handling | BPMN engine persistent state | `CamundaConfiguration.java`, BPMN files at `sentinel-workflow/src/main/resources/bpmn/` |

## Knowledge Gaps

1. **Grizzly HTTP worker thread pool configuration** — The Grizzly server is configured in `ApplicationRuntime.java` but specific thread pool size, queue depth, and rejection policy are opaque. Environment variables or defaults not documented.
2. **Camunda job executor thread pool size** — The Camunda `JobExecutor` uses a default thread pool; its exact configuration and max thread count are not observable from the codebase without running the application.
3. **No application-level health checks for messaging threads** — There is no liveness probe for the outbox publisher or Kafka consumer threads beyond the composite health check (which checks Kafka broker connectivity, not consumer thread health).
4. **Dead-letter routing is manual** — Events exceeding max retries are not automatically routed; they remain in `PENDING` with `last_error` set, and a manual `moveToDeadLetter()` method must be called.
5. **No metrics for async backpressure** — There are no Micrometer/prometheus metrics for outbox queue depth, consumer lag, or job executor queue depth beyond what Camunda exposes internally.

## Source References

| File | Module | Role |
|---|---|---|
| `KafkaOutboxPublisher.java` | `sentinel-messaging` | Outbox polling daemon thread |
| `KafkaNotificationConsumer.java` | `sentinel-messaging` | Kafka consumer daemon thread |
| `MessagingRuntime.java` | `sentinel-messaging` | Lifecycle start/stop for messaging threads |
| `MessagingMyBatisMapper.java` | `sentinel-persistence` | SQL for outbox/inbox operations, `FOR UPDATE SKIP LOCKED` |
| `InboxRepositoryMyBatisAdapter.java` | `sentinel-persistence` | Inbox deduplication and persistence |
| `OutboxRepositoryMyBatisAdapter.java` | `sentinel-persistence` | Outbox enqueue and claim |
| `CamundaConfiguration.java` | `sentinel-workflow` | Camunda engine configuration |
| `CaseWorkflowAdapter.java` | `sentinel-workflow` | Camunda workflow integration |
| `ApplicationRuntime.java` | `sentinel-bootstrap` | Shutdown hook, server startup |
| `pom.xml` (parent) | root | Dependency versions — no reactive libs |
