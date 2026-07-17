---
type: Playbook
title: Testing Strategy
description: Testing pyramid, Testcontainers integration tests, Karate BDD suites, and key test coverage in the Sentinel Enforcement Platform.
tags: [testing, karate, testcontainers, integration, quality]
---

# Testing Strategy

Sentinel follows a **layered testing strategy** with increasing scope and complexity:

1. **Domain unit tests** &mdash; pure logic, no mocks, no infrastructure
2. **Application service tests** &mdash; in-memory fakes, stubbed ports
3. **Unit-level security/workflow tests** &mdash; focused on specific modules
4. **Integration tests** &mdash; Testcontainers with real infrastructure
5. **Karate BDD suites** &mdash; end-to-end against running application

All tests can be run via `make` targets. See [Operations](/openwiki/operations/runbooks.md) for command reference.

## 1. Domain Unit Tests

Pure domain logic tests with no mocking framework. Test state machines, business rules, and invariants in isolation.

| Test Class | File | What It Validates |
|---|---|---|
| `CaseRecordTest` | `/sentinel-domain/src/test/java/.../casefile/CaseRecordTest.java` | Case state transitions (happy path), role-based rejection, stale version rejection, assignment-after-closure rejection |
| `DecisionTest` | `/sentinel-domain/src/test/java/.../decision/DecisionTest.java` | Maker-checker (author cannot approve own draft), published decision immutability |
| `RecommendationTest` | `/sentinel-domain/src/test/java/.../recommendation/RecommendationTest.java` | Maker-checker (author cannot review own recommendation) |

**Run:** `make unit-test`

## 2. Application Service Tests

Test service orchestration using `InMemory*Repository` fakes and `CapturingAuthorizationService`. No DI container needed — pure constructor injection.

| Test Class | File | What It Validates |
|---|---|---|
| `CaseApplicationServiceTest` | `/sentinel-application/src/test/java/.../casefile/CaseApplicationServiceTest.java` | Full case creation flow: triaged report &rarr; case creation, authorization captured, outbox event published, workflow started, audit trail persisted |
| `EvidenceApplicationServiceTest` | `/sentinel-application/src/test/java/.../evidence/EvidenceApplicationServiceTest.java` | Upload session with presigned URL, finalization with checksum verification, authorization checks, outbox publishing |
| `ReportApplicationServiceTest` | `/sentinel-application/src/test/java/.../report/ReportApplicationServiceTest.java` | Report create/get, jurisdiction-based authorization |

**Run:** `make unit-test`

## 3. Security &amp; Workflow Unit Tests

| Test Class | File | What It Validates |
|---|---|---|
| `KeycloakTokenVerifierTest` | `/sentinel-security/src/test/java/.../security/KeycloakTokenVerifierTest.java` | JWT claim extraction: sub, username, roles, jurisdictions, assigned_units, classifications, conflicted actors |
| `RoleBasedAuthorizationServiceTest` | `/sentinel-security/src/test/java/.../security/RoleBasedAuthorizationServiceTest.java` | Role checks, jurisdiction boundary, direct assignment, conflict-of-interest |
| `BpmnModelValidationTest` | `/sentinel-workflow/src/test/java/.../workflow/BpmnModelValidationTest.java` | BPMN model structure validation for both process definitions |

**Run:** `make unit-test` or `make workflow-test`

## 4. Integration Tests (Testcontainers)

**Base class:** `AbstractApiIT.java` (`/sentinel-integration-tests/src/test/java/.../integration/AbstractApiIT.java`)

Bootstraps 6 Testcontainers + full `ApplicationRuntime`:

| Container | Image | Used For |
|---|---|---|
| PostgreSQL | `postgres:18.3-alpine` | Database |
| Keycloak | `keycloak:26.6` | JWT authentication |
| MinIO | `minio:latest` | Evidence storage |
| Redis | `redis:7.2.7-alpine` | Cache |
| Mailpit | `axllent/mailpit` | SMTP capture |
| Kafka | custom `StablePortKafkaContainer` | Event streaming |

**Helpers provided by `AbstractApiIT`:**
- `accessToken(actor)` &mdash; obtain JWT for a test user
- `createTriagedReport(...)` &mdash; setup helper
- `createAssignedCase(...)` &mdash; setup helper
- `createPublishedDecisionContext(...)` &mdash; setup helper
- Direct SQL helpers (`queryForLong`, `executeUpdate`)
- `awaitCondition` &mdash; polling wait

| IT Class | File | What It Validates |
|---|---|---|
| `CaseApiIT` | `CaseApiIT.java` (57 KB) | Full case lifecycle end-to-end: create &rarr; triage &rarr; assign &rarr; investigate &rarr; recommendation &rarr; review &rarr; decision &rarr; appeal &rarr; enforcement &rarr; close. Also: audit cursors, case relationships, maintenance operations |
| `EvidenceApiIT` | `EvidenceApiIT.java` | Upload &rarr; finalize &rarr; get &rarr; download evidence with checksum, media type, size verification |
| `MessagingReliabilityIT` | `MessagingReliabilityIT.java` | Kafka outage resilience: stops Kafka, verifies outbox PENDING, restarts Kafka, waits for drain and notification delivery |
| `WorkflowReconciliationApiIT` | `WorkflowReconciliationApiIT.java` | Supervisor listing, AUTO_REPAIR, TERMINATE_RUNTIME, audit events |
| `WorkflowTaskApiIT` | `WorkflowTaskApiIT.java` | Full workflow task-driven lifecycle |
| `ReportApiIT` | `ReportApiIT.java` | Report create/get/triage with authorization |
| `ApplicationRuntimeSchemaLifecycleIT` | `ApplicationRuntimeSchemaLifecycleIT.java` | Proves startup fails if migrate() not called first |

**Run:** `make integration-test`, `make workflow-test`, `make messaging-test`, `make e2e-test`

## 5. Karate BDD Suites

Runs against a **running application** (Docker Compose or local). Divided into three levels:

### Smoke (`make karate-smoke`)
**File:** `/smoke/platform-smoke.feature`

Quick health check + basic API readiness.

### Regression (`make karate-regression`)
| Feature | File | Scope |
|---|---|---|
| Appeal lifecycle | `/regression/appeal-lifecycle.feature` | Appeal create, decide |
| Evidence lifecycle | `/regression/evidence-lifecycle.feature` | Evidence upload, finalize |
| Maintenance operations | `/regression/maintenance-operations.feature` | Sanction recalculation |
| Workflow case lifecycle | `/regression/workflow-case-lifecycle.feature` | Full workflow-driven case flow |
| Workflow reconciliation | `/regression/workflow-reconciliation.feature` | Basic reconciliation |

### Full (`make karate-full`)
10 feature files covering:
| Feature | File | Scope |
|---|---|---|
| Case query &amp; audit | `/full/case-query-and-audit.feature` | Cursor, sort, filter permutations |
| Case relationships | `/full/case-relationships.feature` | Direct/indirect relationship traversal |
| Case state &amp; authorization | `/full/case-state-and-authorization.feature` (15 KB) | Authorization matrix: role + jurisdiction + unit |
| Decision locking | `/full/decision-locking.feature` | Published decision immutability |
| Evidence negative | `/full/evidence-negative.feature` | Missing objects, wrong checksum |
| Messaging observable | `/full/messaging-observable.feature` | Outbox &rarr; notification delivery |
| Platform auth &amp; report | `/full/platform-auth-report.feature` | Auth denial matrix |
| Workflow reconciliation advanced | `/full/workflow-reconciliation-advanced.feature` | Edge cases |
| Workflow task advanced | `/full/workflow-task-advanced.feature` (14 KB) | Advanced task scenarios |

**Run (with app running):** `make karate-smoke`, `make karate-regression`, `make karate-full`

## Important Testing Notes

- **Kafka outage test** (`MessagingReliabilityIT`) is the only test that actively stops/restarts a container
- **ApplicationRuntimeSchemaLifecycleIT** does NOT extend `AbstractApiIT` (standalone test)
- Karate `full/` features use `karate.call()` to import common features from `karate/common/`
- New list APIs must include tests for: quick search, targeted search, sort, and cursor continuation
