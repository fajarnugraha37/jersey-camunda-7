---
type: Concept
title: Domain Concepts
description: Core domain entities, aggregate state machines, business invariants, and domain rules in the Sentinel Enforcement Platform.
tags: [domain, entities, state-machines, business-rules]
---

# Domain Concepts

## Core Domain Entities

### CaseRecord (`/sentinel-domain/src/main/java/.../domain/casefile/CaseRecord.java`)
The central aggregate. Records a regulatory enforcement case with full state machine, assignment tracking, optimistic locking, and audit trail.

### Report (`/sentinel-domain/src/main/java/.../domain/report/Report.java`)
Initial intake document. A triaged report is required to create a case.

### Evidence (`/sentinel-domain/src/main/java/.../domain/evidence/Evidence.java`)
Supports versioned uploads with checksum verification. Uses MinIO presigned URLs for upload/download sessions.

### Recommendation (`/sentinel-domain/src/main/java/.../domain/recommendation/Recommendation.java`)
Investigator-submitted recommendation that requires reviewer approval. Enforces maker-checker (author cannot self-approve).

### Decision (`/sentinel-domain/src/main/java/.../domain/decision/Decision.java`)
Formal decision with versioning. Maker-checker enforced (author cannot approve own draft). Published decisions are immutable.

### Appeal (`/sentinel-domain/src/main/java/.../domain/appeal/Appeal.java`)
Appeal against a decision, with full lifecycle and outcome tracking.

### Sanction (`/sentinel-domain/src/main/java/.../domain/sanction/Sanction.java`)
Penalties with obligation tracking. Supports overdue sanction recalculation via maintenance operations.

## State Machines

### Case Status Lifecycle

```
CREATED &rarr; UNDER_TRIAGE &rarr; UNDER_INVESTIGATION &rarr;
PENDING_REVIEW &rarr; PENDING_DECISION &rarr; DECIDED &rarr;
UNDER_APPEAL (optional) &rarr; ENFORCEMENT_IN_PROGRESS &rarr; CLOSED

CREATED, UNDER_TRIAGE, UNDER_INVESTIGATION, PENDING_REVIEW,
PENDING_DECISION, DECIDED, UNDER_APPEAL, ENFORCEMENT_IN_PROGRESS &rarr; CANCELLED
```

Terminal states: `CLOSED`, `CANCELLED`. Once terminal, no further transitions or assignments are allowed.

Case state transitions trigger workflow task progression via the embedded [Camunda engine](/openwiki/workflows/bpmn.md), and emit lifecycle events through the [transactional outbox](/openwiki/integrations/messaging-storage.md#transactional-outbox-pattern).

Source: `/sentinel-domain/src/main/java/.../domain/casefile/CaseStatus.java`, `CaseRecord.java` (`transitionTo` method).

### Decision Status Lifecycle

```
DRAFT &rarr; AWAITING_APPROVAL &rarr; APPROVED &rarr; PUBLISHED

DRAFT &rarr; WITHDRAWN
AWAITING_APPROVAL &rarr; WITHDRAWN
```

Published decisions are **immutable** — no further transitions allowed.

Source: `/sentinel-domain/src/main/java/.../domain/decision/DecisionStatus.java`.

### Recommendation Status Lifecycle

```
DRAFT &rarr; SUBMITTED &rarr; REVIEWED &rarr; ACCEPTED (or REJECTED)
SUBMITTED &rarr; WITHDRAWN
```

Maker-checker: the author of a recommendation cannot be the reviewer who accepts/rejects it.

Source: `/sentinel-domain/src/main/java/.../domain/recommendation/RecommendationStatus.java`.

### Appeal Status Lifecycle

```
SUBMITTED &rarr; UNDER_REVIEW &rarr; DECIDED (UPHELD / DENIED / REMANDED)
```

Source: `/sentinel-domain/src/main/java/.../domain/appeal/AppealStatus.java`.

### Evidence Status

Evidence goes through: upload session creation &rarr; object upload to MinIO &rarr; server-side finalization (checksum, size, media type verification) &rarr; version activation. Versions are immutable once finalized.

Source: `/sentinel-domain/src/main/java/.../domain/evidence/EvidenceUploadSessionStatus.java`, `EvidenceStorageStatus.java`.

## Domain Invariants

### Optimistic Locking
All aggregates carry a `version` field incremented on each mutation. Concurrent modification attempts receive a `CONCURRENT_MODIFICATION` error.

Source: `CaseRecord.java` (`validateExpectedVersion`), `Decision.java`.

### Maker-Checker Control
- **Recommendation**: Author cannot be the reviewer (`Recommendation.java` line 60+)
- **Decision**: Author cannot approve their own draft (`Decision.java` line 50+)

### Terminal-State Guard
Cases in `CLOSED` or `CANCELLED` status cannot be transitioned or assigned.

### Immutable Published Decisions
Once a decision reaches `PUBLISHED` status, no further modifications are permitted.

### Case Classification
Cases carry a `CaseClassification` enum (`PUBLIC`, `CONFIDENTIAL`, `SECRET`). Authorization checks require the actor's JWT to contain the matching `case_classifications` claim.

### Case Relationships
Cases can be linked via `CaseRelationship` with a `CaseRelationshipType`. Relationship traversal supports direction-aware queries (`FORWARD`, `BACKWARD`, `BOTH`). Source: `/sentinel-application/src/main/java/.../casefile/CaseRelationshipTraversalDirection.java`.

## Business Flows

### Report-to-Case Flow
1. Intake officer creates a `Report` (status: `SUBMITTED`)
2. Triage officer triages the report (status: `TRIAGED`)
3. A `CaseRecord` is created from the triaged report, with a correlated Camunda workflow instance
4. Case moves through its lifecycle driven by workflow tasks

### Evidence Flow
1. Authorized actor requests an upload session &rarr; receives presigned MinIO URL
2. Client uploads the file directly to MinIO
3. Server finalizes the evidence version: verifies object existence, SHA-256 checksum, size, and content-type
4. Evidence version is activated and immutable

### Decision Flow
1. Decision maker creates a decision draft
2. Decision maker approves (maker-checker enforced: cannot self-approve if author)
3. Supervisor publishes the decision &rarr; immutable

### Appeal Flow
1. Appeal officer submits an appeal against a published decision
2. Appeal officer decides the appeal (UPHELD / DENIED / REMANDED)

## Source Map

All domain source files: `/sentinel-domain/src/main/java/com/sentinel/enforcement/domain/`
All application service tests: `/sentinel-application/src/test/java/com/sentinel/enforcement/application/`
Domain tests: `/sentinel-domain/src/test/java/` (pure unit tests, no mocks)
