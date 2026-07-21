# Architecture Decision Records

All 10 ADRs are located in [`docs/adr/`](../../docs/adr/).

---

| ADR | Title | Decision | Rationale |
|-----|-------|----------|-----------|
| **ADR-001** | [Modular Monolith](docs/adr/ADR-001-modular-monolith.md) | Use Maven multi-module with explicit boundaries; do not split into microservices initially | Clear domain boundaries without the operational overhead of microservices in the early phase |
| **ADR-002** | [Domain State vs Workflow State](docs/adr/ADR-002-domain-state-vs-workflow-state.md) | Database domain is the source of truth for business state; Camunda engine holds orchestration state only | Protect business invariants; synchronisation must be explicit but business truth stays in the database |
| **ADR-003** | [MyBatis over ORM](docs/adr/ADR-003-mybatis-over-orm.md) | Use MyBatis as the primary persistence mapper instead of JPA/Hibernate | Explicit queries that are easy to trace, suitable for schema-driven persistence |
| **ADR-004** | [Transactional Outbox](docs/adr/ADR-004-transactional-outbox.md) | Use transactional outbox pattern when messaging is implemented | Kafka publish must not be an independent operation from the domain change; increases reliability |
| **ADR-005** | [Inbox Idempotency](docs/adr/ADR-005-inbox-idempotency.md) | Use an inbox table with unique key `(consumer_name, event_id)` for duplicate detection | Consumers must be safe against duplicate delivery; side effects are safer with storage-backed deduplication |
| **ADR-006** | [Keycloak Local Authentication](docs/adr/ADR-006-keycloak-local-authentication.md) | Use Keycloak as the local identity provider | Provides a realistic auth flow matching production patterns rather than hard-coded tokens |
| **ADR-007** | [MinIO Evidence Storage](docs/adr/ADR-007-minio-evidence-storage.md) | Use MinIO for local development evidence object storage | Realistic file upload/download flow with proper lifecycle management instead of filesystem or DB blobs |
| **ADR-008** | [Optimistic Locking](docs/adr/ADR-008-optimistic-locking.md) | Use optimistic locking via a `version` column on mutable aggregates | Concurrent update conflicts become explicit; caller must handle retry or user-facing error |
| **ADR-009** | [API Contract First](docs/adr/ADR-009-api-contract-first.md) | OpenAPI spec is the source of truth for HTTP contracts; integrate with code generator | Contract clarity and stability before implementation complexity grows; spec must stay in sync |
| **ADR-010** | [Audit Log Model](docs/adr/ADR-010-audit-log-model.md) | Use append-only audit events as a separate model from application logs | Regulatory enforcement requires tamper-evident trails with dedicated storage and query model |

---

## Key Themes

### Why Not Microservices? (ADR-001)
A modular monolith provides clear domain boundaries without distributed system complexity. If拆分 is needed later, the module boundaries already exist.

### Why Domain State ≠ Workflow State? (ADR-002)
The database is the authoritative business record. Camunda holds process orchestration state. Reconciliation tooling detects and repairs mismatches.

### Why MyBatis? (ADR-003)
Full control over SQL. Schema-driven development. No N+1 surprises. Each query is explicit and traceable.

### Why Transactional Outbox? (ADR-004)
Business commits and message publication must be atomic. Without an outbox, a Kafka broker outage would roll back domain changes.

### Why Inbox Idempotency? (ADR-005)
At-least-once Kafka delivery means duplicates are possible. The inbox table with `(consumer_name, event_id)` unique constraint prevents duplicate side effects.
