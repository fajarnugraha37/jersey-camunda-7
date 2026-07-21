# Sentinel Enforcement Platform — Comprehensive Technical Documentation

> **Version**: 0.1.0-SNAPSHOT  
> **Java**: 21 | **Build**: Maven 3.9+ | **Infrastructure**: Docker Compose  
> **Last Updated**: July 2026  
> **Current Phase**: Phase 8 (Complete)

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Technology Stack](#2-technology-stack)
3. [Architecture Pattern: Modular Hexagonal Monolith](#3-architecture-pattern-modular-hexagonal-monolith)
4. [Module Reference: sentinel-domain](#4-module-reference-sentinel-domain)
5. [Module Reference: sentinel-application](#5-module-reference-sentinel-application)
6. [Module Reference: sentinel-api (REST Adapter)](#6-module-reference-sentinel-api-rest-adapter)
7. [Module Reference: sentinel-persistence (MyBatis)](#7-module-reference-sentinel-persistence-mybatis)
8. [Module Reference: sentinel-messaging (Kafka)](#8-module-reference-sentinel-messaging-kafka)
9. [Module Reference: sentinel-storage (MinIO)](#9-module-reference-sentinel-storage-minio)
10. [Module Reference: sentinel-workflow (Camunda BPMN)](#10-module-reference-sentinel-workflow-camunda-bpmn)
11. [Module Reference: sentinel-security (Keycloak)](#11-module-reference-sentinel-security-keycloak)
12. [Module Reference: sentinel-observability](#12-module-reference-sentinel-observability)
13. [Module Reference: sentinel-bootstrap](#13-module-reference-sentinel-bootstrap)
14. [Module Reference: sentinel-integration-tests](#14-module-reference-sentinel-integration-tests)
15. [Authorization Model (7-Axis)](#15-authorization-model-7-axis)
16. [Case Lifecycle State Machine](#16-case-lifecycle-state-machine)
17. [Evidence Lifecycle](#17-evidence-lifecycle)
18. [Messaging & Reliability Architecture](#18-messaging--reliability-architecture)
19. [BPMN Workflow Detail](#19-bpmn-workflow-detail)
20. [Database Schema](#20-database-schema)
21. [Configuration Reference](#21-configuration-reference)
22. [Infrastructure Architecture](#22-infrastructure-architecture)
23. [Developer Workflow (Make Targets)](#23-developer-workflow-make-targets)
24. [Testing Strategy](#24-testing-strategy)
25. [Architecture Decision Records](#25-architecture-decision-records)
26. [Known Limitations & Future Work](#26-known-limitations--future-work)

---

## 1. System Overview

Sentinel Enforcement Platform is a **regulatory enforcement and complex case management system** for managing cases from initial report intake through final sanction and appeal closure. It serves regulatory and enforcement agencies requiring structured, auditable, event-driven processes with strict business rules enforced at every state transition.

### Non-Responsibilities (Explicit Out-of-Scope)

| Area | Rationale |
|---|---|
| Public-Facing Portal | Reports arrive via API from internal/integrated systems |
| Document Generation | No PDF rendering or template-based generation |
| Notification Delivery | Platform publishes commands to Kafka; does not directly deliver email/SMS |
| Payment Processing | No financial transaction handling |
| Identity Management | Managed in Keycloak |
| Data Analytics / BI | No built-in dashboards |
| Cloud-Native Deployments | All infrastructure runs locally in containers |

---

## 2. Technology Stack

### Backend Framework & Language

| Technology | Version | Role |
|---|---|---|
| Java | 21 | Records, sealed classes, pattern matching, `HexFormat`, `DigestInputStream` |
| Jakarta RESTful Web Services | 3.1 | JAX-RS `@Path`, `@GET`, `@POST`, etc. |
| Jersey | 3.1.9 | JAX-RS implementation |
| Grizzly HTTP Server | 4.x | Embedded HTTP server |
| HK2 | 3.x | Dependency injection via `AbstractBinder` |

### Data & Persistence

| Technology | Version | Role |
|---|---|---|
| PostgreSQL | 18.3-alpine | Primary database |
| MyBatis | 3.5.19 | SQL mapping, no ORM |
| HikariCP | 6.3.0 | Connection pooling |
| Liquibase | 4.31.1 | Database schema migration |
| MapStruct | 1.6.3 | Bean mapping (annotation processor) |

### Integration

| Technology | Version | Role |
|---|---|---|
| Apache Kafka | 3.8.1 (KRaft) | Event bus (no ZooKeeper) |
| Camunda BPM | 7.24.0 | Embedded workflow engine |
| MinIO SDK | 8.5.17 | S3-compatible object storage |
| Keycloak | 26.6 | OIDC provider / JWT issuer |
| Redis | 7.2.7-alpine | Cache (available, not actively used) |
| Mailpit | latest | Dev SMTP catch-all |

### JSON & Validation

| Technology | Version | Role |
|---|---|---|
| Jackson FasterXML | 2.18.2 | JSON serialization/deserialization |
| Hibernate Validator | 9.0.0.Final | Jakarta Bean Validation |
| Nimbus JOSE+JWT | 10.0.2 | JWT verification |

### Code Generation & Quality

| Technology | Version | Role |
|---|---|---|
| OpenAPI Generator | 7.12.0 | Generate API DTOs from OpenAPI spec |
| Spotless | 2.44.3 | Code formatting (Google Java Format) |

### Testing

| Technology | Version | Role |
|---|---|---|
| JUnit | 5.11.4 | Unit testing |
| Testcontainers | 1.20.5 | Integration test infrastructure |
| Karate | 2.1.1 | REST API acceptance testing |

---

## 3. Architecture Pattern: Modular Hexagonal Monolith

The platform uses a **modular monolith with hexagonal (ports & adapters) architecture**. The entire application is a single deployable JAR, but module boundaries and dependency directions enforce internal separation.

### Architectural Layers

```text
┌──────────────────────────────────────────────────────────┐
│                     REST API (JAX-RS)                     │
│   sentinel-api: Resources, Filters, Exception Mappers    │
├──────────────────────────────────────────────────────────┤
│                  Application Services                     │
│    sentinel-application: Use Cases, Ports, Commands       │
├──────────────────────────────────────────────────────────┤
│                   Domain Model                            │
│   sentinel-domain: Aggregates, Value Objects, Policies    │
├──────────┬──────────┬──────────┬──────────┬──────────────┤
│ sentinel │ sentinel │ sentinel │ sentinel │ sentinel      │
│persist.  │ messaging│ storage  │ workflow │ security      │
│(MyBatis) │ (Kafka)  │ (MinIO)  │ (Camunda)│ (Keycloak)    │
├──────────┴──────────┴──────────┴──────────┴──────────────┤
│                   sentinel-bootstrap                      │
│   DI Wiring · Grizzly Server · Daemon Lifecycle          │
├──────────────────────────────────────────────────────────┤
│              sentinel-integration-tests                    │
│   Testcontainers · Karate · End-to-End Verification      │
└──────────────────────────────────────────────────────────┘
```

### Dependency Rules

- `sentinel-domain` → **zero dependencies** (no framework, no infrastructure)
- `sentinel-application` → depends only on `sentinel-domain`
- All adapter modules (`sentinel-api`, `sentinel-persistence`, `sentinel-messaging`, `sentinel-storage`, `sentinel-workflow`, `sentinel-security`, `sentinel-observability`) → depend on `sentinel-application`
- `sentinel-bootstrap` → depends on all adapters
- `sentinel-integration-tests` → depends on bootstrap (test scope)

### Module Dependency Graph (Maven)

```text
sentinel-domain (pom)
    └── sentinel-application (pom)
            ├── sentinel-api (jar)
            ├── sentinel-persistence (jar)
            ├── sentinel-messaging (jar)
            ├── sentinel-storage (jar)
            ├── sentinel-workflow (jar)
            ├── sentinel-security (jar)
            └── sentinel-observability (jar)
                    └── sentinel-bootstrap (jar) [shaded]
                            └── sentinel-integration-tests (jar)
```

---

## 4. Module Reference: sentinel-domain

**Package**: `com.sentinel.enforcement.domain`  
**Zero dependencies** — no framework imports, no infrastructure imports. Pure Java records and enums.

### 4.1 Package Structure

```
domain/
  report/          — Report aggregate
  casefile/        — CaseRecord aggregate (+ AuditEvent, Assignment, Relationship)
  evidence/        — Evidence aggregate (+ UploadSession, Version)
  recommendation/  — Recommendation aggregate (+ Review)
  decision/        — Decision aggregate (+ DecisionVersion)
  sanction/        — Sanction aggregate (+ SanctionObligation)
  appeal/          — Appeal aggregate (+ AppealDecision)
```

### 4.2 Aggregate: Report

**File**: `report/Report.java` — Java `record`

**Fields**: `id`, `title`, `description`, `jurisdictionCode`, `reporterName`, `status`, `createdAt`, `createdBy`, `updatedAt`, `updatedBy`, `version`

**State Machine**: `SUBMITTED` → `TRIAGED`

**Behavioral Methods** (immutable transitions):
- `triage(actorId, expectedVersion, reason, now)` — Validates optimistic lock (`expectedVersion == version`), requires `SUBMITTED` status, requires non-blank `reason` and `actorId`. Returns new record with `TRIAGED` status and incremented version.

**Enums**:
- `ReportStatus` → `SUBMITTED`, `TRIAGED`

**Exceptions**:
- `ReportConflictException` — Thrown on version mismatch or invalid state. Error codes: `CONCURRENT_MODIFICATION`, `REPORT_TRIAGE_NOT_ALLOWED`

### 4.3 Aggregate: CaseRecord

**File**: `casefile/CaseRecord.java` — Java `record`

**Fields**: `id`, `caseNumber`, `reportId`, `title`, `summary`, `jurisdictionCode`, `classification`, `status`, `assignedUnitId`, `assigneeUserId`, `createdAt`, `createdBy`, `updatedAt`, `updatedBy`, `version`

**State Machine**: 10-state lifecycle with strict transition rules:

```text
CREATED ──► UNDER_TRIAGE ──► UNDER_INVESTIGATION ──► PENDING_REVIEW ──► PENDING_DECISION ──► DECIDED
    │            │                  │                       │                    │
    │            ▼                  ▼                       ▼                    ▼
    │        CANCELLED          CANCELLED             (back to INVESTIGATION)   │
    │                                                                          │
    │                                                     DECIDED ──► UNDER_APPEAL ──► CLOSED
    │                                                                  │
    │                                                     DECIDED ──► ENFORCEMENT_IN_PROGRESS ──► CLOSED
    ▼
  CANCELLED
```

**Key methods**:
- `create(...)` — Static factory, starts at `CREATED` status, version `0`
- `assignTo(unitId, userId, context)` — Requires non-terminal status, requires `TRIAGE_OFFICER` or `SUPERVISOR` role, checks optimistic lock
- `transitionTo(targetStatus, context)` — Validates: same-status guard, allowed target check via `isAllowedTarget()`, role check via `requiredRolesFor()`, optimistic lock check
- `auditSummary()` — Returns string summary for audit log

**Enums**:
- `CaseStatus` → `CREATED`, `UNDER_TRIAGE`, `UNDER_INVESTIGATION`, `PENDING_REVIEW`, `PENDING_DECISION`, `DECIDED`, `UNDER_APPEAL`, `ENFORCEMENT_IN_PROGRESS`, `CLOSED`, `CANCELLED`. Method: `isTerminal()` returns true for `CLOSED` and `CANCELLED`.
- `CaseClassification` → `PUBLIC`, `CONFIDENTIAL`, `SECRET`
- `CaseRelationshipType` → `MERGE`, `DERIVATION`, `SPLIT`

**Supporting classes**:
- `CaseActionContext` — Record: `actorId`, `actorRoles`, `expectedVersion`, `reason`, `timestamp`. Method `hasAnyRole(String...)`.
- `CaseAssignment` — Record: `id`, `caseId`, `assignedUnitId`, `assigneeUserId`, `assignmentReason`, `assignedAt`, `assignedBy`, timestamps, `version`
- `CaseStatusHistoryEntry` — Record: `id`, `caseId`, `fromStatus`, `toStatus`, `transitionReason`, `transitionedAt`, `transitionedBy`, timestamps
- `CaseRelationship` — Record: `id`, `parentCaseId`, `childCaseId`, `relationshipType`, `relationshipReason`, timestamps, `version`. Constructor validates `parentCaseId != childCaseId`.
- `AuditEvent` — Record: `eventId`, `eventType`, `actorType`, `actorId`, `actorRoles`, `action`, `resourceType`, `resourceId`, `caseId`, `timestamp`, `correlationId`, `sourceIp`, `result`, `reason`, `beforeSummary`, `afterSummary`, `metadata`

**Exceptions**:
- `CaseConflictException` — Error codes: `CONCURRENT_MODIFICATION`, `CASE_ASSIGNMENT_NOT_ALLOWED`, `CASE_TRANSITION_NOT_ALLOWED`, `CASE_RELATIONSHIP_CYCLE`, `REPORT_NOT_TRIAGED`, `NO_EFFECT_ASSIGNMENT`

### 4.4 Aggregate: Evidence

**File**: `evidence/Evidence.java` — Java `record`

**Fields**: `id`, `caseId`, `title`, `classification`, `storageStatus`, `latestVersion`, timestamps, `version`

**State Machine**: `PENDING_UPLOAD` → `ACTIVE`

**Methods**:
- `create(...)` — Static factory
- `activate(newLatestVersion, now, actorId)` — Requires `newLatestVersion >= 1`. Returns `ACTIVE` status.

**Supporting classes**:
- `EvidenceVersion` — Record: `id`, `evidenceId`, `versionNumber`, `originalFilename`, `generatedFilename`, `bucket`, `objectKey`, `mediaType`, `sizeBytes`, `sha256Checksum`, timestamps
- `EvidenceUploadSession` — Record: `id`, `caseId`, `evidenceId`, `targetVersionNumber`, filenames, `bucket`, `objectKey`, `mediaType`, `sizeBytes`, `sha256Checksum`, `classification`, `status`, `expiresAt`, timestamps, `version`. Methods: `create()` (static), `finalizeSession(now, actorId)` — validates `PENDING` status and non-expired session.
- `EvidenceClassification` → `PUBLIC`, `CONFIDENTIAL`, `SECRET`
- `EvidenceStorageStatus` → `PENDING_UPLOAD`, `ACTIVE`
- `EvidenceUploadSessionStatus` → `PENDING`, `FINALIZED`

**Exceptions**:
- `EvidenceConflictException` — Error codes: `EVIDENCE_UPLOAD_SESSION_ALREADY_FINALIZED`, `EVIDENCE_UPLOAD_SESSION_EXPIRED`, `EVIDENCE_CASE_MISMATCH`, `EVIDENCE_TITLE_MISMATCH`, `EVIDENCE_CLASSIFICATION_MISMATCH`, `EVIDENCE_UPLOAD_SESSION_NOT_FOUND`, `EVIDENCE_UPLOAD_SESSION_MISMATCH`, `EVIDENCE_UPLOAD_SESSION_STALE`, `EVIDENCE_SIZE_MISMATCH`, `EVIDENCE_MEDIA_TYPE_MISMATCH`, `EVIDENCE_CHECKSUM_MISMATCH`, `EVIDENCE_VERSION_NOT_FOUND`

### 4.5 Aggregate: Recommendation

**File**: `recommendation/Recommendation.java` — Java `record`

**Fields**: `id`, `caseId`, `title`, `summary`, `proposedDecision`, `proposedSanction`, `status`, `submittedAt`, `submittedBy`, `approvedReviewId`, timestamps, `version`

**State Machine**: `DRAFT` → `SUBMITTED` → `APPROVED`

**Methods**:
- `create(...)` — Static factory, starts at `DRAFT`
- `submit(now, actorId)` — Requires `DRAFT` status
- `approve(reviewId, now, actorId)` — Requires `SUBMITTED` status, **enforces maker-checker**: `createdBy.equals(actorId)` throws `MAKER_CHECKER_VIOLATION`
- `auditSummary()` — Returns string for audit log

**Supporting classes**:
- `RecommendationReview` — Record: `id`, `recommendationId`, `outcome`, `reviewSummary`, `reviewedAt`, `reviewedBy`, timestamps
- `RecommendationStatus` → `DRAFT`, `SUBMITTED`, `APPROVED`
- `RecommendationReviewOutcome` → `APPROVED`

**Exceptions**:
- `RecommendationConflictException` — Error codes: `RECOMMENDATION_SUBMIT_NOT_ALLOWED`, `RECOMMENDATION_REVIEW_NOT_ALLOWED`, `MAKER_CHECKER_VIOLATION`, `RECOMMENDATION_CREATE_NOT_ALLOWED`, `RECOMMENDATION_ALREADY_EXISTS`

### 4.6 Aggregate: Decision

**File**: `decision/Decision.java` — Java `record`

**Fields**: `id`, `caseId`, `recommendationId`, `title`, `summary`, `violationProven`, `sanctionSummary`, `obligationTitle`, `obligationDetails`, `obligationDueDate`, `appealDeadline`, `status`, `approvedAt`, `approvedBy`, `publishedAt`, `publishedBy`, timestamps, `version`

**State Machine**: `DRAFT` → `APPROVED` → `PUBLISHED` (immutable after publish)

**Validation Logic**:
- If `violationProven=true`: `sanctionSummary`, `obligationTitle`, `obligationDetails`, `obligationDueDate` are required
- If `violationProven=false`: obligation fields are nullified

**Methods**:
- `create(...)` — Static factory, starts at `DRAFT`
- `approve(now, actorId)` — Requires `DRAFT` status, **maker-checker**: author cannot approve own draft
- `publish(now, actorId)` — Requires `APPROVED` status, once `PUBLISHED` is immutable (status guard)
- `auditSummary()` — Returns string for audit log

**Supporting classes**:
- `DecisionVersion` — Record: `id`, `decisionId`, `versionNumber`, title/summary, `violationProven`, sanction/obligation details, `appealDeadline`, `publishedAt`, `publishedBy`, timestamps
- `DecisionStatus` → `DRAFT`, `APPROVED`, `PUBLISHED`

**Exceptions**:
- `DecisionConflictException` — Error codes: `DECISION_APPROVAL_NOT_ALLOWED`, `MAKER_CHECKER_VIOLATION`, `DECISION_PUBLICATION_NOT_ALLOWED`, `DECISION_CREATE_NOT_ALLOWED`, `DECISION_ALREADY_EXISTS`

### 4.7 Aggregate: Sanction

**File**: `sanction/Sanction.java` — Java `record`

**Fields**: `id`, `caseId`, `decisionId`, `summary`, `status`, timestamps, `version`

**State Machine**: `ACTIVE` → `CANCELLED`

**Supporting classes**:
- `SanctionObligation` — Record: `id`, `sanctionId`, `title`, `details`, `dueDate`, `status`, timestamps, `version`. Methods: `create(...)` static factory, `cancel(now, actorId)`.
- `SanctionStatus` → `ACTIVE`, `CANCELLED`
- `SanctionObligationStatus` → `ACTIVE`, `OVERDUE`, `SATISFIED`, `CANCELLED`

### 4.8 Aggregate: Appeal

**File**: `appeal/Appeal.java` — Java `record`

**Fields**: `id`, `caseId`, `decisionId`, `rationale`, `supervisorOverride`, `supervisorOverrideReason`, `status`, `submittedAt`, `submittedBy`, `decidedByAppealDecisionId`, timestamps, `version`

**State Machine**: `ACTIVE` → `DECIDED`

**Methods**:
- `create(...)` — Static factory
- `decide(appealDecisionId, now, actorId)` — Requires `ACTIVE` status
- `auditSummary()` — Returns string for audit log

**Supporting classes**:
- `AppealDecision` — Record: `id`, `appealId`, `outcome`, `summary`, `decidedAt`, `decidedBy`, timestamps
- `AppealStatus` → `ACTIVE`, `DECIDED`
- `AppealDecisionOutcome` → `DENIED`, `GRANTED`

**Exceptions**:
- `AppealConflictException` — Error codes: `APPEAL_DECISION_NOT_ALLOWED`, `APPEAL_CREATE_NOT_ALLOWED`, `APPEAL_ALREADY_EXISTS`, `APPEAL_LATE_OVERRIDE_REQUIRED`, `APPEAL_WORKFLOW_NOT_READY`

---

## 5. Module Reference: sentinel-application

**Package**: `com.sentinel.enforcement.application`  
**Depends on**: `sentinel-domain`  
**Key sub-packages**: One per aggregate domain service + `security/`, `workflow/`, `messaging/`, `health/`, `operations/`

### 5.1 Application Services

All application services follow the same pattern:
1. **Authorize** the actor via `AuthorizationService.requirePermission()`
2. **Load** the aggregate from repository
3. **Validate** business prerequisites (case status, existing records, etc.)
4. **Execute** domain method (returns new immutable record)
5. **Persist** in a database transaction via `ApplicationTransactionManager.required()`
6. **Emit events** via `OutboxRepository.enqueue()`
7. **Return** the updated aggregate

| Service | Key Operations | Prerequisites |
|---|---|---|
| `ReportApplicationService` | `createReport`, `getReport`, `triageReport` | Triage requires `SUBMITTED` status |
| `CaseApplicationService` | `createCase`, `getCase`, `listCases`, `assignCase`, `transitionCase`, `createRelationship`, `listRelationships`, `getCaseAuditEvents` | Create requires `TRIAGED` report; assign/transition require version-based OCC |
| `EvidenceApplicationService` | `createUploadSession`, `finalizeEvidenceVersion`, `getEvidence`, `createDownloadSession` | Finalize verifies size, media type, SHA-256 against MinIO |
| `RecommendationApplicationService` | `createRecommendation`, `submitRecommendation`, `approveRecommendation` | Create requires `UNDER_INVESTIGATION` case; one per case |
| `DecisionApplicationService` | `createDecision`, `approveDecision`, `publishDecision` | Create requires `PENDING_DECISION` + approved recommendation; approve uses `SELECT ... FOR UPDATE`; publish creates sanction+obligation if violation proven |
| `AppealApplicationService` | `createAppeal`, `decideAppeal`, `finalizeAppealWorkflowTask` | Create requires `PUBLISHED` decision + `DECIDED` case; late appeal requires supervisor override; decide maps outcome to case transition |
| `WorkflowTaskApplicationService` | `listTasks`, `claimTask`, `completeTask` | Complete drives domain transitions matching task definition keys |
| `WorkflowReconciliationApplicationService` | `listIssues`, `executeAction` | Supervisors detect/repair domain-workflow state mismatches |
| `MaintenanceOperationApplicationService` | `recalculateOverdueSanctionObligations` | Uses `REPEATABLE_READ` isolation, table lock |

### 5.2 Security Port Interfaces (sentinel-application/security)

```java
interface TokenVerifier {
    ApplicationActor verify(String bearerToken);
}

interface AuthorizationService {
    void requirePermission(ApplicationActor actor, Permission permission, AuthorizationContext context);
}
```

**Key data classes**:

- **`ApplicationActor`** — Record: `subject`, `username`, `roles`, `jurisdictions`, `assignedUnits`, `caseClassifications`, `conflictedActorIds`. Methods: `hasRole()`, `hasJurisdiction()`, `hasAssignedUnit()`, `hasCaseClassification()`, `isConflictedWith()`.
- **`AuthorizationContext`** — Record: `jurisdictionCode`, `resourceType`, `resourceId`, `caseId`, `assigneeUserId`, `assignedUnitId`, `caseClassification`, `resourceOwnerId`, `authorizationScope`. Constructor overload for non-case resources.
- **`CaseAuthorizationScope`** → `NONE`, `RESTRICTED_TO_ASSIGNED_UNITS`, `RESTRICTED_TO_ASSIGNED_UNITS_WHEN_PRESENT`
- **`Permission`** — 27 values across 8 categories (see Section 15)
- **`AuthorizationDeniedException`** — Thrown when any authorization check fails
- **`UnauthenticatedException`** — Thrown when token is missing or invalid

### 5.3 Messaging Port Interfaces (sentinel-application/messaging)

```java
interface OutboxRepository { ... }  // enqueue, claimPending, markPublished, releaseForRetry
interface InboxRepository { ... }  // ensureConsumed (idempotent INSERT)
interface ApplicationTransactionManager { ... }  // required(work), required(options, work)
interface NotificationRepository { ... }  // save, updateStatus, etc.
```

**Key data classes**:
- `EventEnvelope` — Typed event envelope with eventId, eventType, eventVersion, aggregateType, aggregateId, occurredAt, correlationId, causationId, actor, payload
- `EventActor` — Record: `type` (SYSTEM/USER), `id`
- `OutboxEvent` — Record: `eventId`, `topic`, `messageKey`, `serializedEnvelope`, `status` (PENDING/LEASED/PUBLISHED), `publishAttempts`, `leasedUntil`, `retryAt`, timestamps
- `InboxEvent` — Record: `consumerName`, `eventId` (unique constraint for idempotency), `status` (RECEIVED/PROCESSED/FAILED), timestamps
- `TransactionOptions` — Write/read options with isolation level and label
- `TransactionIsolation` → `READ_COMMITTED`, `REPEATABLE_READ`, `SERIALIZABLE`

### 5.4 Workflow Port Interfaces (sentinel-application/workflow)

```java
interface CaseWorkflowPort {
    StartedWorkflowInstance startCaseWorkflow(...);
    StartedWorkflowInstance startAppealWorkflow(...);
    void cancelCaseWorkflow(UUID caseId, String reason);
    void cancelAppealWorkflow(UUID caseId, String reason);
    List<WorkflowTaskView> listActiveTasks();
    Optional<WorkflowTaskView> findActiveTask(String taskId);
    boolean isTaskCompleted(String taskId);
    WorkflowTaskView claimTask(String taskId, String username);
    void completeTask(String taskId, Map<String, Object> variables);
    boolean correlateAppealFiled(UUID caseId, UUID appealId);
    boolean correlateAppealResolved(UUID caseId, boolean enforcementMonitoringRequired);
}

interface WorkflowAdministrationPort { ... }  // terminate, repair
interface WorkflowInstanceStore { ... }  // saveStarted, findById, etc.
interface WorkflowReconciliationQueryPort { ... }  // findMismatches
```

### 5.5 Key Application Data Flow (Case Creation)

This is a representative end-to-end flow showing the outbox pattern in action:

```text
1. Client POST /api/v1/cases
2. CaseResource.createCase(actor, command)
   └── CaseApplicationService.createCase(actor, command)
       ├── authorizationService.requirePermission(CREATE_CASE, ...)
       ├── reportRepository.findById(reportId) → must be TRIAGED
       ├── CaseRecord.create(...)
       ├── workflowPort.startCaseWorkflow(...)  ← starts Camunda process
       ├── transactionManager.required(() -> {
       │     caseRepository.save(caseRecord, historyEntry, auditEvent)
       │     outboxRepository.enqueue(auditIntegrated(auditEvent))
       │     outboxRepository.enqueue(caseCreated(actor, caseRecord))
       │     return null;
       │   })
       │   ← if exception: workflowPort.cancelCaseWorkflow(...) [compensation]
       └── return caseRecord
```

---

## 6. Module Reference: sentinel-api (REST Adapter)

**Package**: `com.sentinel.enforcement.api`  
**Dependencies**: JAX-RS (Jersey), Generated DTOs from OpenAPI

### 6.1 Resources

| Resource | Base Path | Operations |
|---|---|---|
| `HealthResource` | `GET /health` | Public health check (no auth) |
| `ReportResource` | `/api/v1/reports` | `POST` create, `GET {id}` read, `POST {id}/triage` |
| `CaseResource` | `/api/v1/cases` | `POST` create, `GET` list, `GET {id}` detail, `POST {id}/assignments`, `POST {id}/transitions`, `POST {id}/relationships`, `GET {id}/relationships`, `GET {id}/audit-events` |
| `CaseEvidenceResource` | `/api/v1/cases/{caseId}/evidence` | `POST upload-sessions` |
| `EvidenceResource` | `/api/v1/evidence` | `POST {id}/versions/finalize`, `GET {id}`, `POST {id}/download-sessions` |
| `CaseRecommendationResource` | `/api/v1/cases/{caseId}/recommendations` | `POST` create |
| `RecommendationResource` | `/api/v1/recommendations` | `POST {id}/submit`, `POST {id}/review` |
| `CaseDecisionResource` | `/api/v1/cases/{caseId}/decision` | `POST` create, `GET` read |
| `DecisionResource` | `/api/v1/decisions` | `POST {id}/approve`, `POST {id}/publish` |
| `AppealResource` | `/api/v1/cases/{caseId}/appeals` | `POST` create, `GET` list, `POST {id}/decision` |
| `TaskResource` | `/api/v1/tasks` | `GET` list, `POST {id}/claim`, `POST {id}/complete` |
| `WorkflowReconciliationResource` | `/api/v1/workflow-reconciliation` | `GET` list, `POST {caseId}/actions` |
| `MaintenanceOperationResource` | `/api/v1/maintenance` | `POST recalculate-sanctions` |

### 6.2 Exception Mappers (26)

All produce RFC 7807 `application/problem+json` responses using OpenAPI-generated `ErrorResponse` + `Violation` model.

| Mapper | HTTP Status | Maps From |
|---|---|---|
| `ConstraintViolationExceptionMapper` | 400 | Bean Validation violations |
| `BadRequestExceptionMapper` | 400 | Malformed requests |
| `UnauthenticatedExceptionMapper` | 401 | Missing/invalid JWT |
| `AuthorizationDeniedExceptionMapper` | 403 | Insufficient permissions |
| `NotFoundExceptionMapper` | 404 | Unknown paths |
| `ReportNotFoundExceptionMapper` | 404 | Report not found |
| `CaseNotFoundExceptionMapper` | 404 | Case not found |
| `EvidenceNotFoundExceptionMapper` | 404 | Evidence not found |
| `RecommendationNotFoundExceptionMapper` | 404 | Recommendation not found |
| `DecisionNotFoundExceptionMapper` | 404 | Decision not found |
| `AppealNotFoundExceptionMapper` | 404 | Appeal not found |
| `WorkflowTaskNotFoundExceptionMapper` | 404 | Task not found |
| `ReportConflictExceptionMapper` | 409 | Report conflicts |
| `CaseConflictExceptionMapper` | 409 | Case conflicts |
| `EvidenceConflictExceptionMapper` | 409 | Evidence conflicts |
| `RecommendationConflictExceptionMapper` | 409 | Recommendation conflicts |
| `DecisionConflictExceptionMapper` | 409 | Decision conflicts |
| `AppealConflictExceptionMapper` | 409 | Appeal conflicts |
| `WorkflowTaskConflictExceptionMapper` | 409 | Task conflicts |
| `WorkflowReconciliationConflictExceptionMapper` | 409 | Reconciliation conflicts |
| `MaintenanceOperationConflictExceptionMapper` | 409 | Maintenance conflicts |
| `EvidenceObjectMissingExceptionMapper` | 404 | Missing MinIO object |
| `EvidenceStorageUnavailableExceptionMapper` | 503 | MinIO unavailable |
| `GenericExceptionMapper` | 500 | Unhandled exceptions |

### 6.3 Filters

| Filter | Purpose |
|---|---|
| `CorrelationIdFilter` | Extracts `X-Correlation-Id` header (or generates UUID), stores in request property for logging |
| `BearerAuthenticationFilter` | Extracts Bearer token from `Authorization` header, calls `TokenVerifier.verify()`, creates `SecurityContext` with `ApplicationActor` |
| `RequestMetricsFilter` | Records request count, duration histogram, status code distribution via `InMemoryMetricsRecorder` |

### 6.4 JSON

`ObjectMapperContextResolver` — Registers Jackson `ObjectMapper` with OpenAPI module for correct serialization of generated types.

---

## 7. Module Reference: sentinel-persistence (MyBatis)

**Package**: `com.sentinel.enforcement.persistence`

### 7.1 Architecture

```
PersistenceModule.java
  └── createSqlSessionFactory(DataSource)
        └── Configuration with:
              - UuidTypeHandler (UUID <-> JDBC)
              - mapUnderscoreToCamelCase = true
              - 10 MyBatis Mapper interfaces
```

### 7.2 Transaction Management

`MyBatisTransactionManager` implements `ApplicationTransactionManager`:
- **Thread-local session**: `MyBatisSessionContext` binds/unbinds `SqlSession` per thread
- **Nested call support**: If a session is already bound, reuses it (no nested transaction)
- **Rollback**: Catches `RuntimeException` and `Error`, calls `session.rollback()`
- **Isolation** mapping: READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE

### 7.3 MyBatis Mappers (10)

| Mapper Interface | Repository Adapter | Tables |
|---|---|---|
| `CaseMyBatisMapper` | `CaseRepositoryMyBatisAdapter` | `case_record`, `case_assignment`, `case_status_history`, `case_relationship`, `audit_event` |
| `ReportMyBatisMapper` | `ReportRepositoryMyBatisAdapter` | `report` |
| `EvidenceMyBatisMapper` | `EvidenceRepositoryMyBatisAdapter` | `evidence`, `evidence_version`, `evidence_upload_session` |
| `RecommendationMyBatisMapper` | `RecommendationRepositoryMyBatisAdapter` | `recommendation`, `recommendation_review` |
| `DecisionMyBatisMapper` | `DecisionRepositoryMyBatisAdapter` | `decision`, `decision_version` |
| `AppealMyBatisMapper` | `AppealRepositoryMyBatisAdapter` | `appeal`, `appeal_decision` |
| `MessagingMyBatisMapper` | `OutboxRepositoryMyBatisAdapter`, `InboxRepositoryMyBatisAdapter`, `NotificationRepositoryMyBatisAdapter` | `outbox_event`, `inbox_event`, `notification_record` |
| `MaintenanceOperationMyBatisMapper` | `MaintenanceOperationRepositoryMyBatisAdapter` | `maintenance_operation_run`, `sanction_obligation` |
| `WorkflowInstanceMyBatisMapper` | `WorkflowInstanceMyBatisAdapter` | `workflow_instance` |
| `WorkflowReconciliationMyBatisMapper` | `WorkflowReconciliationMyBatisAdapter` | (query-based views) |

### 7.4 Key Persistence Patterns

- **Cursor-based pagination**: List queries use cursor comparison (`WHERE cursor_column > ?`) rather than `OFFSET`
- **Optimistic locking**: All `UPDATE` statements include `WHERE version = ?` and check affected rows
- **`SELECT ... FOR UPDATE SKIP LOCKED`**: Outbox publisher uses this for concurrent-safe row leasing
- **Dynamic SQL**: Case listing and task listing use `<choose>` / `<when>` / `<if>` MyBatis dynamic SQL for search filters, sort columns, and authorization predicates

### 7.5 Exception Classification

`PersistenceExceptionClassifier` — Maps:
- `org.apache.ibatis.exceptions.PersistenceException` → Checked for constraint violation (duplicate key, foreign key)
- `java.sql.SQLIntegrityConstraintViolationException` → Domain-specific conflict exceptions
- Generic → `IllegalStateException`

### 7.6 Liquibase Changelog

Located at: `src/main/resources/db/changelog/`

Contains changesets for:
- All domain tables with UUID primary keys, foreign keys, indexes
- OUTBOX_EVENT table with `status` enum, `retry_at` index, `leased_until` for leasing
- INBOX_EVENT table with unique constraint on `(consumer_name, event_id)`
- NOTIFICATION_RECORD table with status tracking
- WORKFLOW_INSTANCE table for Camunda correlation
- MAINTENANCE_OPERATION_RUN and SANCTION_OBLIGATION tables

---

## 8. Module Reference: sentinel-messaging (Kafka)

**Package**: `com.sentinel.enforcement.messaging`

### 8.1 Active Kafka Topics

| Topic | Key | Produced By | Consumed By |
|---|---|---|---|
| `case.lifecycle.v1` | `caseId` | OutboxPublisher | External systems |
| `case.assignment.v1` | `caseId` | OutboxPublisher | External systems |
| `evidence.lifecycle.v1` | `evidenceId` | OutboxPublisher | External systems |
| `decision.lifecycle.v1` | `decisionId` | OutboxPublisher | External systems |
| `sanction.lifecycle.v1` | `sanctionId` | OutboxPublisher | External systems |
| `appeal.lifecycle.v1` | `appealId` | OutboxPublisher | External systems |
| `notification.command.v1` | — | OutboxPublisher | NotificationConsumer |
| `notification.result.v1` | — | NotificationConsumer | OutboxPublisher → External |
| `audit.integration.v1` | — | OutboxPublisher | External audit systems |

Each topic has automatic `.retry` and `.dlq` variants created by `TopicProvisioner`.

### 8.2 KafkaOutboxPublisher

**Role**: Background daemon thread that polls the `outbox_event` table, publishes to Kafka, and marks events as `PUBLISHED`.

**Algorithm**:
1. `outboxRepository.claimPending()` → `SELECT ... FOR UPDATE SKIP LOCKED` to lease `batchSize` rows (default: 20) with `PENDING` or `(RETRYING AND retry_at <= now)` status
2. For each claimed event:
   - Serialize `EventEnvelope` via `EventEnvelopeJsonCodec`
   - Call `producer.send().get()` (synchronous per-event for ordering)
   - On success: `outboxRepository.markPublished()`
   - On failure: `outboxRepository.releaseForRetry()` with exponential backoff
3. If any failure, call `topicProvisioner.run()` (best-effort topic creation)

**Retry backoff**: `min(60s, 2^min(attempt, 6) seconds)` → 2s, 4s, 8s, 16s, 32s, 60s, 60s...

**Lease mechanism**: Each publisher instance has a unique `leaseOwner` (app instance ID). Leased rows have `leased_until` timestamp; if a publisher crashes, other instances can claim expired leases.

### 8.3 KafkaNotificationConsumer

**Role**: Background daemon thread that subscribes to `notification.command.v1` (and `.retry` topic).

**Algorithm**:
1. Subscribe to `notification.command.v1` and `notification.command.v1.retry` (and notification result topics)
2. Poll with 500ms timeout
3. For each record:
   - Deserialize `EventEnvelope`
   - Determine original topic (via `x-original-topic` header or by stripping `.retry` suffix)
   - Route to `NotificationCommandHandler` (for commands) or `NotificationEventHandler` (for results)
   - On success: commit offset
   - On failure: route to `.retry` or `.dlq` based on retry count

**Retry/DLQ headers**:
- `x-retry-attempt`: Integer counter
- `x-original-topic`: Original topic name
- `x-error`: Exception class name

**Idempotency**: Uses `InboxRepository.ensureConsumed()` which inserts into `inbox_event` with unique constraint `(consumer_name, event_id)`. Duplicate delivery → constraint violation → silently skipped.

### 8.4 MessagingEventFactory

Translates domain events into `EventEnvelope` instances:

| Factory Method | Event Type | Topics |
|---|---|---|
| `auditIntegrated(auditEvent, now)` | `AuditEventRecorded` | `audit.integration.v1` |
| `caseCreated(actor, caseRecord, correlationId, now)` | `CaseCreated` | `case.lifecycle.v1` |
| `caseAssigned(actor, caseRecord, reason, correlationId, now)` | `CaseAssigned` | `case.assignment.v1` |
| `caseTransitioned(actor, caseRecord, fromStatus, reason, correlationId, now)` | `CaseTransitioned` | `case.lifecycle.v1` |
| `evidenceVersionFinalized(actor, evidence, version, correlationId, now)` | `EvidenceVersionFinalized` | `evidence.lifecycle.v1` |
| `decisionPublished(actor, decision, correlationId, now)` | `DecisionPublished` | `decision.lifecycle.v1` |
| `sanctionCreated(actor, sanction, obligation, correlationId, now)` | `SanctionCreated` | `sanction.lifecycle.v1` |
| `sanctionCancelled(actor, sanction, obligation, correlationId, now)` | `SanctionCancelled` | `sanction.lifecycle.v1` |
| `appealFiled(actor, appeal, correlationId, now)` | `AppealFiled` | `appeal.lifecycle.v1` |
| `appealDecided(actor, appeal, decision, correlationId, now)` | `AppealDecided` | `appeal.lifecycle.v1` |

### 8.5 Notification Flow

```text
Domain Change (DB txn)
  └→ INSERT outbox_event { topic: "notification.command.v1", payload: { ... } }

OutboxPublisher (async)
  └→ PUBLISH to notification.command.v1
  
NotificationConsumer (async)
  └→ NotificationCommandHandler.handle()
       ├→ INSERT inbox_event (idempotency guard)
       ├→ NotificationEmailSender.send() → SMTP (Mailpit)
       ├→ UPDATE notification_record.status = 'SENT' | 'FAILED'
       └→ outboxRepository.enqueue(notification.result.v1)
```

---

## 9. Module Reference: sentinel-storage (MinIO)

**Package**: `com.sentinel.enforcement.storage`

### 9.1 MinioEvidenceStorageAdapter

Implements `EvidenceStoragePort` with 4 operations:

| Method | Description |
|---|---|
| `createPresignedUploadUrl(bucket, objectKey, ttl)` | Generates `PUT` presigned URL for direct browser upload |
| `createPresignedDownloadUrl(bucket, objectKey, ttl)` | Generates `GET` presigned URL for secure download |
| `statObject(bucket, objectKey)` | Gets object metadata (size, content-type, etag) for finalize verification |
| `getObjectStream(bucket, objectKey)` | Returns `InputStream` for SHA-256 computation |
| `ensureBucketExists(bucket)` | Creates bucket if not exists (called at startup) |

### 9.2 Evidence Object Key Structure

```
/{jurisdictionCode}/{caseId}/{evidenceId}/{versionNumber}/{uuid}-sanitized-filename.ext

Example: /JKT/550e8400-e29b-41d4-a716-446655440000/660e8400-e29b-41d4-a716-446655440001/1/a1b2c3d4-report.pdf
```

### 9.3 Evidence Lifecycle Flow

```text
1. POST /cases/{caseId}/evidence/upload-sessions
   ├─ Determines version number (1 for new, latest+1 for versioned)
   ├─ Generates randomized filename (UUID + original extension)
   ├─ Computes objectKey
   ├─ Creates DB records (Evidence, EvidenceUploadSession)
   └─ Returns: { evidenceId, sessionId, presignedUploadUrl, expiresAt, objectKey }

2. Client PUT to presignedUploadUrl (directly to MinIO)
   └─ Stores file in MinIO

3. POST /evidence/{evidenceId}/versions/finalize
   ├─ statObject → verifies size + mediaType match session contract
   ├─ getObjectStream → computes SHA-256 digest, verifies match
   ├─ finalizeSession() → marks session as FINALIZED
   ├─ evidence.activate() → marks Evidence as ACTIVE
   ├─ Creates EvidenceVersion record
   ├─ Persists in single DB transaction
   └─ Returns: { evidence, version }

4. POST /evidence/{evidenceId}/download-sessions
   ├─ Authorization check (audits even on DENIED!)
   └─ Returns: { presignedDownloadUrl, expiresAt }
```

### 9.4 Verification Checks on Finalize

| Check | What is Verified | Failure Code |
|---|---|---|
| Object existence | `statObject` succeeds | `EVIDENCE_OBJECT_NOT_FOUND` |
| Size | `stored.sizeBytes == session.sizeBytes` | `EVIDENCE_SIZE_MISMATCH` |
| Media type | `stored.mediaType == session.mediaType` (normalized) | `EVIDENCE_MEDIA_TYPE_MISMATCH` |
| SHA-256 | Computed digest == session.sha256Checksum | `EVIDENCE_CHECKSUM_MISMATCH` |

---

## 10. Module Reference: sentinel-workflow (Camunda BPMN)

**Package**: `com.sentinel.enforcement.workflow`

### 10.1 Architecture

```text
WorkflowModule.start(dataSource, ...)
  └── Creates ProcessEngineConfiguration
       ├── Uses provided DataSource (shared with app)
       ├── databaseSchemaUpdate = false (schema managed externally)
       ├── Creates ProcessEngine
       ├── Deploys BPMN resources (with duplicate filtering)
       └── Returns WorkflowRuntime
             ├── caseWorkflowPort: CamundaCaseWorkflowAdapter
             ├── workflowAdministrationPort: CamundaWorkflowAdministrationAdapter
             ├── ProcessEngineProvider: SingleProcessEngineProvider
             ├── WorkflowReadinessProbe
             └── JobExecutor (thread pool for timers/async)
```

### 10.2 BPMN Process: Main Case Lifecycle

**File**: `regulatory-enforcement-case.bpmn`  
**Process ID**: `regulatoryEnforcementCase`  
**History TTL**: 30 days

**Flow Nodes**: 45+ elements including:

#### Stage 1: Intake & Triage
1. Start Event (message: `CaseCreatedMessage`) — triggered when case is created
2. Pre-Triage Service Task (`preTriageRoutingDelegate`) — validates and enriches
3. Intake Valid Gateway — `intakeValid` → if false, terminate end event
4. **Triage User Task** (candidates: `TRIAGE_OFFICER`, `SUPERVISOR`)
   - Boundary timer: `PT30M` triage SLA warning (non-interrupting)
   - Boundary escalation: supervisor override (non-interrupting)

#### Stage 2: Investigation
5. Inclusive Gateway — selects tracks (default investigation + optional legal + optional financial)
6. **Investigation Subprocess** (`investigationAndEvidenceSubProcess`):
   - Inclusive Gateway → Investigation Task + External Evidence + Legal Advisory + Financial Analysis
   - **Investigation User Task** (candidates: `INVESTIGATOR`, `SUPERVISOR`)
     - Boundary timer: `investigationEscalationDuration` (configurable, default `PT30M`)
     - Escalation → records escalation → throws escalation event
   - External Evidence: Send Task → gateway (auto or wait for message `ExternalEvidenceDelivered`)
   - Legal Advisory User Task
   - Financial Analysis Script Task (JavaScript)
   - Evidence Sufficiency Rule Task
   - Conditional Event: `evidenceSufficient == true`
   - Inclusive Gateway join
   - Additional Evidence Gateway
7. Post-Investigation Inclusive Gateway

#### Stage 3: Recommendation & Review
8. **Recommendation & Multi-Party Review Subprocess**:
   - Inclusive Gateway → Review Task + optional Supervisor Review
   - **Review User Task** (candidates: `CASE_REVIEWER`, `SUPERVISOR`)
   - **Supervisor Review User Task** (candidates: `SUPERVISOR`)
   - Review Outcome Gateway → Approved (continue) or Requires Revision (loop back)
   - **Revise Recommendation User Task** (candidates: `INVESTIGATOR`, `SUPERVISOR`)

#### Stage 4: Decision & Sanction
9. **Decision & Sanction Publication Subprocess**:
   - **Decision User Task** (candidates: `DECISION_MAKER`, `SUPERVISOR`)
   - Violation Proven Gateway → No Sanction (fast path) or Sanction Publication
   - **Transaction Subprocess** `publishSanctionTransaction`:
     - Determine Sanction Package → Send to Registry
     - Registry Ack Gateway (auto or wait for message)
     - Post-Publication Parallel Split:
       - Send Notification Command
       - Create Obligation Schedule
     - Notification Result Gateway (auto or wait)
     - Failure paths: resend → review → manual notification or abort
     - Boundary events: Cancel → compensation, Error → compensation
   - Event-Based Gateway: Wait for Appeal/Expiry:
     - **Appeal Filed Message** → Wait for Appeal Resolution
     - **Appeal Period Expired Timer** (P14D — 14 days)
     - **Global Hold Signal**

#### Stage 5: Post-Decision
10. Post-Decision Gateway → Close Without Sanction or Enforcement Monitoring
11. **Enforcement Monitoring Subprocess** (parallel):
    - Monitor Payment Obligation
    - Monitor Corrective Action
    - Monitor Reporting Obligation
    - Conditional Event: `allObligationsComplete == true`
    - Obligation Breach Gateway → Escalate or Complete
12. Close After Enforcement End Event

#### Global: Supervisor Override
13. Event Subprocess `supervisorOverrideEventSubProcess` — triggered by any escalation event
    - **Supervisor Override Review User Task** (candidates: `SUPERVISOR`, `SYSTEM_ADMIN`)
    - Override Outcome → Cancel Case, Suspend Case, or Continue

### 10.3 BPMN Process: Decision Appeal Review

**File**: `decision-appeal-review.bpmn`  
Separate sub-process for handling the appeal review workflow.

### 10.4 Java Delegates (3)

| Delegate | Bean Name | Purpose |
|---|---|---|
| `PreTriageRoutingDelegate` | `preTriageRoutingDelegate` | Validates intake data, enriches routing variables |
| `InvestigationEscalationDelegate` | `investigationEscalationDelegate` | Records investigation SLA breach, logs event |
| `MockWorkflowServiceDelegate` | `mockWorkflowServiceDelegate` | Simulates external system calls (evidence delivery, registry, notification) |

### 10.5 Task Definition Keys (18)

| Key | BPMN Element | Completes Case Transition |
|---|---|---|
| `triageTask` | Triage User Task | CREATED → UNDER_TRIAGE → UNDER_INVESTIGATION |
| `investigationTask` | Investigate Case | UNDER_INVESTIGATION → PENDING_REVIEW |
| `optionalLegalAdvisoryTask` | Optional Legal Advisory | (no domain transition) |
| `financialReviewTask` | Financial Analysis Review | (no domain transition) |
| `legalAdvisoryTask` | Request Legal Advisory | (no domain transition) |
| `reviewTask` | Review Recommendation | PENDING_REVIEW → PENDING_DECISION |
| `supervisorReviewTask` | Supervisor Review | (no domain transition) |
| `recommendationRevisionTask` | Revise Recommendation | (no domain transition) |
| `decisionTask` | Approve Decision | PENDING_DECISION → DECIDED |
| `reviewRegistryFailureTask` | Review Registry Failure | (no domain transition) |
| `reviewNotificationFailureTask` | Review Notification Failure | (no domain transition) |
| `supervisorOverrideReviewTask` | Supervisor Override Review | (no domain transition) |
| `globalHoldOverrideReviewTask` | Global Hold Override Review | (no domain transition) |
| `monitorPaymentObligationTask` | Monitor Payment Obligation | (triggers auto-close after all done) |
| `monitorCorrectiveActionTask` | Monitor Corrective Action | (triggers auto-close after all done) |
| `monitorReportingObligationTask` | Monitor Reporting Obligation | (triggers auto-close after all done) |
| `additionalEnforcementActionTask` | Escalate Additional Enforcement | (triggers auto-close after all done) |
| `appealReviewTask` | (in appeal sub-process) | DECIDED → CLOSED or ENFORCEMENT_IN_PROGRESS |
| `appealSupervisorOverrideReviewTask` | (in appeal sub-process) | (no domain transition) |

### 10.6 Workflow Reconciliation

Detects and repairs mismatches between domain state (`case_record.status`) and workflow state (Camunda runtime/history):

| Issue Type | Detection | Action |
|---|---|---|
| `RUNTIME_MISSING` | Domain has active status but no running process instance | Relaunch workflow |
| `RUNTIME_EXTRA` | Running process instance but domain is terminal | Terminate process instance |
| `HISTORY_MISSING` | Domain has terminal status but process not ended | Clean correlation record |
| `HISTORY_EXTRA` | Terminal process but domain not terminal | Repair domain state |

---

## 11. Module Reference: sentinel-security (Keycloak)

**Package**: `com.sentinel.enforcement.security`

### 11.1 KeycloakTokenVerifier

Implements `TokenVerifier`:

```java
ApplicationActor verify(String bearerToken)
```

**Algorithm**:
1. Validate token not null/blank
2. Process via `DefaultJWTProcessor` with `RemoteJWKSet` (fetches JWKS from Keycloak at construction)
3. Validate claims:
   - Issuer must match configured `KEYCLOAK_ISSUER`
   - Audience must contain configured `KEYCLOAK_AUDIENCE`
   - Expiration time must be in the future
   - Not-before time must be in the past (if present)
4. Extract custom JWT claims:
   - `realm_access.roles` → `Set<String> roles`
   - `jurisdictions` → `Set<String> jurisdictions`
   - `assigned_units` → `Set<String> assignedUnits`
   - `case_classifications` → `Set<CaseClassification>` (default: all if absent)
   - `conflicted_actor_ids` → `Set<String>` for conflict-of-interest
5. Return `ApplicationActor`

### 11.2 RoleBasedAuthorizationService

Implements `AuthorizationService`:

```java
void requirePermission(ApplicationActor actor, Permission permission, AuthorizationContext context)
```

**Evaluation Order** (7 axes):

| Order | Check | Bypass Conditions |
|---|---|---|
| 1 | `SYSTEM_ADMIN` role | Always pass |
| 2 | Role → Permission mapping (see Section 15) | Actor has any required role |
| 3 | Jurisdiction match | `context.jurisdictionCode()` is null or actor has it |
| 4 | Classification clearance | `context.caseClassification()` is null or actor has clearance |
| 5 | Conflict-of-interest | `context.resourceOwnerId()` not in actor's conflicted list |
| 6 | Assigned unit scope | Case's `assignedUnitId` matches actor's `assignedUnits` |
| 7 | Direct assignment | For INVESTIGATOR-only actors: `actor.username == case.assigneeUserId` |

### 11.3 Role → Permission Mapping (27 Permissions)

```text
CREATE_REPORT                    → CASE_INTAKE_OFFICER
READ_REPORT                      → CASE_INTAKE_OFFICER, TRIAGE_OFFICER, AUDITOR
TRIAGE_REPORT                    → TRIAGE_OFFICER, SUPERVISOR
CREATE_CASE                      → TRIAGE_OFFICER, SUPERVISOR
READ_CASE, LIST_CASES            → TRIAGE_OFFICER, INVESTIGATOR, CASE_REVIEWER, DECISION_MAKER, APPEAL_OFFICER, SUPERVISOR, AUDITOR
ASSIGN_CASE                      → TRIAGE_OFFICER, SUPERVISOR
TRANSITION_CASE                  → TRIAGE_OFFICER, INVESTIGATOR, CASE_REVIEWER, DECISION_MAKER, APPEAL_OFFICER, SUPERVISOR
READ_CASE_AUDIT                  → SUPERVISOR, AUDITOR
CREATE_EVIDENCE_UPLOAD_SESSION   → TRIAGE_OFFICER, INVESTIGATOR, CASE_REVIEWER, DECISION_MAKER, APPEAL_OFFICER, SUPERVISOR, AUDITOR
FINALIZE_EVIDENCE                → same as above
READ_EVIDENCE                    → same as above
CREATE_EVIDENCE_DOWNLOAD_SESSION → same as above
CREATE_RECOMMENDATION            → INVESTIGATOR, SUPERVISOR
SUBMIT_RECOMMENDATION            → INVESTIGATOR, SUPERVISOR
REVIEW_RECOMMENDATION            → CASE_REVIEWER, SUPERVISOR
CREATE_DECISION                  → DECISION_MAKER, SUPERVISOR
APPROVE_DECISION                 → DECISION_MAKER, SUPERVISOR
PUBLISH_DECISION                 → DECISION_MAKER, SUPERVISOR
CREATE_APPEAL                    → APPEAL_OFFICER, SUPERVISOR
DECIDE_APPEAL                    → APPEAL_OFFICER, SUPERVISOR
LIST_TASKS, CLAIM_TASK, COMPLETE_TASK → TRIAGE_OFFICER, INVESTIGATOR, CASE_REVIEWER, DECISION_MAKER, APPEAL_OFFICER, SUPERVISOR
RECONCILE_WORKFLOW               → SUPERVISOR
MANAGE_CASE_RELATIONSHIPS        → TRIAGE_OFFICER, SUPERVISOR
RUN_MAINTENANCE_OPERATION        → SUPERVISOR
```

---

## 12. Module Reference: sentinel-observability

**Package**: `com.sentinel.enforcement.observability`

### 12.1 Health Checks

`CompositeHealthStatusService` aggregates:

| Health Check | Type | Measures |
|---|---|---|
| `DatabaseDependencyHealthCheck` | JDBC | `SELECT 1` against PostgreSQL via HikariCP |
| `SocketDependencyHealthCheck` | TCP Socket | Kafka (9092), Redis (6379), Mailpit (1025) |
| `WorkflowDependencyHealthCheck` | Custom | Camunda ProcessEngine is not null, job executor active |

Health response format:
```json
{
  "status": "UP",
  "database": "UP",
  "dependencies": [
    { "name": "kafka", "status": "UP" },
    { "name": "redis", "status": "UP" },
    { "name": "mailpit", "status": "UP" },
    { "name": "workflow", "status": "UP" }
  ],
  "timestamp": "2026-07-22T12:00:00Z"
}
```

### 12.2 Metrics

`InMemoryMetricsRecorder` — In-memory request metrics:
- Request count (total)
- Duration histogram (bucketed)
- Status code distribution

---

## 13. Module Reference: sentinel-bootstrap

**Package**: `com.sentinel.enforcement.bootstrap`

### 13.1 Startup Sequence

```text
SentinelMain.main()
  └── ApplicationRuntime.start(configuration)
        ├── create HikariDataSource
        ├── PersistenceModule.createSqlSessionFactory(dataSource)
        ├── Wire all repositories (MyBatis adapters)
        ├── Create MinIO adapter, ensure bucket exists
        ├── WorkflowModule.start(dataSource, ...) → WorkflowRuntime
        │     └── Create ProcessEngine, deploy BPMN, start job executor
        ├── Create KeycloakTokenVerifier
        ├── Create all Application Services
        ├── Create CompositeHealthStatusService
        ├── MessagingRuntime.start(...) → start outbox publisher + notification consumer daemons
        ├── Create ResourceConfig with:
        │     ├── ApplicationBinder (HK2 wiring of all services)
        │     ├── Resources (12 resource classes)
        │     ├── Filters (CorrelationId, BearerAuth, RequestMetrics)
        │     └── Exception Mappers (26 mappers)
        ├── GrizzlyHttpServerFactory.createHttpServer(...)
        ├── server.start()
        └── Thread.currentThread().join()
```

### 13.2 HK2 Binder (ApplicationBinder)

Binds these interfaces to implementations for Jersey DI:
- `HealthStatusService`
- `CaseApplicationService`, `EvidenceApplicationService`, `ReportApplicationService`
- `RecommendationApplicationService`, `DecisionApplicationService`, `AppealApplicationService`
- `WorkflowTaskApplicationService`, `WorkflowReconciliationApplicationService`
- `MaintenanceOperationApplicationService`
- `AuthorizationService`, `TokenVerifier`

### 13.3 Migration Runners

| Class | Command | Purpose |
|---|---|---|
| `LiquibaseMigrator` | `make migrate` | Apply Liquibase changelogs |
| `CamundaSchemaMigrator` | `make migrate` | Execute Camunda SQL schema scripts |
| `DatabaseMigrationMain` | `make migrate` | Combined migration entry point |
| `DatabaseRollbackMain` | `make rollback` | Rollback N Liquibase changesets |

---

## 14. Module Reference: sentinel-integration-tests

**Package**: `com.sentinel.enforcement.integration`

### 14.1 Integration Tests (Testcontainers)

| Test Class | Infrastructure | What It Tests |
|---|---|---|
| `ApplicationRuntimeSchemaLifecycleIT` | PostgreSQL | Schema migration from scratch |
| `ReportApiIT` | PostgreSQL + Keycloak | Report CRUD, triage, auth |
| `CaseApiIT` | PostgreSQL + Keycloak | Case lifecycle, assignments, transitions, audit, authorization matrix (wrong role, jurisdiction, unit, clearance, conflict) |
| `EvidenceApiIT` | PostgreSQL + Keycloak + MinIO | Upload session, presigned URL, finalize, checksum mismatch, download session |
| `WorkflowTaskApiIT` | PostgreSQL + Keycloak + Camunda | Task list/claim/complete, cursor, search, sort, duplicate-completion safety |
| `WorkflowReconciliationApiIT` | PostgreSQL + Keycloak + Camunda | Detect/repair workflow mismatches |
| `MessagingReliabilityIT` | PostgreSQL + Kafka | Outbox reliability during Kafka outage, inbox deduplication |

### 14.2 Karate Acceptance Tests

| Suite | File | Coverage |
|---|---|---|
| `KarateSmokeIT` | `karate/smoke/` | Health, login, report intake, triage, baseline API |
| `KarateRegressionIT` | `karate/regression/` | Smoke + workflow tasks, evidence, appeal, maintenance, reconciliation |
| `KarateFullIT` | `karate/full/` | Full regression + search/cursor matrix, auth denial matrix, relationships, locking, duplicate-delivery |

### 14.3 Test Infrastructure Management

`AbstractApiIT` — Base class for API integration tests. Manages Testcontainers lifecycle:
- PostgreSQL
- Keycloak (with realm import)
- MinIO
- Redis
- Mailpit
- Kafka (via `KafkaContainer`)

---

## 15. Authorization Model (7-Axis)

### 15.1 Overview

The authorization model is evaluated in this fixed order. Each axis can independently deny the request:

```
Request → [1. Admin Bypass?]
              ↓ no
         → [2. Role-Permission?]
              ↓ pass
         → [3. Jurisdiction?]
              ↓ pass
         → [4. Classification?]
              ↓ pass
         → [5. Conflict-of-Interest?]
              ↓ pass
         → [6. Assigned Unit?]
              ↓ pass
         → [7. Direct Assignment?]
              ↓ pass
         → ALLOW
```

### 15.2 Axis Details

#### Axis 1: Admin Bypass
- If actor has `SYSTEM_ADMIN` role → **ALLOW** immediately (all remaining checks skipped)

#### Axis 2: Role → Permission Mapping
- Each `Permission` enum value maps to one or more required roles
- Actor's role set must intersect with required roles
- See Section 11.3 for complete mapping

#### Axis 3: Jurisdiction Match
- `AuthorizationContext.jurisdictionCode()` (from case's `jurisdictionCode`)
- If null → skip
- Otherwise: `actor.hasJurisdiction(jurisdictionCode)` must return true

#### Axis 4: Classification Clearance
- `AuthorizationContext.caseClassification()` (from case's `classification`)
- If null → skip
- Otherwise: `actor.hasCaseClassification(classification)` must return true
- Actor's `caseClassifications` claim from JWT (default: all 3 levels if absent)
- Hierarchy: `PUBLIC` ⊆ `CONFIDENTIAL` ⊆ `SECRET`

#### Axis 5: Conflict-of-Interest
- `AuthorizationContext.resourceOwnerId()` (from case's `createdBy`)
- If null → skip
- Otherwise: `actor.isConflictedWith(resourceOwnerId)` must return false
- JWT claim: `conflicted_actor_ids: ["investigator-jkt", ...]`

#### Axis 6: Assigned Unit Scope
- Uses `CaseAuthorizationScope.RESTRICTED_TO_ASSIGNED_UNITS_WHEN_PRESENT`
- If case has no `assignedUnitId` → skip (unless `TRIAGE_OFFICER` or `SUPERVISOR` which can see unassigned)
- If actor has no `assignedUnits` → skip
- Otherwise: `actor.hasAssignedUnit(case.assignedUnitId)` must return true
- Unit-scoped roles: TRIAGE_OFFICER, INVESTIGATOR, CASE_REVIEWER, DECISION_MAKER, APPEAL_OFFICER, SUPERVISOR
- Non-scoped: SYSTEM_ADMIN, AUDITOR

#### Axis 7: Direct Assignment
- Only enforced when actor has `INVESTIGATOR` role AND does NOT have SUPERVISOR/TRIAGE_OFFICER/CASE_REVIEWER/DECISION_MAKER/APPEAL_OFFICER/AUDITOR roles
- Requires: `actor.username == case.assigneeUserId`
- Applies to: READ_CASE, TRANSITION_CASE, all EVIDENCE permissions, RECOMMENDATION permissions

### 15.3 Authorization Context Construction

For case-scoped operations, context is constructed from the `CaseRecord`:

```java
new AuthorizationContext(
    caseRecord.jurisdictionCode(),          // Axis 3
    "CASE",                                  // resourceType
    caseRecord.id().toString(),              // resourceId
    caseRecord.id(),                         // caseId
    caseRecord.assigneeUserId(),             // Axis 7
    caseRecord.assignedUnitId(),             // Axis 6
    caseRecord.classification(),             // Axis 4
    caseRecord.createdBy(),                  // Axis 5
    CaseAuthorizationScope.RESTRICTED_TO_ASSIGNED_UNITS_WHEN_PRESENT  // Axis 6 mode
);
```

For non-case resources (reports, maintenance ops), a simplified context with only jurisdiction and resourceId is used.

### 15.4 Task Visibility

Task listing (`GET /api/v1/tasks`) applies authorization differently:
1. Actor must have `LIST_TASKS` permission
2. Each task is filtered through the full authorization check (same 7 axes)
3. Additionally, task-specific role checks enforce that only appropriate roles see each task type:
   - `triageTask` → TRIAGE_OFFICER or SUPERVISOR
   - `investigationTask` → INVESTIGATOR (assigned) or SUPERVISOR
   - `reviewTask` → CASE_REVIEWER or SUPERVISOR
   - `decisionTask` → DECISION_MAKER or SUPERVISOR
   - `reviewRegistryFailureTask` / `reviewNotificationFailureTask` → SUPERVISOR or SYSTEM_ADMIN
   - `monitorPaymentObligationTask` / etc. → CASE_REVIEWER or SUPERVISOR
   - `appealReviewTask` → APPEAL_OFFICER or SUPERVISOR

---

## 16. Case Lifecycle State Machine

### 16.1 Complete Transition Matrix

| From → To | Allowed? | Required Roles |
|---|---|---|
| CREATED → UNDER_TRIAGE | ✅ | TRIAGE_OFFICER, SUPERVISOR |
| CREATED → CANCELLED | ✅ | TRIAGE_OFFICER, SUPERVISOR |
| UNDER_TRIAGE → UNDER_INVESTIGATION | ✅ | TRIAGE_OFFICER, SUPERVISOR |
| UNDER_TRIAGE → CANCELLED | ✅ | TRIAGE_OFFICER, SUPERVISOR |
| UNDER_INVESTIGATION → PENDING_REVIEW | ✅ | INVESTIGATOR, SUPERVISOR |
| UNDER_INVESTIGATION → CANCELLED | ✅ | INVESTIGATOR, SUPERVISOR |
| PENDING_REVIEW → UNDER_INVESTIGATION | ✅ | CASE_REVIEWER, SUPERVISOR |
| PENDING_REVIEW → PENDING_DECISION | ✅ | CASE_REVIEWER, SUPERVISOR |
| PENDING_DECISION → UNDER_INVESTIGATION | ✅ | DECISION_MAKER, SUPERVISOR |
| PENDING_DECISION → DECIDED | ✅ | DECISION_MAKER, SUPERVISOR |
| DECIDED → UNDER_APPEAL | ✅ | APPEAL_OFFICER, SUPERVISOR |
| DECIDED → ENFORCEMENT_IN_PROGRESS | ✅ | DECISION_MAKER, SUPERVISOR |
| UNDER_APPEAL → DECIDED | ✅ | APPEAL_OFFICER, SUPERVISOR |
| UNDER_APPEAL → ENFORCEMENT_IN_PROGRESS | ✅ | APPEAL_OFFICER, SUPERVISOR |
| UNDER_APPEAL → CLOSED | ✅ | APPEAL_OFFICER, SUPERVISOR |
| ENFORCEMENT_IN_PROGRESS → CLOSED | ✅ | SUPERVISOR |
| CLOSED → anything | ❌ | Terminal |
| CANCELLED → anything | ❌ | Terminal |

### 16.2 Enforcement Logic

The `CaseProgressionGuard` enforces business prerequisites before transitions:

| Target Status | Prerequisite |
|---|---|
| `UNDER_INVESTIGATION` | Report must be triaged |
| `PENDING_REVIEW` | Recommendation must be submitted |
| `PENDING_DECISION` | Recommendation must be approved |
| `DECIDED` | Decision must be published |

### 16.3 Case Number Format

Generated by `caseRepository.nextCaseNumber(jurisdictionCode, year)`:
- Pattern: `{jurisdictionCode}-{year}-{sequentialNumber}`
- Example: `JKT-2026-00042`

---

## 17. Evidence Lifecycle

### 17.1 Complete Evidence State Machine

```text
                                ┌──────────────┐
                                │  Evidence     │
                                │  (created)    │
                                │ PENDING_UPLOAD│
                                │ latestVer: 0  │
                                └──────┬───────┘
                                       │
                               ┌───────▼────────┐
                               │ Upload Session  │
                               │ (PENDING)       │
                               │ expiresAt: now+ │
                               │   TTL           │
                               │ targetVer: 1    │
                               └───────┬────────┘
                                       │ Client uploads directly
                                       │ to MinIO via presigned URL
                                       │
                               ┌───────▼────────┐
                               │ Finalize        │
                               │ Verifies:       │
                               │  • Object exists│
                               │  • Size matches │
                               │  • MediaType    │
                               │  • SHA-256      │
                               └───────┬────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    ▼                  ▼                  ▼
            ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
            │ Evidence     │  │ Upload       │  │ Evidence     │
            │ (ACTIVE)     │  │ Session      │  │ Version     │
            │ latestVer: 1 │  │ (FINALIZED)  │  │ verNum: 1   │
            └──────────────┘  └──────────────┘  └──────────────┘
```

### 17.2 Versioning

- New evidence starts at version 1
- Uploading a new version increments `latestVersion + 1`
- Each version creates a separate entry in `evidence_version` table
- Version object key: `/{jurisdiction}/{caseId}/{evidenceId}/{versionNumber}/{filename}`
- Title and classification are immutable across versions (enforced in application service)

---

## 18. Messaging & Reliability Architecture

### 18.1 Pattern: Transactional Outbox

**Purpose**: Guarantee at-least-once event delivery without distributed transactions (2PC).

**Flow**:
```text
1. Business Transaction (ACID):
   ┌─────────────────────────────────────┐
   │  BEGIN TRANSACTION                  │
   │    UPDATE case_record SET ...       │
   │    INSERT INTO audit_event (...)    │
   │    INSERT INTO outbox_event (       │
   │      event_id, topic, message_key,  │
   │      serialized_envelope,           │
   │      status = 'PENDING'             │
   │    )                                │
   │  COMMIT                             │
   └─────────────────────────────────────┘
                    │
2. OutboxPublisher (async daemon):
   ┌─────────────────────────────────────┐
   │  BEGIN TRANSACTION                  │
   │    SELECT ... FROM outbox_event     │
   │    WHERE status = 'PENDING'         │
   │    FOR UPDATE SKIP LOCKED           │
   │    LIMIT batch_size                 │
   │    UPDATE status = 'LEASED'         │
   │  COMMIT                             │
   │                                     │
   │  For each leased row:               │
   │    kafkaProducer.send(topic, key,   │
   │      serialized_envelope)           │
   │                                     │
   │  On success:                        │
   │    UPDATE status = 'PUBLISHED'      │
   │  On failure:                        │
   │    UPDATE status = 'RETRYING',      │
   │      retry_at = now + backoff       │
   └─────────────────────────────────────┘
```

**Benefits**:
- Business data and events in same DB transaction → strong consistency
- Kafka outage does NOT roll back the business operation
- Events are retried with exponential backoff
- Multiple publisher instances coordinate via `SKIP LOCKED`

### 18.2 Pattern: Idempotent Consumer (Inbox)

**Purpose**: Guarantee at-most-once side effects for event consumers.

**Flow**:
```text
Kafka message received
  ├→ INSERT INTO inbox_event (consumer_name, event_id, status = 'RECEIVED')
  │     ON CONFLICT (consumer_name, event_id) DO NOTHING
  │     → If conflict: skip (duplicate already processed)
  │
  ├→ Process event (send email, update status, etc.)
  │
  └→ UPDATE inbox_event SET status = 'PROCESSED'
```

### 18.3 Failure Handling

| Scenario | Mechanism |
|---|---|
| Kafka broker down during publish | Outbox row remains PENDING → retried on next poll |
| Application crash mid-publish | Leased rows expire → re-claimed by another instance |
| Event processing fails | Moved to `.retry` topic, retry count tracked in header |
| Max retries exceeded | Moved to `.dlq` (dead letter queue), permanent failure logged |
| Duplicate Kafka delivery | Inbox table unique constraint → silent skip |
| Race condition on claim | `SKIP LOCKED` prevents concurrent processing |
| Topic not yet created | `TopicProvisioner` creates topics + `.retry` + `.dlq` on startup and on failure |

### 18.4 Topic Structure

For each logical topic (e.g., `notification.command.v1`):
- `notification.command.v1` — Main topic
- `notification.command.v1.retry` — Events that failed (with `x-retry-attempt` header)
- `notification.command.v1.dlq` — Events that exceeded max retries

---

## 19. BPMN Workflow Detail

### 19.1 Main Process: `regulatoryEnforcementCase`

**Start trigger**: Message `CaseCreatedMessage` (correlated when case is created)

**BPMN elements count**:
| Element Type | Count |
|---|---|
| Start Events | 1 (main) + 4 (subprocess) + 1 (event subprocess) |
| End Events | 5 (main) + 6 (subprocess) |
| User Tasks | 15 |
| Service Tasks | 9 (3 delegates + 6 mock delegates) |
| Send Tasks | 3 (external evidence, registry, notification) |
| Receive Tasks | 3 (external evidence delivery, registry ack, notification result) |
| Script Tasks | 1 (financial analysis) |
| Gateways (Exclusive) | 12 |
| Gateways (Inclusive) | 4 |
| Gateways (Parallel) | 3 |
| Gateways (Event-Based) | 1 |
| Boundary Events | 6 |
| Intermediate Events | 10 |
| Subprocesses | 5 |
| Data Objects | 4 |
| Data Stores | 4 |

### 19.2 BPMN Messages (7)

| Message | Used By | Triggered By |
|---|---|---|
| `CaseCreatedMessage` | Start Event | Case creation |
| `ExternalEvidenceDelivered` | Receive Task | External system callback |
| `SanctionRegistryAcknowledged` | Receive Task | External registry system |
| `NotificationResultReceived` | Receive Task | Notification delivery result |
| `AppealFiled` | Intermediate Catch | Appeal filing |
| `AppealResolved` | Intermediate Catch | Appeal decision |
| `Supervisor Override Escalation` | Escalation Events | Timer or user escalation |

### 19.3 BPMN Signals (1)

| Signal | Used By | Purpose |
|---|---|---|
| `GlobalHoldSignal` | Intermediate Catch (Global Hold) | Triggers case hold from external event |

### 19.4 BPMN Data Stores (4)

| Store | Purpose |
|---|---|
| `Case Database` | Application database |
| `Evidence Object Store` | MinIO evidence storage |
| `Sanction Registry Store` | External sanction registry |
| `Notification Store` | Notification records |

---

## 20. Database Schema

### 20.1 Domain Tables

**`report`**: id(UUID), title, description, jurisdiction_code, reporter_name, status(ENUM), created_at, created_by, updated_at, updated_by, version(BIGINT)

**`case_record`**: id(UUID), case_number(UNIQUE), report_id(FK→report), title, summary, jurisdiction_code, classification(ENUM), status(ENUM), assigned_unit_id, assignee_user_id, created_at, created_by, updated_at, updated_by, version(BIGINT)

**`case_assignment`**: id(UUID), case_id(FK→case_record), assigned_unit_id, assignee_user_id, assignment_reason, assigned_at, assigned_by, created_at, created_by, updated_at, updated_by, version(BIGINT)

**`case_status_history`**: id(UUID), case_id(FK→case_record), from_status(ENUM), to_status(ENUM), transition_reason, transitioned_at, transitioned_by, created_at, created_by

**`case_relationship`**: id(UUID), parent_case_id(FK→case_record), child_case_id(FK→case_record), relationship_type(ENUM), relationship_reason, created_at, created_by, updated_at, updated_by, version(BIGINT)

**`audit_event`**: event_id(UUID), event_type, actor_type, actor_id, actor_roles, action, resource_type, resource_id, case_id(FK→case_record), timestamp, correlation_id, source_ip, result, reason, before_summary, after_summary, metadata

**`evidence`**: id(UUID), case_id(FK→case_record), title, classification(ENUM), storage_status(ENUM), latest_version(INT), created_at, created_by, updated_at, updated_by, version(BIGINT)

**`evidence_version`**: id(UUID), evidence_id(FK→evidence), version_number(INT), original_filename, generated_filename, bucket, object_key, media_type, size_bytes(BIGINT), sha256_checksum, uploaded_at, uploaded_by, created_at, created_by

**`evidence_upload_session`**: id(UUID), case_id(FK→case_record), evidence_id(FK→evidence), target_version_number, original_filename, generated_filename, bucket, object_key, media_type, size_bytes(BIGINT), sha256_checksum, classification(ENUM), status(ENUM), expires_at, created_at, created_by, updated_at, updated_by, version(BIGINT)

**`recommendation`**: id(UUID), case_id(FK→case_record), title, summary, proposed_decision, proposed_sanction, status(ENUM), submitted_at, submitted_by, approved_review_id(FK→recommendation_review), created_at, created_by, updated_at, updated_by, version(BIGINT)

**`recommendation_review`**: id(UUID), recommendation_id(FK→recommendation), outcome(ENUM), review_summary, reviewed_at, reviewed_by, created_at, created_by, version(BIGINT)

**`decision`**: id(UUID), case_id(FK→case_record), recommendation_id(FK→recommendation), title, summary, violation_proven(BOOLEAN), sanction_summary, obligation_title, obligation_details, obligation_due_date(DATE), appeal_deadline(DATE), status(ENUM), approved_at, approved_by, published_at, published_by, created_at, created_by, updated_at, updated_by, version(BIGINT)

**`decision_version`**: id(UUID), decision_id(FK→decision), version_number(INT), title, summary, violation_proven, sanction_summary, obligation_title, obligation_details, obligation_due_date, appeal_deadline, published_at, published_by, created_at, created_by

**`sanction`**: id(UUID), case_id(FK→case_record), decision_id(FK→decision), summary, status(ENUM), created_at, created_by, updated_at, updated_by, version(BIGINT)

**`sanction_obligation`**: id(UUID), sanction_id(FK→sanction), title, details, due_date(DATE), status(ENUM), created_at, created_by, updated_at, updated_by, version(BIGINT)

**`appeal`**: id(UUID), case_id(FK→case_record), decision_id(FK→decision), rationale, supervisor_override(BOOLEAN), supervisor_override_reason, status(ENUM), submitted_at, submitted_by, decided_by_appeal_decision_id(FK→appeal_decision), created_at, created_by, updated_at, updated_by, version(BIGINT)

**`appeal_decision`**: id(UUID), appeal_id(FK→appeal), outcome(ENUM), summary, decided_at, decided_by, created_at, created_by, version(BIGINT)

### 20.2 Messaging Tables

**`outbox_event`**: event_id(UUID PK), topic, message_key, serialized_envelope(JSONB), status(ENUM: PENDING/LEASED/PUBLISHED/RETRYING/FAILED), publish_attempts(INT), leased_until(TIMESTAMP), retry_at(TIMESTAMP), created_at, updated_at, created_by

**`inbox_event`**: id(SERIAL), consumer_name(VARCHAR), event_id(UUID), status(ENUM), created_at
- UNIQUE INDEX on `(consumer_name, event_id)`

**`notification_record`**: id(UUID), case_id(FK→case_record), notification_type, title, body, to_email, from_email, channel, status(ENUM), created_at, created_by, updated_at, updated_by, version(BIGINT)

### 20.3 Workflow Tables

**`workflow_instance`**: id(UUID), case_id(FK→case_record), process_instance_id, process_definition_id, process_definition_version, business_key, workflow_type(ENUM: CASE/APPEAL), status(ENUM: ACTIVE/CANCELLED/COMPLETED), started_at, cancelled_at, completed_at, created_at, created_by, updated_at, updated_by, version(BIGINT)

### 20.4 Maintenance Tables

**`maintenance_operation_run`**: id(UUID), operation_type, effective_date(DATE), status, affected_rows, created_at, created_by

### 20.5 Camunda Internal Tables

Camunda uses its own schema with `ACT_*` tables (deployed via `CamundaSchemaMigrator` using official SQL resources). These are never queried directly by application code — only through public Camunda Java API.

---

## 21. Configuration Reference

### 21.1 Environment Variables

All configuration is via environment variables with sensible defaults:

| Variable | Required | Default | Description |
|---|---|---|---|
| `HTTP_PORT` | ✅ | — | Application HTTP listener port |
| `DB_URL` | ✅ | — | PostgreSQL JDBC URL (e.g., `jdbc:postgresql://localhost:5432/sentinel`) |
| `DB_USERNAME` | ✅ | — | Database user |
| `DB_PASSWORD` | ✅ | — | Database password |
| `DB_MAX_POOL_SIZE` | ❌ | `12` | HikariCP maximum pool size |
| `KAFKA_BOOTSTRAP_SERVERS` | ✅ | — | Kafka broker (e.g., `localhost:29092`) |
| `REDIS_HOST` | ✅ | — | Redis host |
| `REDIS_PORT` | ✅ | — | Redis port |
| `MAILPIT_SMTP_HOST` | ✅ | — | Mailpit SMTP host |
| `MAILPIT_SMTP_PORT` | ✅ | — | Mailpit SMTP port |
| `NOTIFICATION_FROM_EMAIL` | ✅ | — | Sender email address |
| `NOTIFICATION_TO_EMAIL` | ✅ | — | Default recipient email |
| `APP_INSTANCE_ID` | ❌ | auto UUID | Unique instance ID (for outbox leasing) |
| `OUTBOX_POLL_INTERVAL` | ❌ | `PT2S` | Outbox publisher poll rate |
| `OUTBOX_LEASE_DURATION` | ❌ | `PT30S` | Duration before leased rows expire |
| `OUTBOX_BATCH_SIZE` | ❌ | `20` | Max rows per poll batch |
| `NOTIFICATION_CONSUMER_GROUP_ID` | ❌ | `sentinel-notification-consumer` | Kafka consumer group |
| `NOTIFICATION_MAX_RETRIES` | ❌ | `3` | Max notification retry attempts |
| `MINIO_ENDPOINT` | ✅ | — | MinIO S3 endpoint (e.g., `http://localhost:9000`) |
| `MINIO_PUBLIC_ENDPOINT` | ❌ | MINIO_ENDPOINT | Public-facing MinIO endpoint |
| `MINIO_ACCESS_KEY` | ✅ | — | MinIO access key |
| `MINIO_SECRET_KEY` | ✅ | — | MinIO secret key |
| `MINIO_EVIDENCE_BUCKET` | ✅ | — | Evidence bucket name |
| `EVIDENCE_UPLOAD_URL_TTL` | ✅ | — | Presigned upload URL TTL (e.g., `PT15M`) |
| `EVIDENCE_DOWNLOAD_URL_TTL` | ✅ | — | Presigned download URL TTL (e.g., `PT10M`) |
| `KEYCLOAK_ISSUER` | ✅ | — | JWT issuer URL (e.g., `http://localhost:8081/realms/sentinel`) |
| `KEYCLOAK_AUDIENCE` | ✅ | — | Expected JWT audience |
| `KEYCLOAK_JWKS_URL` | ✅ | — | JWKS endpoint URL |
| `WORKFLOW_ENGINE_NAME` | ❌ | `sentinel-workflow-engine` | Camunda process engine name |
| `WORKFLOW_INVESTIGATION_ESCALATION_DURATION` | ✅ | — | Investigation SLA timer (e.g., `PT30M`) |

### 21.2 Docker Compose Override Values

When running inside Docker Compose, these hostnames change:
- `DB_URL` → `jdbc:postgresql://postgres:5432/sentinel`
- `KAFKA_BOOTSTRAP_SERVERS` → `kafka:9092`
- `REDIS_HOST` → `redis`
- `MINIO_ENDPOINT` → `http://minio:9000`
- `MAILPIT_SMTP_HOST` → `mailpit`
- `KEYCLOAK_JWKS_URL` → `http://host.docker.internal:8081/realms/sentinel/protocol/openid-connect/certs`

---

## 22. Infrastructure Architecture

### 22.1 Docker Compose Services

```yaml
Services:
  postgres:
    image: postgres:18.3-alpine
    port: 5432
    healthcheck: pg_isready
    volume: sentinel-postgres-data

  kafka:
    image: confluentinc/cp-kafka:7.8.1
    port: 29092 (host) / 9092 (internal)
    mode: KRaft (single node, no ZK)
    env: CLUSTER_ID, KAFKA_NODE_ID=1, KAFKA_PROCESS_ROLES=broker,controller
    healthcheck: kafka-broker-api-versions

  redis:
    image: redis:7.2.7-alpine
    port: 6379
    healthcheck: redis-cli ping

  minio:
    image: quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z
    port: 9000 (S3) / 9001 (console)
    command: server /data --console-address :9001
    env: MINIO_ROOT_USER, MINIO_ROOT_PASSWORD
    volume: sentinel-minio-data
    healthcheck: curl /minio/health/ready

  minio-init:
    image: quay.io/minio/mc:latest
    depends_on: minio (healthy)
    entrypoint: /bin/sh /init/create-bucket.sh
    volumes: ./deployment/minio/init/create-bucket.sh

  keycloak:
    image: quay.io/keycloak/keycloak:26.6
    port: 8081
    command: start-dev --http-port=8080 --import-realm
    env: KC_BOOTSTRAP_ADMIN_USERNAME, KC_BOOTSTRAP_ADMIN_PASSWORD
    volumes: ./deployment/keycloak/realm/sentinel-realm.json
    healthcheck: TCP to port 9000
    start_period: 90s

  mailpit:
    image: axllent/mailpit:latest
    port: 1025 (SMTP) / 8025 (web UI)
    healthcheck: wget to port 8025

  app:
    build: .
    port: 8080
    depends_on: all services healthy
    env: All environment variables (with Docker hostnames)
    healthcheck: curl /health
```

### 22.2 Keycloak Realm Configuration

File: `deployment/keycloak/realm/sentinel-realm.json`

- Realm: `sentinel`
- Client: `sentinel-api` (with JWT audience)
- 14 users (see Section 23) with passwords `sentinel`
- Roles: `SYSTEM_ADMIN`, `CASE_INTAKE_OFFICER`, `TRIAGE_OFFICER`, `INVESTIGATOR`, `CASE_REVIEWER`, `DECISION_MAKER`, `APPEAL_OFFICER`, `SUPERVISOR`, `AUDITOR`
- Each user assigned to roles + custom attributes (jurisdictions, assigned_units, case_classifications, conflicted_actor_ids)

### 22.3 MinIO Bucket Init

File: `deployment/minio/init/create-bucket.sh`

Uses `mc` (MinIO client) to create `sentinel-evidence` bucket with versioning enabled and public upload policy.

---

## 23. Developer Workflow (Make Targets)

### 23.1 Quick Start

```bash
git clone <repository>
cd sentinel-enforcement
make bootstrap          # Download Maven dependencies
make up                 # Start all infrastructure (Docker)
make migrate            # Run schema migrations + start app
make seed               # Init MinIO bucket
make smoke-test         # Verify: GET /health → UP
```

### 23.2 Complete Target Reference

#### Testing

| Target | Command | Description |
|---|---|---|
| `test` | `mvn verify` | All unit + integration tests |
| `unit-test` | `mvn test` | Unit tests only |
| `integration-test` | `mvn -pl sentinel-integration-tests -am verify` | Testcontainers integration |
| `workflow-test` | `mvn -pl sentinel-workflow -am test && ... WorkflowTaskApiIT` | Workflow unit + integration |
| `messaging-test` | `mvn ... -Dit.test=MessagingReliabilityIT` | Messaging reliability |
| `e2e-test` | `mvn -pl sentinel-integration-tests -am verify` | Full end-to-end |
| `karate-smoke` | `mvn ... -Dit.test=KarateSmokeIT` | Smoke suite (requires running app) |
| `karate-regression` | `mvn ... -Dit.test=KarateRegressionIT` | Regression suite (requires running app) |
| `karate-full` | `mvn ... -Dit.test=KarateFullIT` | Full suite (requires running app) |
| `verify` | `mvn verify` | All checks |

#### Build & Code Quality

| Target | Command | Description |
|---|---|---|
| `compile` | `mvn -DskipTests compile` | Compile all modules |
| `package` | `mvn -DskipTests package` | Build JARs |
| `format` | `mvn spotless:apply` | Apply Google Java Format |
| `lint` | `mvn spotless:check` | Check formatting |
| `dependency-check` | `mvn dependency:analyze` | Analyze dependencies |
| `openapi-validate` | `mvn ... generate-sources` | Validate OpenAPI spec |
| `openapi-generate` | `mvn ... generate-sources` | Generate API sources |

#### Infrastructure

| Target | Command | Description |
|---|---|---|
| `up` | `docker compose up -d` | Start all services |
| `down` | `docker compose down` | Stop services |
| `restart` | `docker compose restart` | Restart services |
| `reset` | `docker compose down -v` | Stop + remove volumes |
| `ps` | `docker compose ps` | Show status |
| `logs` | `docker compose logs -f` | Tail logs |
| `app-logs` | `docker compose logs -f app` | App logs |
| `docker-build` | `docker compose build app` | Build image |
| `docker-push-local` | `docker build -t sentinel-app:local` | Local image |

#### Database

| Target | Command | Description |
|---|---|---|
| `migrate` | `mvn install + exec:java + docker compose up app` | Run migrations + start |
| `rollback` | `mvn exec:java ... DatabaseRollbackMain` | Rollback N changesets |
| `db-status` | `docker compose ps postgres` | Postgres status |
| `db-shell` | `docker compose exec postgres psql` | Open psql |
| `db-reset` | `docker compose down -v && up -d postgres` | Reset data |

#### Operations

| Target | Command | Description |
|---|---|---|
| `seed` | `make minio-init` | Bootstrap helpers |
| `smoke-test` | `Invoke-RestMethod GET /health` | Health check |
| `minio-init` | `docker compose up minio-init` | Create MinIO bucket |
| `keycloak-import` | `docker compose up -d keycloak` | Start Keycloak |
| `kafka-topics` | `kafka-topics --list` | List topics |
| `kafka-consume` | `kafka-console-consumer` | Tail case.lifecycle.v1 |
| `kafka-produce` | `kafka-console-producer` | Produce sample notification |
| `bpmn-validate` | `BpmnModelValidationTest` | Validate BPMN model |
| `bpmn-deploy` | informational | Deployment is automatic |

### 23.3 Manual Sequence for Running Karate Tests

```bash
make up                  # Start infrastructure
make migrate             # Run migrations + start app
# Wait for GET /health to return UP
make karate-smoke        # or karate-regression / karate-full
```

---

## 24. Testing Strategy

### 24.1 Test Pyramid

```text
            ╱╲
           ╱  ╲          Karate Acceptance Tests
          ╱    ╲         3 suites: smoke, regression, full
         ╱──────╲
        ╱        ╲       Integration Tests (Testcontainers)
       ╱          ╲      PostgreSQL + Kafka + Keycloak + MinIO + Redis + Mailpit
      ╱────────────╲
     ╱              ╲    Unit Tests
    ╱                ╲   Domain logic, app services, auth policy, BPMN validation
   ╱──────────────────╲
```

### 24.2 Unit Tests

| Module | Tests | What's Covered |
|---|---|---|
| `sentinel-domain` | `CaseRecordTest`, `DecisionTest`, `RecommendationTest` | State transitions, invariant enforcement, optimistic locking |
| `sentinel-application` | `ReportApplicationServiceTest`, `CaseApplicationServiceTest`, `EvidenceApplicationServiceTest` | Use case orchestration, authorization integration |
| `sentinel-security` | `KeycloakTokenVerifierTest`, `RoleBasedAuthorizationServiceTest` | Token validation, auth axis evaluation |
| `sentinel-workflow` | `BpmnModelValidationTest` | BPMN model parsing, process definition count |

### 24.3 Integration Tests (Testcontainers)

All integration tests use `AbstractApiIT` base class which manages containers for:
- PostgreSQL 18
- Keycloak 26 (with sentinel realm import)
- MinIO (latest)
- Redis 7
- Mailpit
- Kafka (KRaft)

| Test | Containers | Coverage Highlights |
|---|---|---|
| `ApplicationRuntimeSchemaLifecycleIT` | PostgreSQL | Empty DB → migration → startup → health |
| `ReportApiIT` | PostgreSQL + Keycloak | Create, read, triage, jurisdiction auth |
| `CaseApiIT` | PostgreSQL + Keycloak | Full lifecycle, cursor listing, auth matrix (wrong role, jurisdiction, unit, clearance, conflict) |
| `EvidenceApiIT` | PostgreSQL + Keycloak + MinIO | Upload session, presigned URL, finalize, checksum mismatch, download auth audit |
| `WorkflowTaskApiIT` | PostgreSQL + Keycloak + Camunda | List, claim, complete, cursor, search, duplicate-completion safety |
| `WorkflowReconciliationApiIT` | PostgreSQL + Keycloak + Camunda | Detect runtime/history mismatch, auto-repair, terminate invalid |
| `MessagingReliabilityIT` | PostgreSQL + Kafka | Outbox with Kafka outage, inbox deduplication, at-least-once |

### 24.4 Karate Acceptance Tests

| Suite | Feature | What It Tests |
|---|---|---|
| **Smoke** (7 tests) | `health`, `auth-login`, `report-intake`, `report-triage`, `case` | Baseline API readiness |
| **Regression** (12 tests) | Smoke + `workflow-task`, `evidence`, `sanction-appeal`, `maintenance`, `reconciliation` | All happy paths |
| **Full** (18 tests) | Regression + `case-search-cursor`, `case-auth-denial`, `case-relationships`, `evidence-checksum`, `messaging-duplicate-delivery` | Auth denial matrix, edge cases |

---

## 25. Architecture Decision Records

| ADR | Title | Decision | Key Consequence |
|---|---|---|---|
| **ADR-001** | Modular Monolith | Modular monolith with Maven multi-module, not microservices | Clear boundaries without operational overhead |
| **ADR-002** | Domain State vs Workflow State | Database domain = source of truth; Camunda holds orchestration state only | Sync must be explicit, but business invariants protected |
| **ADR-003** | MyBatis over ORM | MyBatis instead of JPA/Hibernate | Full SQL control, no N+1, no lazy-loading surprises |
| **ADR-004** | Transactional Outbox | Outbox pattern for event publication | At-least-once without distributed transactions |
| **ADR-005** | Inbox Idempotency | Inbox table with unique constraint | At-most-once side effects for consumers |
| **ADR-006** | Keycloak Local Auth | Keycloak as local OIDC provider | Standard JWT, custom claims for multi-axis authz |
| **ADR-007** | MinIO Evidence Storage | MinIO for S3-compatible evidence storage | Presigned URLs, checksum verification, versioning |
| **ADR-008** | Optimistic Locking | Version-based concurrency with application retry | No DB locks, conflict detection at domain level |
| **ADR-009** | Contract-First API | OpenAPI spec → generated request/response DTOs | API contract versioned, changes visible in spec diff |
| **ADR-010** | Append-Only Audit Log | Immutable audit_event table, also published to Kafka | Full traceability, integration stream without weakening DB audit |

---

## 26. Known Limitations & Future Work

### 26.1 Current Limitations

| Area | Limitation | Impact |
|---|---|---|
| **Workflow Start** | Uses compensation rather than outbox-backed workflow-start intent | If DB write fails after Camunda start, orphan process instance risked |
| **Business Prerequisites** | Later-state prerequisites (decision/sanction/appeal) are lighter than target | Some supporting aggregates not fully implemented |
| **Enforcement Monitoring** | Identified in BPMN but non-trivial obligation tracking behavior not fully implemented | Monitoring workflow runs but with minimal domain backing |
| **Performance** | No load/performance review completed | Unknown scalability characteristics |
| **Resilience** | No failure injection coverage | Recovery paths untested under real failures |
| **Metrics** | In-memory only, no dashboard or export | No operational visibility |
| **Caching** | Redis running but not actively used | Potential for performance optimization unused |
| **Notification** | SMTP via Mailpit (dev only) | Not production-grade email delivery |

### 26.2 Recommended Next Increments

1. **Strengthen Later-State Aggregates** — Deeper business rules for recommendation/review/decision/sanction/appeal
2. **Enforcement Monitoring Deepening** — Full obligation-tracking behavior for the post-decision path
3. **Hardening Extension** — Failure-injection tests, load/performance review, operational metrics export
4. **Outbox-Backed Workflow Start** — Replace compensation with true outbox intent for workflow start
5. **Redis Integration** — Leverage Redis for distributed caching of authorization lookups
6. **Metrics & Dashboard** — Export `InMemoryMetricsRecorder` data to a time-series store

---

## Appendix: Default Test Users

All users have password: `sentinel`

| Username | Role(s) | Jurisdiction | Special Attributes |
|---|---|---|---|
| `intake-jkt` | CASE_INTAKE_OFFICER | JKT | — |
| `intake-bdg` | CASE_INTAKE_OFFICER | BDG | — |
| `triage-jkt` | TRIAGE_OFFICER | JKT | — |
| `triage-bdg` | TRIAGE_OFFICER | BDG | — |
| `investigator-jkt` | INVESTIGATOR | JKT | — |
| `reviewer-jkt` | CASE_REVIEWER | JKT | — |
| `reviewer-jkt-public` | CASE_REVIEWER | JKT | classification: PUBLIC only |
| `reviewer-jkt-conflicted` | CASE_REVIEWER | JKT | conflicted_actor_ids: ["investigator-jkt"] |
| `decision-jkt` | DECISION_MAKER | JKT | — |
| `appeal-jkt` | APPEAL_OFFICER | JKT | — |
| `supervisor-jkt` | SUPERVISOR | JKT | — |
| `supervisor-jkt-unit-2` | SUPERVISOR | JKT | assigned_units: ["JKT-UNIT-2"] |
| `auditor-jkt` | AUDITOR | JKT | — |
| `system-admin` | SYSTEM_ADMIN | — | Superuser (all bypass) |

---

*This documentation was generated from direct analysis of all source code files, configuration files, BPMN models, test classes, infrastructure definitions, and architecture records in the repository. Every statement is grounded in actual source code evidence.*
