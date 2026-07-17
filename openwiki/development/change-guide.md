---
type: Development Guide
title: Change Guide
description: Practical developer guidance for making changes — build system, module order, test pyramid, code style, and step-by-step recipes for adding endpoints and aggregates.
tags: [sentinel, development, build, test, maven, ci]
---

# Change Guide

## Build System

The project uses a **Maven multi-module** build with 11 modules defined in `/pom.xml` (lines 12–24). A **Makefile** at `/Makefile` wraps the most common Maven and Docker targets for developer convenience. The Makefile targets are written for **PowerShell Core** (`SHELL := pwsh.exe` on line 1) and set local runtime defaults inline on line 4.

### Module Build Order

The dependency graph enforces a strict build order. From root `pom.xml` (lines 12–24):

```
sentinel-domain
  └─ sentinel-application
       ├─ sentinel-api
       ├─ sentinel-persistence
       ├─ sentinel-messaging
       ├─ sentinel-workflow
       ├─ sentinel-security
       ├─ sentinel-storage
       └─ sentinel-observability
            └─ sentinel-bootstrap
                 └─ sentinel-integration-tests
```

**Convention**: `domain → application → (api, persistence, messaging, workflow, security, storage, observability) → bootstrap → integration-tests`

Each adapter module (`sentinel-api`, `sentinel-persistence`, etc.) depends only on `sentinel-application` (which depends on `sentinel-domain`). No adapter depends on another adapter. Source: individual `/sentinel-*/pom.xml` files.

### Key Makefile Targets

| Target | Command | Use |
|---|---|---|
| `make compile` | `mvn -q -DskipTests compile` | Fast compile check |
| `make test` | `mvn -q verify` | Full unit + integration |
| `make unit-test` | `mvn -q test` | Unit tests only (surefire) |
| `make integration-test` | `mvn -q -pl sentinel-integration-tests -am verify` | Integration tests (Testcontainers) |
| `make workflow-test` | `mvn -q -pl sentinel-workflow -am test` then `mvn -q -pl sentinel-integration-tests -am "-Dit.test=WorkflowTaskApiIT" verify` | Workflow-focused tests |
| `make messaging-test` | `mvn -q -pl sentinel-integration-tests -am "-Dit.test=MessagingReliabilityIT" verify` | Messaging-focused tests |
| `make verify` | `mvn -q verify` | Full build verify |
| `make format` | `mvn -q spotless:apply` | Apply Spotless formatting |
| `make package` | `mvn -q -DskipTests package` | Build JAR |

Source: `/Makefile` lines 56–91.

## Test Pyramid

```
     ╱╲
    ╱  ╲        Acceptance (Karate)
   ╱────╲       smoke / regression / full
  ╱      ╲
 ╱────────╲     Integration (Testcontainers)
╱          ╲    WorkflowTaskApiIT, MessagingReliabilityIT, Phase 6 end-to-end
╱────────────╲  Unit (JUnit 5 / Surefire)
                 Domain tests, application service tests, security tests
```

### Unit Tests — `mvn test` / `make unit-test`

Run via Maven Surefire (`/pom.xml` line 98: `maven-surefire-plugin` 3.5.2). Test classes matching `**/*Test.java` convention.

**Test locations by module:**

| Module | Test source | Example files |
|---|---|---|
| `sentinel-domain` | `/sentinel-domain/src/test/java/` | `CaseRecordTest.java`, `DecisionTest.java`, `RecommendationTest.java` |
| `sentinel-application` | `/sentinel-application/src/test/java/` | `CaseApplicationServiceTest.java`, `EvidenceApplicationServiceTest.java`, `ReportApplicationServiceTest.java` |
| `sentinel-security` | `/sentinel-security/src/test/java/` | `KeycloakTokenVerifierTest.java`, `RoleBasedAuthorizationServiceTest.java` |
| `sentinel-workflow` | `/sentinel-workflow/src/test/java/` | `BpmnModelValidationTest.java` |

### Integration Tests — Testcontainers

Run via `make integration-test`. Lives in `sentinel-integration-tests` with JUnit 5 + Testcontainers (`/pom.xml` line 46: `testcontainers.version 1.20.5`). Integration test classes match `**/*IT.java` convention and require PostgreSQL, Kafka, Redis, MinIO, and Mailpit containers.

### Acceptance Tests — Karate

Three Karate suites in `sentinel-integration-tests`:

| Target | Suite | Use case |
|---|---|---|
| `make karate-smoke` | `KarateSmokeIT` | Quick health check |
| `make karate-regression` | `KarateRegressionIT` | Core flow regression |
| `make karate-full` | `KarateFullIT` | Full acceptance suite |

Source: `/Makefile` lines 78–85.

## Which Tests to Run for Each Change Area

| Change area | Recommended command | What it exercises |
|---|---|---|
| Domain change (records, invariants) | `make unit-test` (or `mvn test -pl sentinel-domain`) | Domain aggregate tests |
| Application service change | `make unit-test` | Application service tests |
| Persistence adapter change | `make integration-test` | MyBatis adapters + Testcontainers |
| API/REST endpoint change | `make integration-test` | JAX-RS resources via test containers |
| Workflow change | `make workflow-test` | `sentinel-workflow` unit tests + `WorkflowTaskApiIT` |
| Messaging change | `make messaging-test` | `MessagingReliabilityIT` |
| Any change (full validation) | `make verify` | All unit + integration tests |
| Formatting only | `make format` | Spotless `spotless:apply` |

## Code Style

The project uses **Spotless** for Java source and POM formatting.

```bash
make format          # Apply formatting
mvn spotless:check   # Check formatting in CI
```

Source: `/Makefile` lines 173–177.

## Recipe: Adding a New REST Endpoint

This recipe follows the API-contract-first pattern (see `/docs/adr/ADR-009-api-contract-first.md`).

1. **OpenAPI contract** — Add the endpoint definition in `/docs/api/openapi.yaml`. Define request/response models as YAML.
2. **Generate** — Run `make openapi-generate` (triggers `mvn -q -pl sentinel-api -am generate-sources`). OpenAPI Generator writes model classes to `sentinel-api/target/generated-sources/openapi/src/gen/java/`.
3. **MapStruct mapper** — Create an `Api*Mapper.java` interface (e.g., `ApiCaseMapper.java`, `ApiEvidenceMapper.java`) in `sentinel-api/src/main/java/.../api/<entity>/`. Uses MapStruct 1.6.3 (`/pom.xml` line 32) with annotation processor for compile-time generation. Example: `/sentinel-api/src/main/java/com/sentinel/enforcement/api/evidence/ApiEvidenceMapper.java`.
4. **ApplicationService command/query** — Create a Command or Query record in `sentinel-application/src/main/java/.../application/<entity>/` (e.g., `CreateEvidenceUploadSessionCommand.java`). Add or extend the `*ApplicationService.java` class.
5. **JAX-RS resource** — Create a `*Resource.java` class in `sentinel-api/src/main/java/.../api/<entity>/` (e.g., `CaseEvidenceResource.java` at `/sentinel-api/src/main/java/com/sentinel/enforcement/api/evidence/CaseEvidenceResource.java`). Annotate with `@Path`, `@Consumes`, `@Produces`, `@Inject`.
6. **Integration test** — Either add a Karate scenario to the existing suite or write a new `*IT.java` in `sentinel-integration-tests`.

**Existing endpoint examples:**
- `/api/v1/cases/{caseId}/evidence/upload-sessions` → `CaseEvidenceResource.java` (line 34)
- `/api/v1/evidence/{evidenceId}/versions/finalize` → `EvidenceResource.java` (line 37)
- `/api/v1/cases` → `CaseResource.java`
- `/api/v1/workflow-reconciliation` → `WorkflowReconciliationResource.java`

## Recipe: Adding a New Domain Aggregate

1. **Domain record with invariants** — Create an immutable Java `record` in `sentinel-domain/src/main/java/.../domain/<entity>/`. Define constructor validation (see `CaseRecord.java` at `/sentinel-domain/src/main/java/com/sentinel/enforcement/domain/casefile/CaseRecord.java` lines 28–50). Add factory methods like `create(...)` and state-transition methods that return new record instances (defensive copy pattern).
2. **Application port + service** — Define a repository port interface in `sentinel-application/src/main/java/.../application/<entity>/` (e.g., `EvidenceRepository.java`). Create an `*ApplicationService.java` that orchestrates the port, authorization, and transactions.
3. **Persistence adapter + Liquibase changelog** — Implement the port in `sentinel-persistence` with a MyBatis mapper interface and adapter class. Add a Liquibase changeset in the module's `src/main/resources/db/changelog/`. Pattern: see `EvidenceRepositoryMyBatisAdapter.java` at `/sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/evidence/EvidenceRepositoryMyBatisAdapter.java`.
4. **API resource** — Create an OpenAPI contract, generate, MapStruct mapper, and JAX-RS resource (see REST endpoint recipe above).
5. **Test** — Add domain unit tests in `sentinel-domain/src/test/java/`, application service tests in `sentinel-application/src/test/java/`, and integration tests in `sentinel-integration-tests`.

**Existing aggregate reference:**
- `Report` (`/sentinel-domain/src/main/java/com/sentinel/enforcement/domain/report/Report.java`) — Record with constructor validation, `triage()` method with optimistic locking via `expectedVersion`.
- `CaseRecord` (`/sentinel-domain/src/main/java/com/sentinel/enforcement/domain/casefile/CaseRecord.java`) — Record with `assignTo()`, `transitionTo()` state machine.
- `Evidence` (`/sentinel-domain/src/main/java/com/sentinel/enforcement/domain/evidence/Evidence.java`) — Record with `activate()` version bump and optimistic locking via `version + 1`.

## Source References

1. **Build System** — `/pom.xml`, `/Makefile`
2. **API Contract** — `/docs/api/openapi.yaml`, `sentinel-api/pom.xml`
3. **Domain Aggregates** — `sentinel-domain/src/main/java/.../domain/`
4. **Application Services** — `sentinel-application/src/main/java/.../application/`
5. **Persistence** — `sentinel-persistence/src/main/java/.../persistence/`
6. **Tests** — `sentinel-domain/src/test/java/`, `sentinel-application/src/test/java/`, `sentinel-integration-tests/`
