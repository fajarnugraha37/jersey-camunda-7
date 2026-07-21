# Testing Strategy

Three-tier testing strategy: Unit → Integration (Testcontainers) → Acceptance (Karate).

---

## Test Pyramid

```
         ╱╲
        ╱  ╲
       ╱ Karate ╲        ← Acceptance tests (API-level scenarios)
      ╱──────────╲
     ╱            ╲
    ╱ Integration  ╲      ← Testcontainers-based (JUnit 5)
   ╱────────────────╲
  ╱                  ╲
 ╱    Unit Tests      ╲    ← Pure JUnit 5 (no infrastructure)
╱────────────────────────╲
```

---

## Unit Tests (`mvn test`)

| Module | Tests |
|--------|-------|
| `sentinel-domain` | Aggregate behavior, state transitions, OCC, maker-checker |
| `sentinel-application` | Application service logic, authorization port mocks |
| `sentinel-security` | KeycloakTokenVerifier, RoleBasedAuthorizationService |
| `sentinel-workflow` | BPMN model validation (`BpmnModelValidationTest`) |

**Run:** `make unit-test`

---

## Integration Tests (`mvn verify -pl sentinel-integration-tests -am`)

### Infrastructure (Testcontainers)

| Test Class | What It Tests |
|------------|---------------|
| `ReportApiIT` | Report CRUD, triage, auth (401/403), health endpoint |
| `CaseApiIT` | Full case lifecycle, assignment, relationships, search/pagination, authorization |
| `EvidenceApiIT` | Upload session, finalize, download, checksum verification |
| `WorkflowTaskApiIT` | Task claim/complete, appeal lifecycle, enforcement monitoring |
| `WorkflowReconciliationApiIT` | Reconciliation issues detection and repair |
| `MessagingReliabilityIT` | Kafka outage resilience, outbox recovery, inbox idempotency |
| `ApplicationRuntimeSchemaLifecycleIT` | Schema migration failure and recovery |

### Testcontainers Used
- PostgreSQL 18
- Kafka (via Redpanda or Kafka container)
- Keycloak 26.6
- MinIO
- Redis
- Mailpit

**Run:** `make integration-test`

---

## Acceptance Tests (Karate)

### Smoke Suite
- `karate-smoke` → `platform-smoke.feature`: Health check + basic report → triage → case bootstrap

### Regression Suite
- `workflow-case-lifecycle.feature`: Full workflow progression
- `evidence-lifecycle.feature`: Upload → finalize → download
- `appeal-lifecycle.feature`: Granted appeal + late appeal override
- `workflow-reconciliation.feature`: Auto-repair and terminate
- `maintenance-operations.feature`: Overdue recalculation

### Full Suite
Complete end-to-end scenarios:
- `platform-auth-report.feature`: Auth, report creation, triage
- `case-state-and-authorization.feature`: Full lifecycle, transitions, OCC, authorization
- `case-relationships.feature`: Recursive relationship queries
- `case-query-and-audit.feature`: Search, sort, pagination
- `evidence-negative.feature`: Unauthorized download, duplicate finalize
- `decision-locking.feature`: Row lock handling
- `workflow-task-advanced.feature`: Enforcement monitoring, idempotent completion
- `workflow-reconciliation-advanced.feature`: Supervisor reconciliation
- `messaging-observable.feature`: Outbox events, notification delivery, inbox dedup

**Run:**
```bash
make karate-smoke      # Smoke tests
make karate-regression # Regression tests
make karate-full       # Full suite
```

---

## Testing Infrastructure (Shared)

### `AbstractApiIT`
Base class providing:
- Testcontainers lifecycle management
- Shared HTTP client
- Auth token provider
- SQL helpers
- Domain helpers: `createReport()`, `triageReport()`, `createRecommendation()`, `createDecision()`, `createAppeal()`, etc.

### `LiveDbSupport`
Karate-callable static methods for direct SQL:
- `countRows(table)`, `getCaseStatus(caseId)`, `lockTable(table)`

### `LiveMessagingSupport`
Karate-callable static methods for Kafka:
- `produceCaseLifecycleEvent(eventPayload)`

---

## Running Tests

```bash
# All tests
make test               # = mvn verify

# Unit only
make unit-test          # = mvn test

# Integration only (with Testcontainers)
make integration-test   # = mvn verify -pl sentinel-integration-tests -am

# Karate acceptance
make karate-smoke       # KarateSmokeIT
make karate-regression  # KarateRegressionIT
make karate-full        # KarateFullIT

# Specific
make workflow-test      # Workflow unit tests + WorkflowTaskApiIT
make messaging-test     # MessagingReliabilityIT
```

---

## Code Formatting

```bash
# Apply formatting
make format             # mvn spotless:apply

# Check formatting (CI)
make lint               # mvn spotless:check
```
