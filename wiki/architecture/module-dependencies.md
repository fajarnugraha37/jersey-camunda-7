# Module Dependencies

## Maven Module Structure

```
sentinel-enforcement (parent POM)
├── sentinel-domain            — Pure domain model
├── sentinel-application       — Application services + port interfaces
├── sentinel-api               — JAX-RS REST adapter
├── sentinel-persistence       — MyBatis persistence adapter
├── sentinel-messaging         — Kafka messaging adapter
├── sentinel-storage           — MinIO storage adapter
├── sentinel-workflow          — Camunda BPMN workflow adapter
├── sentinel-security          — Keycloak security adapter
├── sentinel-observability     — Health checks + metrics adapter
├── sentinel-bootstrap         — DI wiring + HTTP server
└── sentinel-integration-tests — Testcontainers + Karate tests
```

---

## Dependency Direction (strict inward-facing)

```
sentinel-domain
  ↑
sentinel-application
  ↑
sentinel-api  sentinel-persistence  sentinel-messaging
  sentinel-storage  sentinel-workflow  sentinel-security
  sentinel-observability
  ↑
sentinel-bootstrap
  ↑
sentinel-integration-tests
```

### Rules

| Rule | Enforcement |
|------|------------|
| Domain → nothing | `sentinel-domain` has ZERO Maven dependencies (pure Java) |
| Application → Domain only | `sentinel-application` depends on `sentinel-domain` + spec APIs (Jakarta Validation) |
| Adapters → Application + Domain | Each adapter depends on `sentinel-application` and `sentinel-domain` |
| API → all adapters | `sentinel-api` is an inbound adapter; no outgoing adapter depends on it |
| Bootstrap → everything | `sentinel-bootstrap` depends on all modules to wire them |
| Tests → everything | `sentinel-integration-tests` depends on all modules |

---

## Detailed Dependency Table

| Module | Compile Dependencies | Purpose |
|--------|---------------------|---------|
| `sentinel-domain` | *(none)* | Pure Java records + enums + exceptions |
| `sentinel-application` | `sentinel-domain`, Jakarta Validation | App services, port interfaces, Permission enum |
| `sentinel-api` | `sentinel-application`, Jersey 3.1.9, Jackson, Hibernate Validator | REST resources, DTOs, exception mappers, filters |
| `sentinel-persistence` | `sentinel-application`, MyBatis 3.5.19, HikariCP, Liquibase, PostgreSQL driver | Repository implementations, mappers, transaction management |
| `sentinel-messaging` | `sentinel-application`, Kafka 3.8.1 client, Jackson | Outbox publisher, notification consumer, retry/DLQ |
| `sentinel-storage` | `sentinel-application`, MinIO SDK | Evidence storage adapter, presigned URL handling |
| `sentinel-workflow` | `sentinel-application`, Camunda 7.24.0 (engine + Spin) | Workflow adapter, Java delegates, BPMN deployment |
| `sentinel-security` | `sentinel-application`, Nimbus JOSE + JWT | Keycloak token verification, 7-axis authorization |
| `sentinel-observability` | `sentinel-application`, Micrometer core | Health check aggregation, metrics recording |
| `sentinel-bootstrap` | ALL modules, Grizzly, HK2, SLF4J/Logback | Startup, DI wiring, HTTP server |
| `sentinel-integration-tests` | ALL modules, Testcontainers, Karate, JUnit 5 | Integration + acceptance tests |

---

## Module Boundary Rules (package-level)

| Module | Source Package | Exported |
|--------|---------------|----------|
| `sentinel-domain` | `com.sentinel.enforcement.domain.*` | All aggregates, enums, exceptions |
| `sentinel-application` | `com.sentinel.enforcement.application.*` | Services + port interfaces only |
| `sentinel-persistence` | `com.sentinel.enforcement.persistence.*` | Repository implementations (package-private mappers) |
| `sentinel-messaging` | `com.sentinel.enforcement.messaging.*` | Publisher + consumer (package-private internals) |
| `sentinel-storage` | `com.sentinel.enforcement.storage.*` | Storage adapter |
| `sentinel-workflow` | `com.sentinel.enforcement.workflow.*` | Workflow adapter (package-private delegates) |
| `sentinel-security` | `com.sentinel.enforcement.security.*` | Token verifier + authorization service |
| `sentinel-api` | `com.sentinel.enforcement.api.*` | Resources, DTOs, mappers, filters |
| `sentinel-observability` | `com.sentinel.enforcement.observability.*` | Health check implementations |
| `sentinel-bootstrap` | `com.sentinel.enforcement.bootstrap.*` | Main class, binder, runtime |
