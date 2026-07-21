# Domain Aggregates

All aggregates are implemented as **immutable Java records** in `sentinel-domain`. State changes return new instances (no setters). Every mutable aggregate carries a `long version` field for optimistic concurrency control.

**Package:** `com.sentinel.enforcement.domain`

---

## 1. Report (Package: `report`)

Root entity for incoming reports.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Primary key |
| `title` | `String` | Report title |
| `description` | `String` | Report details |
| `jurisdictionCode` | `String` | Territorial jurisdiction |
| `reporterName` | `String` | Name of reporting party |
| `status` | `ReportStatus` | `SUBMITTED` or `TRIAGED` |
| `createdAt` | `Instant` | Creation timestamp |
| `createdBy` | `String` | Actor ID who created |
| `updatedAt` | `Instant` | Last update timestamp |
| `updatedBy` | `String` | Actor ID who last updated |
| `version` | `long` | OCC version |

### Behavior
| Method | Transition | Guard |
|--------|-----------|-------|
| `triage()` | `SUBMITTED → TRIAGED` | Validates version; reason must be non-blank |

---

## 2. CaseRecord (Package: `casefile`)

Primary aggregate — the central entity of the platform.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Primary key |
| `caseNumber` | `String` | Generated unique number (JURIS-ENF-YEAR-SEQ) |
| `reportId` | `UUID` | Source report FK |
| `title` | `String` | Case title |
| `summary` | `String` | Case summary |
| `jurisdictionCode` | `String` | Territorial jurisdiction |
| `classification` | `CaseClassification` | `PUBLIC`, `CONFIDENTIAL`, or `SECRET` |
| `status` | `CaseStatus` | One of 10 lifecycle states |
| `assignedUnitId` | `String` | Nullable assigned unit |
| `assigneeUserId` | `String` | Nullable assignee |
| `createdAt` | `Instant` | |
| `createdBy` | `String` | |
| `updatedAt` | `Instant` | |
| `updatedBy` | `String` | |
| `version` | `long` | |

### Behaviors
| Method | Purpose |
|--------|---------|
| `assignTo(unitId, userId, context)` | Assign to unit + user; validates version, non-terminal status, role |
| `transitionTo(targetStatus, context)` | State transition; validates role, allowed target, version |

### Supporting Records

**CaseAssignment** — Assignment chain with rotation support:
- `id`, `caseId`, `assignedUnitId`, `assigneeUserId`, `assignmentReason`
- `assignedAt`, `assignedBy`, `createdAt`, `createdBy`, `updatedAt`, `updatedBy`, `version`

**CaseRelationship** — Directed edge between cases:
- `parentCaseId`, `childCaseId`, `relationshipType` (MERGE, DERIVATION, SPLIT)
- Self-reference guard: `parentCaseId != childCaseId`

**CaseStatusHistoryEntry** — Immutable status transition log:
- `caseId`, `fromStatus` (nullable), `toStatus`, `transitionReason`, `transitionedAt`, `transitionedBy`

**AuditEvent** — Append-only audit trail:
- `eventId`, `eventType`, `actorType/id/roles`, `action`, `resourceType/id`
- `caseId`, `timestamp`, `correlationId`, `sourceIp`, `result`, `reason`
- `beforeSummary`, `afterSummary`, `metadata`

**CaseActionContext** — Value object for action authorization:
- `actorId`, `actorRoles`, `expectedVersion`, `reason`, `timestamp`
- Helper: `hasAnyRole(String... roles)`

---

## 3. Evidence (Package: `evidence`)

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Primary key |
| `caseId` | `UUID` | Parent case FK |
| `title` | `String` | Evidence title |
| `classification` | `EvidenceClassification` | PUBLIC, CONFIDENTIAL, SECRET |
| `storageStatus` | `EvidenceStorageStatus` | PENDING_UPLOAD or ACTIVE |
| `latestVersion` | `int` | Current version number |
| `createdAt/By` | | |
| `updatedAt/By` | | |
| `version` | `long` | |

### Behavior
| Method | Transition | Guard |
|--------|-----------|-------|
| `activate(version, now, actor)` | `PENDING_UPLOAD → ACTIVE` | version >= 1 |

### Supporting Records

**EvidenceVersion** — Immutable version snapshot:
- `evidenceId`, `versionNumber`, `originalFilename`, `generatedFilename`
- `bucket`, `objectKey`, `mediaType`, `sizeBytes`, `sha256Checksum`
- `uploadedAt`, `uploadedBy`

**EvidenceUploadSession** — Presigned URL session:
- `caseId`, `evidenceId`, `targetVersionNumber`
- Filename, bucket, objectKey, mediaType, sizeBytes, sha256
- `status` (PENDING / FINALIZED), `expiresAt`
- `finalizeSession()` guards: cannot finalize if already FINALIZED or expired

---

## 4. Recommendation (Package: `recommendation`)

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Primary key |
| `caseId` | `UUID` | Parent case FK (unique per case) |
| `title` | `String` | |
| `summary` | `String` | |
| `proposedDecision` | `String` | Proposed decision text |
| `proposedSanction` | `String` | Nullable proposed sanction |
| `status` | `RecommendationStatus` | DRAFT → SUBMITTED → APPROVED |
| `submittedAt/By` | | Nullable until submitted |
| `approvedReviewId` | `UUID` | Nullable until approved |
| `version` | `long` | |

### State Machine (with maker-checker)
| Method | From → To | Guard |
|--------|-----------|-------|
| `submit(now, actor)` | DRAFT → SUBMITTED | Must be DRAFT |
| `approve(reviewId, now, actor)` | SUBMITTED → APPROVED | Must be SUBMITTED; **maker-checker**: `createdBy != actorId` |

**RecommendationReview**: outcome (APPROVED), reviewSummary, reviewedAt/By

---

## 5. Decision (Package: `decision`)

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Primary key |
| `caseId` | `UUID` | Parent case FK (unique per case) |
| `recommendationId` | `UUID` | Source recommendation FK |
| `violationProven` | `boolean` | Whether violation was proven |
| `sanctionSummary` | `String` | Nullable if no violation |
| `obligationTitle/Details/DueDate` | | Nullable if no violation |
| `appealDeadline` | `LocalDate` | Deadline to file appeal |
| `status` | `DecisionStatus` | DRAFT → APPROVED → PUBLISHED |
| `approvedAt/By` | | Nullable until approved |
| `publishedAt/By` | | Nullable until published |
| `version` | `long` | |

### State Machine (with maker-checker)
| Method | From → To | Guard |
|--------|-----------|-------|
| `approve(now, actor)` | DRAFT → APPROVED | **maker-checker**: `createdBy != actorId` |
| `publish(now, actor)` | APPROVED → PUBLISHED | Must be APPROVED; immutable once PUBLISHED |

**DecisionVersion** — Immutable snapshot on publish.

---

## 6. Sanction (Package: `sanction`)

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Primary key |
| `caseId` | `UUID` | |
| `decisionId` | `UUID` | |
| `summary` | `String` | |
| `status` | `SanctionStatus` | ACTIVE or CANCELLED |
| `version` | `long` | |

### Behavior
| Method | Transition |
|--------|-----------|
| `cancel(now, actor)` | ACTIVE → CANCELLED |

**SanctionObligation**: title, details, dueDate, status (ACTIVE / OVERDUE / SATISFIED / CANCELLED)

---

## 7. Appeal (Package: `appeal`)

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Primary key |
| `caseId` | `UUID` | |
| `decisionId` | `UUID` | |
| `rationale` | `String` | Appeal rationale |
| `supervisorOverride` | `boolean` | Whether supervisor override applied |
| `status` | `AppealStatus` | ACTIVE or DECIDED |
| `submittedAt/By` | | |
| `decidedByAppealDecisionId` | `UUID` | Nullable until decided |
| `version` | `long` | |

### Behavior
| Method | Transition |
|--------|-----------|
| `decide(appealDecisionId, now, actor)` | ACTIVE → DECIDED |

**AppealDecision**: outcome (DENIED / GRANTED), summary, decidedAt/By

---

## Domain Exceptions

| Exception | Package | Error Codes |
|-----------|---------|-------------|
| `ReportConflictException` | `report` | `CONCURRENT_MODIFICATION`, `REPORT_TRIAGE_NOT_ALLOWED` |
| `CaseConflictException` | `casefile` | `CONCURRENT_MODIFICATION`, `CASE_ASSIGNMENT_NOT_ALLOWED`, `CASE_TRANSITION_NOT_ALLOWED` |
| `EvidenceConflictException` | `evidence` | `EVIDENCE_UPLOAD_SESSION_ALREADY_FINALIZED`, `EVIDENCE_UPLOAD_SESSION_EXPIRED` |
| `RecommendationConflictException` | `recommendation` | `RECOMMENDATION_SUBMIT_NOT_ALLOWED`, `RECOMMENDATION_REVIEW_NOT_ALLOWED`, `MAKER_CHECKER_VIOLATION` |
| `DecisionConflictException` | `decision` | `DECISION_APPROVAL_NOT_ALLOWED`, `DECISION_PUBLICATION_NOT_ALLOWED`, `MAKER_CHECKER_VIOLATION` |
| `AppealConflictException` | `appeal` | `APPEAL_DECISION_NOT_ALLOWED` |

All extend `RuntimeException` with a `code()` method returning the error code string.
