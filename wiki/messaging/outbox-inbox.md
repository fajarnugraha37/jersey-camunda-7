# Transactional Outbox & Inbox Idempotency

## Architecture

```
Domain Change (within DB transaction)
    │
    ▼
outbox_event table ← INSERT event (same transaction)
    │
    ▼ (daemon thread polls)
KafkaOutboxPublisher
    │
    ├─ claimPending() → FOR UPDATE SKIP LOCKED
    ├─ serialize to JSON
    ├─ producer.send().get()  (synchronous)
    ├─ markPublished() OR releaseForRetry()
    │
    ▼
Kafka Topic
    │
    ▼ (consumer polls)
KafkaNotificationConsumer
    │
    ├─ deserialize JSON
    ├─ inboxRepository.beginProcessing()  ← idempotency check
    ├─ handle event (business logic)
    ├─ commit offset
    └─ completeProcessing()
```

---

## Outbox Pattern

### Why?
Kafka broker outage must NOT roll back domain changes. The outbox ensures **atomicity**: business commit + event enqueue happen in the same database transaction.

### OutboxEvent Record

| Field | Description |
|-------|-------------|
| `eventId` | UUID primary key |
| `topic` | Target Kafka topic |
| `messageKey` | Kafka record key |
| `envelope` | `EventEnvelope` (eventType, aggregateId, payload, etc.) |
| `status` | PENDING or PUBLISHED |
| `availableAt` | Earliest time for publication |
| `leaseOwner` | Publisher instance that claimed this event |
| `leaseExpiresAt` | When the lease expires |
| `publishAttempts` | Incremented on each attempt |
| `lastError` | Last failure reason (truncated to 500 chars) |

### KafkaOutboxPublisher

**Thread:** Single daemon thread, polls on configurable interval (`OUTBOX_POLL_INTERVAL`, default PT2S)

**Batch flow:**
1. `claimPending()` — atomically leases N rows with `FOR UPDATE SKIP LOCKED`
2. For each claimed event:
   - Serialize to JSON via `EventEnvelopeJsonCodec`
   - `producer.send().get()` — **synchronous** Kafka send
   - On success: `markPublished(eventId)`
   - On failure: `releaseForRetry()` with exponential backoff

**Horizontal scaling:** Lease mechanism allows multiple app instances. If one crashes, leases expire and other instances pick up.

### Producer Configuration
```
acks=all
enable.idempotence=true
max.in.flight.requests.per.connection=5
max.block.ms=5000
request.timeout.ms=5000
delivery.timeout.ms=10000
```

---

## Inbox Idempotency

### Why?
At-least-once Kafka delivery means the same event may be delivered multiple times. The inbox prevents duplicate side effects.

### Mechanism
```sql
UNIQUE (consumer_name, event_id)
INSERT ... ON CONFLICT (consumer_name, event_id) DO NOTHING
```

| `beginProcessing()` return | Meaning |
|---------------------------|---------|
| `true` | New event — first time processing |
| `false` | Duplicate — skip (already processed) |

### Consumer Names

| Consumer | Name | Dedup Method |
|----------|------|-------------|
| NotificationEventHandler | `notification-consumer` | Inbox table |
| NotificationCommandHandler.handle() | `notification-command-consumer` | Upsert (findById) |
| NotificationCommandHandler.failure() | `notification-command-consumer` | Inbox table |

---

## Guarantees

| Property | How It's Achieved |
|----------|-------------------|
| **Atomicity** | Outbox INSERT in same DB transaction as domain change |
| **At-least-once delivery** | Offset committed after DB transaction completes |
| **At-most-once SMTP** | Email sent before DB transaction (may send without recording) |
| **Idempotent processing** | Inbox table with unique constraint |
| **Ordering within partition** | Synchronous `send().get()` per partition |
| **No data loss on crash** | Uncommitted offsets → re-delivery, idempotency prevents duplicates |
