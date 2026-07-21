# Event Catalog

## Kafka Topics

### Domain Lifecycle Topics (6)

| Topic | Event Types | Produced By |
|-------|------------|-------------|
| `case.lifecycle.v1` | `CaseCreated`, `CaseTransitioned` | Case application service |
| `case.assignment.v1` | `CaseAssigned` | Case application service |
| `evidence.lifecycle.v1` | `EvidenceVersionFinalized` | Evidence application service |
| `decision.lifecycle.v1` | `DecisionPublished` | Decision application service |
| `sanction.lifecycle.v1` | `SanctionCreated`, `SanctionCancelled` | Decision application service |
| `appeal.lifecycle.v1` | `AppealFiled`, `AppealDecided` | Appeal application service |

### Integration Topics (3)

| Topic | Event Types | Purpose |
|-------|------------|---------|
| `notification.command.v1` | `NotificationDispatchRequested` | Outbound email dispatch command |
| `notification.result.v1` | `NotificationDispatched`, `NotificationDispatchFailed` | Email dispatch result |
| `audit.integration.v1` | `AuditIntegrated` | External audit event integration |

### Retry & DLQ Topics

For every topic above, the following are auto-provisioned:
- `{topic}.retry` — Scheduled retry queue (consumed by the consumer)
- `{topic}.dlq` — Dead letter queue (never auto-consumed, human investigation)

---

## Event Envelope

Every event follows this structure:

```json
{
  "eventId": "uuid",
  "eventType": "CaseCreated",
  "eventVersion": 1,
  "aggregateType": "Case",
  "aggregateId": "uuid",
  "occurredAt": "2026-07-22T00:00:00Z",
  "correlationId": "...",
  "causationId": "...",
  "actor": {
    "actorType": "USER",
    "actorId": "intake-jkt"
  },
  "payload": { ... }
}
```

---

## Event Schemas

### `NotificationDispatchRequested` (topic: `notification.command.v1`)

| Field | Type | Description |
|-------|------|-------------|
| `notificationId` | UUID | Notification record ID |
| `caseId` | UUID | Related case (nullable) |
| `notificationType` | String | e.g. `CaseCreated`, `DecisionPublished` |
| `title` | String | Email subject |
| `body` | String | Email body |
| `toEmail` | String | Recipient |
| `fromEmail` | String | Sender |
| `channel` | String | Always `"EMAIL"` |

### `NotificationDispatched` / `NotificationDispatchFailed` (topic: `notification.result.v1`)

| Field | Type | Description |
|-------|------|-------------|
| `notificationId` | UUID | |
| `caseId` | UUID | |
| `notificationType` | String | |
| `deliveryStatus` | String | `"SENT"` or `"FAILED"` |
| `toEmail` | String | |
| `errorDetail` | String | Null on success |

### `AuditIntegrated` (topic: `audit.integration.v1`)

| Field | Type | Description |
|-------|------|-------------|
| `auditEventType` | String | Type of audit event |
| `actorRoles` | String | Roles at time of action |
| `action` | String | Action performed |
| `resourceType` | String | |
| `resourceId` | String | |
| `caseId` | UUID | |
| `timestamp` | Instant | |
| `sourceIp` | String | |
| `result` | String | Success/failure |
| `reason` | String | |
| `beforeSummary` / `afterSummary` | String | State diff |
| `metadata` | Object | |

---

## Event Producer → Consumer Flow

```
Application Service
    │ (within DB transaction)
    ├─ outboxRepository.enqueue(case.lifecycle.v1)
    ├─ outboxRepository.enqueue(case.assignment.v1)
    └─ DB commit
         │
         ▼ (outbox publisher)
    Kafka topic
         │
         ▼ (notification consumer)
    Inbox dedup → NotificationEventHandler
         ├─ notificationRepository.save()
         ├─ outboxRepository.enqueue(notification.command.v1)
         └─ DB commit
              │
              ▼ (outbox publisher)
         notification.command.v1 topic
              │
              ▼ (notification command consumer)
         NotificationCommandHandler
              ├─ SMTP send (Mailpit)
              ├─ notificationRepository.updateStatus(SENT/FAILED)
              ├─ outboxRepository.enqueue(notification.result.v1)
              ├─ outboxRepository.enqueue(audit.integration.v1)
              └─ DB commit
```
