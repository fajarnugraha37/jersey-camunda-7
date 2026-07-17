---
type: Runtime Concurrency
title: Concurrency and Asynchronous Processing
description: Threading and async processing model for the Sentinel Enforcement Platform — Grizzly request threads, MyBatis session management, background messaging threads, Camunda job executor, HikariCP pooling, transaction isolation, and outbox lease locking.
tags: [sentinel, runtime, concurrency, threading, async, mybatis, hikaricp, outbox, camunda, kafka, transactions]
---

# Concurrency and Asynchronous Processing

## Overview

The Sentinel Enforcement Platform uses a **thread-per-request** model for HTTP serving and dedicated **background daemon threads** for asynchronous outbox publishing and notification consumption. There are no virtual threads, structured concurrency, or application-level thread pools for business logic — concurrency is limited to the infrastructure layer.

| Concern | Mechanism | Source File |
|---|---|---|
| HTTP request serving | Grizzly NIO + Jersey — one thread per request | `ApplicationRuntime.java` |
| DB connection pooling | HikariCP (`DB_MAX_POOL_SIZE=12`) | `ApplicationRuntime.java` |
| Transaction propagation | `ThreadLocal<SqlSession>` via `MyBatisSessionContext` | `MyBatisSessionContext.java` |
| Async outbox publishing | Platform daemon thread polling DB → publishing Kafka | `MessagingRuntime.java`, `KafkaOutboxPublisher.java` |
| Async notification consumption | Platform daemon thread polling Kafka → processing inbox | `MessagingRuntime.java`, `KafkaNotificationConsumer.java` |
| Workflow timers/escalations | Camunda job executor thread pool | `WorkflowModule.java`, `WorkflowRuntime.java` |
| Outbox lease safety | `FOR UPDATE SKIP LOCKED` on PostgreSQL | `MessagingMyBatisMapper.java` |

## Grizzly HTTP Server — One Request Per Thread

The HTTP server is started via `GrizzlyHttpServerFactory.createHttpServer()` in `ApplicationRuntime.java` (line 366). Grizzly uses a non-blocking I/O (NIO) transport layer, but Jersey's default request processing invokes each JAX-RS resource method on a **single thread from Grizzly's worker thread pool**. There is no asynchronous request processing (`@Suspended`, `AsyncResponse`) in the application layer — every HTTP request occupies one thread for its full duration.

```java
// ApplicationRuntime.java — line 365-369
HttpServer server =
    GrizzlyHttpServerFactory.createHttpServer(
        URI.create("http://0.0.0.0:" + configuration.httpPort() + "/"),
        resourceConfig,
        false);
server.start();
```

**Key properties:**
- No `ServerProperties.ASYNC_SERVICE` is configured
- Request threads are managed by Grizzly's internal NIO transport thread pool
- Thread count is bounded by Grizzly's default worker pool (typically `Runtime.getRuntime().availableProcessors() * 2 + 1`)

## MyBatis Session Management — ThreadLocal Propagation

MyBatis SQL sessions are bound to the current thread using `ThreadLocal<SqlSession>` in `MyBatisSessionContext.java`:

```java
// MyBatisSessionContext.java — lines 5-20
final class MyBatisSessionContext {
  private static final ThreadLocal<SqlSession> CURRENT_SESSION = new ThreadLocal<>();

  static SqlSession currentSession() { return CURRENT_SESSION.get(); }
  static void bind(SqlSession session) { CURRENT_SESSION.set(session); }
  static void clear() { CURRENT_SESSION.remove(); }
}
```

The `MyBatisTransactionManager` (line 27) checks for an existing session before opening a new one. If a session is already bound, the calling code **participates in the existing transaction** (no nested session creation). This enables repository composition within a single unit of work:

```java
// MyBatisTransactionManager.java — lines 26-57
public <T> T required(TransactionOptions options, Supplier<T> work) {
  SqlSession currentSession = MyBatisSessionContext.currentSession();
  if (currentSession != null) {
    return work.get();  // participate in existing transaction
  }
  // open new session, bind to ThreadLocal, commit/rollback, clear
  try (SqlSession session = sqlSessionFactory.openSession(...)) {
    MyBatisSessionContext.bind(session);
    try {
      session.getConnection().setReadOnly(options.readOnly());
      T result = work.get();
      session.commit();
      return result;
    } catch (RuntimeException | Error exception) {
      session.rollback();
      throw exception;
    } finally {
      MyBatisSessionContext.clear();
    }
  }
}
```

## Background Threads — MessagingRuntime

The `MessagingRuntime` starts two background daemon threads using `Thread.ofPlatform()`:

### Outbox Publisher Thread

```
Thread name:  sentinel-outbox-publisher-<appInstanceId>
Type:         Daemon platform thread
Runnable:     KafkaOutboxPublisher.publishPendingBatch()
Poll cycle:   configurable via outboxPollInterval
```

Source: `MessagingRuntime.java` — lines 101-118

```java
Thread outboxThread =
    Thread.ofPlatform()
        .name("sentinel-outbox-publisher-" + suffix(configuration.appInstanceId()))
        .daemon(true)
        .start(() -> {
          while (running.get()) {
            try {
              outboxPublisher.publishPendingBatch();
            } catch (Exception exception) {
              LOGGER.warn("Outbox publisher loop failed...", exception);
            }
            sleep(configuration.outboxPollInterval());
          }
        });
```

The publisher thread:
1. Calls `outboxRepository.claimPending()` which uses `FOR UPDATE SKIP LOCKED` to lease a batch of pending outbox events (see below)
2. Serializes each `EventEnvelope` to JSON using `EventEnvelopeJsonCodec`
3. Sends each event to the appropriate Kafka topic via `KafkaProducer.send().get()` (synchronous per-event send)
4. Marks events as `PUBLISHED` on success, or `releaseForRetry()` with exponential backoff on failure
5. Loop repeats after `outboxPollInterval`

### Notification Consumer Thread

```
Thread name:  sentinel-notification-consumer-<appInstanceId>
Type:         Daemon platform thread
Runnable:     KafkaNotificationConsumer.run()
Poll cycle:   500ms Kafka poll timeout
```

Source: `MessagingRuntime.java` — lines 119-123, `KafkaNotificationConsumer.java` — lines 54-70

```java
Thread consumerThread =
    Thread.ofPlatform()
        .name("sentinel-notification-consumer-" + suffix(configuration.appInstanceId()))
        .daemon(true)
        .start(() -> notificationConsumer.run(running));
```

The consumer thread:
1. Subscribes to notification projection topics and `NOTIFICATION_COMMAND` topic (plus their `.retry` variants)
2. Polls Kafka with a 500ms timeout
3. Deserializes each `ConsumerRecord` into an `EventEnvelope`
4. Routes to `NotificationEventHandler` (domain lifecycle events) or `NotificationCommandHandler` (email send commands)
5. Commits offsets synchronously after successful processing
6. On failure, routes to `.retry` or `.dlq` topic based on retry attempt count

## Workflow Runtime — Camunda Job Executor

The `WorkflowRuntime` wraps an embedded Camunda process engine configured in `WorkflowModule.java`:

```java
// WorkflowModule.java — lines 35-50
StandaloneProcessEngineConfiguration configuration = new StandaloneProcessEngineConfiguration();
configuration.setProcessEngineName(engineName);
configuration.setDataSource(dataSource);
configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);
configuration.setHistory(ProcessEngineConfiguration.HISTORY_FULL);
configuration.setJobExecutorActivate(true);  // enables job executor thread pool
configuration.setJdbcBatchProcessing(true);
configuration.setExpressionManager(new JuelExpressionManager(Map.of(
    "preTriageRoutingDelegate", preTriageRoutingDelegate,
    "investigationEscalationDelegate", escalationDelegate,
    "mockWorkflowServiceDelegate", mockWorkflowServiceDelegate
)));
```

**`setJobExecutorActivate(true)`** starts Camunda's internal job executor — a thread pool that processes:
- **Timer boundary events** (e.g., investigation escalation after a duration)
- **Asynchronous continuations** after service task execution
- **Escalation triggers** defined in BPMN

The Camunda job executor manages its own thread pool (separate from Grizzly and messaging threads). Thread pool sizing follows Camunda defaults unless overridden via process engine configuration.

## HikariCP Connection Pool — DB_MAX_POOL_SIZE=12

The HikariCP connection pool is configured in `ApplicationRuntime.java` — lines 426-442:

```java
private static HikariDataSource createDataSource(AppConfiguration configuration) {
  HikariConfig hikariConfig = new HikariConfig();
  hikariConfig.setJdbcUrl(configuration.dbUrl());
  hikariConfig.setUsername(configuration.dbUsername());
  hikariConfig.setPassword(configuration.dbPassword());
  hikariConfig.setMaximumPoolSize(configuration.dbMaxPoolSize());  // DB_MAX_POOL_SIZE=12
  hikariConfig.setMinimumIdle(Math.min(2, configuration.dbMaxPoolSize()));
  hikariConfig.setConnectionTimeout(10000);
  hikariConfig.setValidationTimeout(2000);
  hikariConfig.setInitializationFailTimeout(5000);
  hikariConfig.setLeakDetectionThreshold(30000);
  hikariConfig.setConnectionInitSql("SET statement_timeout = '10s'");
  hikariConfig.addDataSourceProperty("connectTimeout", "5");
  hikariConfig.addDataSourceProperty("socketTimeout", "30");
  hikariConfig.setPoolName("sentinel-hikari");
  return new HikariDataSource(hikariConfig);
}
```

**Pool characteristics:**
- **Maximum pool size:** 12 (`DB_MAX_POOL_SIZE`)
- **Minimum idle:** `min(2, DB_MAX_POOL_SIZE)`
- **Connection timeout:** 10 seconds
- **Leak detection threshold:** 30 seconds
- **Statement timeout:** 10 seconds (set via `connectionInitSql`)
- **Socket timeout:** 30 seconds
- **Pool name:** `sentinel-hikari`

The same data source is shared across:
- Grizzly request threads (via `MyBatisTransactionManager`)
- Outbox publisher thread (via `OutboxRepositoryMyBatisAdapter`)
- Notification consumer thread (via `InboxRepositoryMyBatisAdapter` / `NotificationRepositoryMyBatisAdapter`)
- Camunda workflow engine (camunda owns its own connection usage through the same datasource)

## Transaction Isolation Levels

Transaction isolation is configured via the `TransactionIsolation` enum:

```java
// TransactionIsolation.java
public enum TransactionIsolation {
  READ_COMMITTED,
  REPEATABLE_READ,
  SERIALIZABLE
}
```

Mapped to MyBatis equivalents in `MyBatisTransactionManager.toMyBatisIsolationLevel()` (lines 59-65):

```java
private TransactionIsolationLevel toMyBatisIsolationLevel(TransactionIsolation isolation) {
  return switch (isolation) {
    case READ_COMMITTED -> TransactionIsolationLevel.READ_COMMITTED;
    case REPEATABLE_READ -> TransactionIsolationLevel.REPEATABLE_READ;
    case SERIALIZABLE -> TransactionIsolationLevel.SERIALIZABLE;
  };
}
```

- **Default:** `TransactionOptions.defaultWrite()` uses `READ_COMMITTED` for standard write transactions
- **Read-only transactions:** Pass `readOnly = true` to skip commit overhead and enable PostgreSQL read-only optimizations

## Outbox Lease Locking — FOR UPDATE SKIP LOCKED

Multi-instance safety for the outbox publisher is achieved through PostgreSQL row-level locking:

```sql
-- MessagingMyBatisMapper.java — lines 73-121
WITH claimed AS (
  SELECT event_id
  FROM outbox_event
  WHERE status = 'PENDING'
    AND available_at <= #{now}
    AND (lease_expires_at IS NULL OR lease_expires_at <= #{now})
  ORDER BY occurred_at, event_id
  FOR UPDATE SKIP LOCKED
  LIMIT #{batchSize}
)
UPDATE outbox_event o
SET
  lease_owner = #{leaseOwner},
  lease_expires_at = #{leaseExpiresAt},
  ...
FROM claimed
WHERE o.event_id = claimed.event_id
RETURNING ...;
```

**Design properties:**
- **`FOR UPDATE SKIP LOCKED`** — locks only the rows selected, skipping rows already locked by another instance. This allows multiple application instances to safely claim different batches concurrently
- **Lease-based claim** — each row is claimed with a `lease_owner` and `lease_expires_at` timestamp. If the publisher crashes, the lease expires and another instance can reclaim the row
- **Batch ordering** — rows are claimed in `occurred_at, event_id` order to preserve event ordering guarantees
- **Atomic claim+publish** — the CTE claims and updates in a single SQL statement, returning the full row data via `RETURNING`

## Thread Model Summary

```
┌──────────────────────────────────────────────────────────┐
│                   Application Process                     │
│                                                          │
│  ┌──────────────────┐    ┌───────────────────────────┐   │
│  │  Grizzly Worker   │    │  Background Daemon Threads │   │
│  │  Thread Pool      │    │                           │   │
│  │                   │    │  ┌─────────────────────┐  │   │
│  │  Thread-1 ────►   │    │  │ Outbox Publisher    │  │   │
│  │  HTTP Request     │    │  │ (platform thread)   │  │   │
│  │                   │    │  └─────────┬───────────┘  │   │
│  │  Thread-2 ────►   │    │            │ polls DB     │   │
│  │  HTTP Request     │    │            ▼              │   │
│  │                   │    │  ┌─────────────────────┐  │   │
│  │  ...              │    │  │ KafkaProducer       │  │   │
│  │                   │    │  └─────────────────────┘  │   │
│  └──────────────────┘    │                           │   │
│                          │  ┌─────────────────────┐  │   │
│                          │  │ Notification Consumer│  │   │
│                          │  │ (platform thread)   │  │   │
│  ┌──────────────────┐    │  └─────────┬───────────┘  │   │
│  │ Camunda Job       │    │            │ polls Kafka  │   │
│  │ Executor Pool     │    │            ▼              │   │
│  │ (timers,          │    │  ┌─────────────────────┐  │   │
│  │  escalations)     │    │  │ KafkaConsumer       │  │   │
│  └──────────────────┘    │  └─────────────────────┘  │   │
│                          │                           │   │
│  ┌──────────────────┐    │  ┌─────────────────────┐  │   │
│  │ HikariCP Pool     │    │  │ ThreadLocal<SqlSession>│ │   │
│  │ (12 connections)  │◄───┼──┤ per-thread binding  │  │   │
│  └──────────────────┘    │  └─────────────────────┘  │   │
└──────────────────────────────────────────────────────────┘
```

## Source Files

| File | Role |
|---|---|
| `sentinel-bootstrap/.../ApplicationRuntime.java` | HTTP server startup, HikariCP config, runtime wiring |
| `sentinel-messaging/.../MessagingRuntime.java` | Background thread lifecycle management |
| `sentinel-messaging/.../KafkaOutboxPublisher.java` | Outbox polling and publishing logic |
| `sentinel-messaging/.../KafkaNotificationConsumer.java` | Kafka consumer loop with retry/DLQ routing |
| `sentinel-persistence/.../MyBatisSessionContext.java` | `ThreadLocal<SqlSession>` binding |
| `sentinel-persistence/.../MyBatisTransactionManager.java` | Transaction lifecycle with isolation levels |
| `sentinel-persistence/.../messaging/MessagingMyBatisMapper.java` | `FOR UPDATE SKIP LOCKED` outbox claim SQL |
| `sentinel-application/.../messaging/TransactionIsolation.java` | Isolation level enum |
| `sentinel-workflow/.../WorkflowModule.java` | Camunda engine config with job executor |
| `sentinel-workflow/.../WorkflowRuntime.java` | Workflow runtime wrapper |
