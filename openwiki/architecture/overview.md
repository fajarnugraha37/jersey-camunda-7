---
type: Architecture
title: Architecture Overview
description: Modular monolith architecture of the Sentinel Enforcement Platform. Covers hexagonal layering, module boundaries, dependency direction, CDI wiring, and cross-cutting concerns.
tags: [architecture, modules, hexagonal, monolith]
---

# Architecture Overview

## System Architecture

Sentinel Enforcement Platform is a **modular monolith** with hexagonal (ports-and-adapters) layering and event-driven integration boundaries. The entire application deploys as a single Grizzly HTTP server but is split into explicit Maven modules with strict dependency direction.

### Dependency Direction

```
REST API (sentinel-api)
    |
Application Services (sentinel-application)
    |
Domain Model & Policies (sentinel-domain)
    |
Ports (interfaces in sentinel-application)
    |
Infrastructure Adapters
    +-- sentinel-persistence (MyBatis + Liquibase over PostgreSQL)
    +-- sentinel-workflow   (Embedded Camunda 7 BPMN engine)
    +-- sentinel-messaging  (Kafka outbox publisher, notification consumer)
    +-- sentinel-storage    (MinIO object storage adapter)
    +-- sentinel-security   (Keycloak JWT verification + RBAC)
    +-- sentinel-observability (Health checks, metrics, correlation)
```

Critical rule: **Domain must never depend on infrastructure frameworks**. The domain module (`sentinel-domain`) has zero dependencies on Jersey, MyBatis, Kafka, Camunda, MinIO, or Keycloak. Ports defined in `sentinel-application` are implemented by infrastructure adapters.

Source: `/AGENTS.md` sections 3-4, `/README.md`.

## Module Responsibilities

### `sentinel-domain` (`/sentinel-domain/src/main/java/com/sentinel/enforcement/domain/`)
Pure Java aggregates, entities, value objects, domain services, policies, state transition rules, and domain exceptions. No framework dependencies. Key packages:
- `casefile/` — `CaseRecord`, `CaseStatus`, `CaseClassification`, `CaseRelationship`, `AuditEvent`
- `appeal/` — `Appeal`, `AppealDecision`, `AppealStatus`
- `decision/` — `Decision`, `DecisionStatus`, `DecisionVersion`
- `evidence/` — `Evidence`, `EvidenceClassification`, `EvidenceVersion`, `EvidenceUploadSession`
- `recommendation/` — `Recommendation`, `RecommendationStatus`, `RecommendationReview`
- `sanction/` — `Sanction`, `SanctionObligation`, `SanctionStatus`
- `report/` — `Report`, `ReportStatus`

### `sentinel-application` (`/sentinel-application/src/main/java/com/sentinel/enforcement/application/`)
Use case orchestration layer. Contains:
- **Application services** per aggregate: `CaseApplicationService`, `EvidenceApplicationService`, `DecisionApplicationService`, `AppealApplicationService`, `RecommendationApplicationService`, `ReportApplicationService`
- **Command/Query objects** — `CreateCaseCommand`, `ListCasesQuery`, `AssignCaseCommand`, etc.
- **Port interfaces** — `CaseRepository`, `EvidenceRepository`, `EvidenceStoragePort`, `CaseWorkflowPort`, `WorkflowReconciliationQueryPort`
- **Security** — `Permission`, `ApplicationActor`, `AuthorizationContext`, `AuthorizationService`
- **Messaging** — `OutboxRepository`, `InboxRepository`, `NotificationRepository`, `EventEnvelope`

### `sentinel-api` (`/sentinel-api/src/main/java/com/sentinel/enforcement/api/`)
Jersey REST layer. Key components:
- **Resources** — `CaseResource`, `EvidenceResource`, `ReportResource`, `TaskResource`, `DecisionResource`, `AppealResource`, `RecommendationResource`, `MaintenanceOperationResource`, `WorkflowReconciliationResource`, `HealthResource`
- **Error mappers** — 15+ exception mappers for 401/403/404/409/503 and domain-specific errors
- **Security** — `BearerAuthenticationFilter`, `RequestActorResolver`, `RequestMetadataResolver`
- **DTOs** — Generated from OpenAPI spec (`openapi-generator-maven-plugin`), mapped via MapStruct

### `sentinel-persistence` (`/sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/`)
MyBatis-based persistence with:
- **Repository adapters** implementing `sentinel-application` ports
- **MyBatis mappers** with dynamic SQL (safe `<choose>` for enum-to-column, `<where>`, `<foreach>`)
- **Transaction management** — `MyBatisTransactionManager` wrapping per-thread `SqlSession`
- **Liquibase changelogs** — 11 release files (0001–0011)
- **Exception classification** — `PersistenceExceptionClassifier`

### `sentinel-workflow` (`/sentinel-workflow/src/main/java/com/sentinel/enforcement/workflow/`)
Embedded Camunda BPMN engine:
- `CamundaCaseWorkflowAdapter` — implements `CaseWorkflowPort`, correlates workflows via `workflow_instance` table
- `CamundaWorkflowAdministrationAdapter` — implements `WorkflowAdministrationPort` for reconciliation
- `PreTriageRoutingDelegate` — classifies cases during workflow execution
- `InvestigationEscalationDelegate` — handles timer escalation
- Two BPMN processes: `regulatory-enforcement-case.bpmn`, `decision-appeal-review.bpmn`
- Schema migration: explicit `CamundaSchemaMigrator`, not auto-update
- See [BPMN Workflows](/openwiki/workflows/bpmn.md) for details.

### `sentinel-messaging` (`/sentinel-messaging/src/main/java/com/sentinel/enforcement/messaging/`)
- `KafkaOutboxPublisher` — polls `outbox_event` table with lease-based locking (`FOR UPDATE SKIP LOCKED`)
- `KafkaNotificationConsumer` — idempotent inbox consumer
- `NotificationEventHandler` — processes command events, dispatches email
- `NotificationCommandHandler` — handles retry and DLQ routing
- 9 Kafka topics for lifecycle events

### `sentinel-storage` (`/sentinel-storage/src/main/java/com/sentinel/enforcement/storage/`)
- `MinioEvidenceStorageAdapter` — implements `EvidenceStoragePort`
- Two-client architecture: internal + public endpoints for presigned URLs
- Presigned URL generation with TTL checks (max 7 days)

### `sentinel-security` (`/sentinel-security/src/main/java/com/sentinel/enforcement/security/`)
- `KeycloakTokenVerifier` — Nimbus JWT verification with JWKS
- `RoleBasedAuthorizationService` — three-layer authorization: role check, scope check (jurisdiction, classification, assigned unit, conflict-of-interest), direct assignment check

### `sentinel-observability` (`/sentinel-observability/src/main/java/com/sentinel/enforcement/observability/`)
- `CompositeHealthStatusService` — aggregates dependency health checks
- `DatabaseDependencyHealthCheck`, `SocketDependencyHealthCheck`, `WorkflowDependencyHealthCheck`
- `RequestMetricsFilter` — request counting and timing via `MetricsRecorder`
- `CorrelationContext` — MDC-based correlation ID propagation

### `sentinel-bootstrap` (`/sentinel-bootstrap/src/main/java/com/sentinel/enforcement/bootstrap/`)
- `ApplicationRuntime` — assembles modules via HK2 `ApplicationBinder`, starts Grizzly
- `AppConfiguration` — reads environment variables
- `LiquibaseMigrator` — runs Liquibase + Camunda schema migration
- `DatabaseMigrationMain`, `DatabaseRollbackMain` — CLI migration entrypoints

## Cross-Cutting Concerns

### Transaction Management
`MyBatisTransactionManager` provides per-thread `SqlSession` management. Transaction options support configurable isolation levels via `TransactionOptions` / `TransactionIsolation`. The `ApplicationTransactionManager` bridges application-layer transaction requirements to the MyBatis implementation.

### Authorization
Every API request flows through: `BearerAuthenticationFilter` &rarr; `KeycloakTokenVerifier` &rarr; `RequestActorResolver` &rarr; `RoleBasedAuthorizationService`. Authorization checks happen at the application service layer, not in the API. See [API Overview](/openwiki/api/overview.md#security-flow) for the full flow.

### Auditing
State transitions, assignments, evidence actions, and reconciliation actions write append-only `audit_event` rows. Domain events are also published to the `audit.integration.v1` Kafka topic.

### Outbox Pattern
All messaging side effects are written to `outbox_event` in the same database transaction as the business change. A background `KafkaOutboxPublisher` polls pending rows with lease-based locking, publishes to Kafka, and marks rows as `PUBLISHED`. This ensures business commits survive Kafka outages.

Source: `/docs/IMPLEMENTATION_PLAN.md`, `/docs/PROJECT_STATUS.md`, `/README.md` sections 3-4, `/AGENTS.md`.

## Source Map

Key wiring class: `ApplicationBinder.java` (`/sentinel-bootstrap/src/main/java/.../bootstrap/ApplicationBinder.java`) — HK2 DI registration for all modules.
Key runtime class: `ApplicationRuntime.java` (`/sentinel-bootstrap/src/main/java/.../bootstrap/ApplicationRuntime.java`) — startup orchestration.
