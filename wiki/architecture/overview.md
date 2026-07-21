# Architecture Overview

## Pattern: Modular Hexagonal Monolith

The Sentinel Enforcement Platform uses a **modular monolith** with **hexagonal (ports & adapters)** architecture. All modules compile and deploy as a single JAR artifact, but dependency direction and package boundaries enforce internal separation.

---

## Layers

```
 ┌─────────────────────────────────────────────────────┐
 │                 REST API (JAX-RS)                    │
 │         sentinel-api — 12 resources, 26 mappers     │
 ├─────────────────────────────────────────────────────┤
 │              Application Services                    │
 │       sentinel-application — 8 services, ports       │
 ├─────────────────────────────────────────────────────┤
 │                  Domain Model                        │
 │         sentinel-domain — 7 aggregates, pure Java    │
 ├────────────┬──────────┬──────────┬───────────────────┤
 │ Persistence│ Messaging│ Storage  │ Workflow          │
 │ MyBatis    │ Kafka    │ MinIO    │ Camunda BPMN      │
 ├────────────┴──────────┴──────────┴───────────────────┤
 │ Security (Keycloak)      │ Observability (Health)    │
 ├──────────────────────────┴───────────────────────────┤
 │              Bootstrap (HK2 DI + Grizzly)             │
 └─────────────────────────────────────────────────────┘
```

### Domain Layer (`sentinel-domain`)
- **Zero dependencies** on any framework or infrastructure
- Contains: 7 aggregates (Java records), 14 enums, 6 domain exceptions, value objects
- State changes return **new immutable instances** (records)
- Enforces business invariants: state transitions, maker-checker, OCC version checks
- Audit summaries exposed via `auditSummary()` on each aggregate

### Application Layer (`sentinel-application`)
- Depends only on `sentinel-domain`
- Contains: 8 application services, port interfaces (security, messaging, workflow, health)
- Authorization port (`AuthorizationService`, `TokenVerifier`)
- Messaging port (`OutboxPort`, `InboxPort`, `NotificationPort`)
- Workflow port (`CaseWorkflowPort`, `WorkflowAdministrationPort`)
- 30 `Permission` enum values for authz

### Adapter Layer (6 modules)
Each adapter implements a port interface from the application layer:

| Module | Port | Technology |
|--------|------|-----------|
| `sentinel-api` | Inbound REST | Jersey 3.1.9, Jackson |
| `sentinel-persistence` | Outbound persistence | MyBatis 3.5.19, HikariCP, Liquibase |
| `sentinel-messaging` | Outbound messaging | Kafka 3.8.1 client |
| `sentinel-storage` | Outbound file storage | MinIO SDK |
| `sentinel-workflow` | Outbound workflow | Camunda 7.24.0 (embedded) |
| `sentinel-security` | Outbound auth | Keycloak 26.6, Nimbus JOSE |
| `sentinel-observability` | Monitoring | Micrometer, health checks |

### Bootstrap Layer (`sentinel-bootstrap`)
- Assembles all adapters via HK2 dependency injection (`ApplicationBinder`)
- Starts Grizzly HTTP server on configurable port
- Runs Liquibase migrations at startup
- Starts Kafka outbox publisher daemon thread
- Registers all JAX-RS resources, filters, exception mappers

---

## Dependency Rules

```
sentinel-domain (no dependencies)
    ↑
sentinel-application (depends only on domain)
    ↑
sentinel-api  sentinel-persistence  sentinel-messaging
sentinel-storage  sentinel-workflow  sentinel-security
sentinel-observability
    ↑
sentinel-bootstrap (assembles everything)
```

- Domain NEVER imports from adapters
- Application NEVER imports from adapters (only interfaces)
- Adapters depend on application (for port interfaces) and infrastructure libraries
- Bootstrap depends on ALL modules (assembly point)

---

## Key Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| Modular monolith, not microservices | Clear domain boundaries without operational overhead of distributed systems |
| Domain state ≠ Workflow state | Database is source of truth for business state; Camunda holds orchestration state only |
| MyBatis, not JPA | Explicit queries, schema-driven, easy to trace and optimize |
| Transactional outbox | Kafka publish must not be an independent operation from domain change |
| Inbox idempotency | Safe against duplicate delivery — side effects with storage-backed deduplication |
| Optimistic locking | Concurrent update conflicts become explicit; caller handles retry |
| OpenAPI contract-first | Contract clarity before implementation; generated DTOs |
| Append-only audit | Tamper-evident trail with dedicated storage |

Full details: [Architecture Decision Records](decision-records.md)

---

## Module Dependency Graph

```
sentinel-domain
    ↓
sentinel-application
    ↓
sentinel-api  →  sentinel-persistence
              →  sentinel-messaging
              →  sentinel-storage
              →  sentinel-workflow
              →  sentinel-security
              →  sentinel-observability
    ↓
sentinel-bootstrap
    ↓
sentinel-integration-tests (test only)
```

Detailed dependencies: [Module Dependencies](module-dependencies.md)
