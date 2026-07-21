# Consumer Reliability

## KafkaNotificationConsumer

A single consumer daemon that subscribes to ALL domain lifecycle topics + `notification.command.v1` + their `.retry` queues.

### Consumer Configuration

| Property | Value |
|----------|-------|
| `group.id` | `sentinel-notification-consumer` (configurable) |
| `enable.auto.commit` | `false` (manual offset control) |
| `auto.offset.reset` | `earliest` |
| `max.poll.records` | 10 |
| `poll.timeout` | 500ms |

---

## Retry Mechanism

### Flow

```
Consumer polls record
    │
    ├─ Process successfully → commitSync() offset
    │
    └─ Processing fails →
        │
        ├─ Read x-retry-attempt header (default 0)
        │
        ├─ attempt >= maxRetries (default 3) → send to {topic}.dlq
        │
        └─ attempt < maxRetries → send to {topic}.retry
            with x-retry-attempt = currentAttempt + 1
```

### Retry/DLQ Headers

| Header | Description |
|--------|-------------|
| `x-original-topic` | The base topic name |
| `x-retry-attempt` | Incremented counter |
| `x-error` | Exception class name |

### Important
- After routing to retry/DLQ, **the original offset is committed** (record considered consumed)
- DLQ topics are never auto-consumed — they require human investigation
- Retry topics ARE consumed by the same consumer (subscribed via `subscribedTopics()`)

---

## Permanent Failure Handling

If a `notification.command.v1` event permanently fails (sent to DLQ):

1. `notificationRepository` records status = `FAILED`
2. `outboxRepository` enqueues a `notification.result.v1` event with `deliveryStatus: "FAILED"`
3. This allows upstream systems to know the notification ultimately failed

---

## At-Least-Once Delivery

| Scenario | Behavior |
|----------|----------|
| DB commit succeeds, Kafka commit fails | Record re-delivered → idempotency prevents duplicate |
| DB commit fails | No visible effect, record retried |
| SMTP send succeeds, DB commit fails | Email already sent (at-most-once for SMTP) |
| SMTP send fails | Exception thrown → retry via `.retry` queue |
| Processor crashes mid-processing | Uncommitted offsets → re-delivery on restart |

---

## Leases (Outbox Publisher Only)

| Mechanism | Purpose |
|-----------|---------|
| `leaseOwner` | Unique instance ID marks claim |
| `leaseExpiresAt` | Auto-expiry if publisher crashes |
| `FOR UPDATE SKIP LOCKED` | No lock contention between instances |

This enables safe horizontal scaling of the outbox publisher.
