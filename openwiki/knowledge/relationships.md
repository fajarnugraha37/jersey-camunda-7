---
type: Knowledge Graph
title: Domain and Architectural Relationships
description: Key domain-to-domain and architectural relationships in the Sentinel Enforcement Platform. This is a reference map, not a full explanation.
tags: [sentinel, relationships, domain, architecture, knowledge-graph]
---

# Domain and Architectural Relationships

This page documents the key relationships between domain concepts, architectural components, and infrastructure. Use it as a reference map when navigating the codebase.

## Domain Concept Lifecycle

```mermaid
%%{init:{'theme':'default'}}%%
flowchart LR
    Report -->|triage| CaseRecord
    CaseRecord -->|investigation| Recommendation
    Recommendation -->|review/approve| Decision
    Decision -->|publish| Sanction
    Decision -->|publish| SanctionObligation
    Decision -->|appeal| Appeal

    CaseRecord <-->|1:N| Evidence
    CaseRecord <-->|1:2<br/>CASE_MAIN / APPEAL| WorkflowInstance
```

### Relationship Details

| From | To | Relationship | Semantics |
|---|---|---|---|
| `Report` | `CaseRecord` | triage | A triaged report creates a case. Source: `Report.triage()` at `/sentinel-domain/.../domain/report/Report.java`, `CaseApplicationService.createCase()` at `/sentinel-application/.../application/casefile/CaseApplicationService.java` |
| `CaseRecord` | `Recommendation` | investigation | An investigated case yields a recommendation. Source: `RecommendationApplicationService` at `/sentinel-application/.../application/recommendation/RecommendationApplicationService.java` |
| `Recommendation` | `Decision` | review/approve | A reviewed recommendation leads to a decision. Source: `DecisionApplicationService` at `/sentinel-application/.../application/decision/DecisionApplicationService.java` |
| `Decision` | `Sanction` | publish | Publishing a decision creates sanctions (if violation proven). Source: `PublishDecisionCommand` at `/sentinel-application/.../application/decision/PublishDecisionCommand.java` |
| `Decision` | `SanctionObligation` | publish | Publishing creates sanction obligations with due dates. |
| `Decision` | `Appeal` | appeal | Respondents can appeal a published decision. Source: `AppealApplicationService` at `/sentinel-application/.../application/appeal/AppealApplicationService.java` |
| `CaseRecord` | `Evidence` | 1-to-many | A case can have many evidence objects. Source: `Evidence.caseId()` at `/sentinel-domain/.../domain/evidence/Evidence.java` |
| `CaseRecord` | `WorkflowInstance` | 1-to-2 | Each case has exactly two workflow instances: `CASE_MAIN` and `APPEAL`. Source: `WorkflowInstanceCorrelation` at `/sentinel-application/.../application/workflow/WorkflowInstanceCorrelation.java` |

## Event Propagation Chain (Transactional Outbox)

```mermaid
%%{init:{'theme':'default'}}%%
flowchart LR
    subgraph "Application Process"
        DomainAggregate -->|state change| OutboxEvent
        OutboxEvent -->|claim & publish| KafkaTopic
        KafkaTopic -->|consume| NotificationConsumer
        NotificationConsumer -->|inbox & send| Notification
    end

    subgraph "Database"
        OutboxEventTable[(outbox_event<br/>PostgreSQL)]
        InboxTable[(inbox_event<br/>PostgreSQL)]
    end

    subgraph "Kafka"
        Topic[("topic<br/>(1:1 with event type)")]
        Retry[("topic.retry")]
        DLQ[("topic.dlq")]
    end

    OutboxEventTable -->|publish| Topic
    Topic -->|failure| Retry
    Retry -->|exhausted| DLQ
```

### Relationship Details

| From | To | Relationship | Notes |
|---|---|---|---|
| Domain change | `OutboxEvent` | 1-to-1 per transaction | Written in same DB transaction as aggregate change. Source: `EvidenceApplicationService.finalizeEvidenceVersion()` lines 250–259 enqueues events after aggregate update. |
| `OutboxEvent` table | Kafka topic | 1-to-1 | Each `outbox_event.topic()` maps to a specific Kafka topic. Topics defined in `MessagingTopics.java` at `/sentinel-application/.../application/messaging/MessagingTopics.java`. |
| Kafka topic | `NotificationConsumer` | 1-to-N | Consumer subscribes to all domain lifecycle and integration topics. |
| Inbox event | `Notification` | 1-to-1 | Inbox ensures idempotent notification processing. Source: `/docs/adr/ADR-005-inbox-idempotency.md`. |

## Authorization Architecture

```mermaid
%%{init:{'theme':'default'}}%%
graph TD
    User -->|Bearer JWT| Keycloak
    Keycloak -->|JWT with roles,<br/>jurisdictions, units,<br/>classifications| BearerAuthenticationFilter
    BearerAuthenticationFilter -->|ApplicationActor| JAX_RS_Resource

    JAX_RS_Resource -->|delegates to| ApplicationService
    ApplicationService -->|requirePermission| RoleBasedAuthorizationService
    RoleBasedAuthorizationService -->|checks| ApplicationActor{roles,<br/>jurisdictions,<br/>units,<br/>classifications,<br/>conflicts}

    ApplicationService -->|invokes| DomainAggregate
    ApplicationService -->|persists via| Repository
```

### Relationship Details

| From | To | Relationship |
|---|---|---|
| `ApplicationActor` | Keycloak | Authenticates via JWT bearer token verified by `KeycloakTokenVerifier` at `/sentinel-security/.../security/KeycloakTokenVerifier.java` |
| `ApplicationActor` | `RoleBasedAuthorizationService` | Authorized by multi-axis check at `/sentinel-security/.../security/RoleBasedAuthorizationService.java`: role, jurisdiction, unit, case classification, conflict-of-interest, and direct assignment |
| `BearerAuthenticationFilter` | JAX-RS resource | Sets `ApplicationActor` as request property. Source: `/sentinel-api/.../api/security/BearerAuthenticationFilter.java` |

## Layered Architecture Call Chain

```mermaid
%%{init:{'theme':'default'}}%%
graph TD
    subgraph "Inbound Adapters"
        JAX_RS[JAX-RS Resource<br/>sentinel-api]
        Auth[BearerAuthenticationFilter<br/>sentinel-api]
    end

    subgraph "Application Layer"
        AppSvc[ApplicationService<br/>sentinel-application]
        AuthSvc[AuthorizationService<br/>sentinel-application]
        Ports[Port interfaces<br/>sentinel-application]
    end

    subgraph "Domain Layer"
        DA[Aggregate + Invariants<br/>sentinel-domain]
    end

    subgraph "Outbound Adapters"
        Persist[MyBatis Repository<br/>sentinel-persistence]
        Storage[MinIO Adapter<br/>sentinel-storage]
        Messaging[Kafka Producer<br/>sentinel-messaging]
        Workflow[Camunda Adapter<br/>sentinel-workflow]
    end

    JAX_RS --> AppSvc
    Auth --> AppSvc
    AppSvc --> AuthSvc
    AppSvc --> DA
    AppSvc -->|through port| Persist
    AppSvc -->|through port| Storage
    AppSvc -->|through port| Messaging
    AppSvc -->|through port| Workflow
```

## Topic-to-Event Mapping

Each Kafka topic in `MessagingTopics.java` (`/sentinel-application/.../application/messaging/MessagingTopics.java`) maps 1-to-1 to a domain event type:

| Topic (Kafka) | Event Type (outbox_event) | Producing Aggregate |
|---|---|---|
| `case.lifecycle.v1` | Case events (created, transitioned, etc.) | `CaseRecord` |
| `case.assignment.v1` | Assignment events | `CaseAssignment` |
| `evidence.lifecycle.v1` | Evidence events | `Evidence` |
| `decision.lifecycle.v1` | Decision events | `Decision` |
| `sanction.lifecycle.v1` | Sanction events | `Sanction` |
| `appeal.lifecycle.v1` | Appeal events | `Appeal` |
| `notification.command.v1` | Command to send notification | Outbox publisher |
| `notification.result.v1` | Notification delivery result | Consumer |
| `audit.integration.v1` | Audit event for external integration | Domain operations |

## Relationship Table

| Subject | Relationship | Object | Evidence |
|---|---|---|---|
| Report | triaged into → | CaseRecord | `Report.triage()` creates `CaseRecord` |
| CaseRecord | has → | Evidence | `evidence.case_id` → `case_record(id)` |
| CaseRecord | has → | Recommendation | `recommendation.case_id` → `case_record(id)` (1:1) |
| CaseRecord | has → | Decision | `decision.case_id` → `case_record(id)` (1:1) |
| CaseRecord | has → | Sanction | `sanction.case_id` → `case_record(id)` |
| CaseRecord | has → | Appeal | `appeal.case_id` → `case_record(id)` |
| CaseRecord | triggers → | WorkflowInstance | `WorkflowModule` starts process on case creation |
| CaseRecord | audits → | AuditEvent | `audit_event.case_id` → `case_record(id)` |
| CaseRecord | transitions via → | CaseStatusHistory | `case_status_history.case_id` → `case_record(id)` |
| Recommendation | approved by → | RecommendationReview | `recommendation_review.recommendation_id` → `recommendation(id)` |
| Decision | versioned by → | DecisionVersion | `decision_version.decision_id` → `decision(id)` |
| Decision | prescribes → | Sanction | `sanction.decision_id` → `decision(id)` (1:1) |
| Sanction | tracks → | SanctionObligation | `sanction_obligation.sanction_id` → `sanction(id)` (1:1) |
| Appeal | decided by → | AppealDecision | `appeal_decision.appeal_id` → `appeal(id)` (1:1) |
| Domain Aggregate | publishes → | OutboxEvent | Transactional outbox within same DB transaction |
| OutboxEvent → Kafka | consumed by → | InboxEvent | Kafka consumer deduplicates via `eventId` |
| Kafka Topic | routed to → | Notification | `KafkaNotificationConsumer` routes to `NotificationCommandHandler`/`NotificationEventHandler` |
| HTTP Request | filtered by → | BearerAuthenticationFilter | JWT extraction → `ApplicationActor` |
| ApplicationActor | authorized by → | AuthorizationService | 6-axis permission check |
| AuthorizationService | delegates to → | RoleBasedAuthorizationService | Role, jurisdiction, classification, unit, conflict, assignment checks |

## Source References

| File | Maps |
|---|---|
| `/sentinel-domain/src/main/java/com/sentinel/enforcement/domain/casefile/CaseRecord.java` | CaseRecord ↔ Report, Evidence, WorkflowInstance |
| `/sentinel-domain/src/main/java/com/sentinel/enforcement/domain/evidence/Evidence.java` | Evidence ↔ CaseRecord |
| `/sentinel-domain/src/main/java/com/sentinel/enforcement/domain/decision/Decision.java` | Decision ↔ Recommendation, Sanction, SanctionObligation |
| `/sentinel-domain/src/main/java/com/sentinel/enforcement/domain/appeal/Appeal.java` | Appeal ↔ Decision |
| `/sentinel-application/src/main/java/com/sentinel/enforcement/application/messaging/MessagingTopics.java` | Topic ↔ event type mapping |
| `/sentinel-application/src/main/java/com/sentinel/enforcement/application/security/AuthorizationService.java` | Actor ↔ authorization port |
| `/sentinel-security/src/main/java/com/sentinel/enforcement/security/RoleBasedAuthorizationService.java` | Authorization rules |
| `/sentinel-workflow/src/main/java/com/sentinel/enforcement/workflow/WorkflowModule.java` | WorkflowInstance ↔ CaseRecord correlation |
