---
type: Reference
title: API Overview
description: Full REST API surface, list query pattern, error handling contract, and security flow for the Sentinel Enforcement Platform.
tags: [api, rest, endpoints, security, openapi]
---

# API Overview

## REST API Surface

All business endpoints are under `/api/v1/`. OpenAPI spec: `/docs/api/openapi.yaml`.

### Health &amp; Readiness

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Returns composite UP/DOWN with per-dependency status |

**Source:** `HealthResource.java` (`/sentinel-api/src/main/java/.../api/health/`)

### Report Management

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/reports` | Create a new report (intake officer) |
| GET | `/api/v1/reports/{reportId}` | Get report details |
| POST | `/api/v1/reports/{reportId}/triage` | Triage a report (moves to TRIAGED status) |

**Source:** `ReportResource.java` (`/sentinel-api/src/main/java/.../api/report/`)

### Case Management

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/cases` | Create case from a triaged report |
| GET | `/api/v1/cases` | List cases (cursor-paged, searchable, sortable) |
| GET | `/api/v1/cases/{caseId}` | Get case details |
| POST | `/api/v1/cases/{caseId}/assignments` | Assign case to investigator |
| POST | `/api/v1/cases/{caseId}/transitions` | Transition case status |
| GET | `/api/v1/cases/{caseId}/audit-events` | List audit events (cursor-paged) |

**Source:** `CaseResource.java` (`/sentinel-api/src/main/java/.../api/casefile/`)

### Evidence

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/cases/{caseId}/evidence/upload-sessions` | Create presigned upload URL |
| GET | `/api/v1/evidence/{evidenceId}` | Get evidence metadata |
| POST | `/api/v1/evidence/{evidenceId}/versions/finalize` | Verify and activate evidence version |
| POST | `/api/v1/evidence/{evidenceId}/download-sessions` | Create presigned download URL |

**Source:** `CaseEvidenceResource.java`, `EvidenceResource.java` (`/sentinel-api/src/main/java/.../api/evidence/`)

### Workflow Tasks

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/tasks` | List workflow tasks (cursor-paged) |
| POST | `/api/v1/tasks/{taskId}/claim` | Claim a task |
| POST | `/api/v1/tasks/{taskId}/complete` | Complete a task |

**Source:** `TaskResource.java` (`/sentinel-api/src/main/java/.../api/workflow/`)

### Recommendations

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/cases/{caseId}/recommendations` | Create recommendation |
| POST | `/api/v1/recommendations/{recommendationId}/submit` | Submit for review |
| POST | `/api/v1/recommendations/{recommendationId}/review` | Review recommendation |
| GET | `/api/v1/recommendations/{recommendationId}` | Get recommendation |
| GET | `/api/v1/cases/{caseId}/recommendations` | List recommendations for case |

**Source:** `CaseRecommendationResource.java`, `RecommendationResource.java` (`/sentinel-api/src/main/java/.../api/recommendation/`)

### Decisions

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/cases/{caseId}/decisions` | Create decision draft |
| POST | `/api/v1/decisions/{decisionId}/approve` | Approve decision |
| POST | `/api/v1/decisions/{decisionId}/publish` | Publish decision |

**Source:** `CaseDecisionResource.java`, `DecisionResource.java` (`/sentinel-api/src/main/java/.../api/decision/`)

### Appeals

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/cases/{caseId}/appeals` | Create appeal |
| POST | `/api/v1/appeals/{appealId}/decide` | Decide appeal |

**Source:** `AppealResource.java` (`/sentinel-api/src/main/java/.../api/appeal/`)

### Workflow Reconciliation

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/workflow-reconciliation` | List reconciliation issues |
| POST | `/api/v1/workflow-reconciliation/{caseId}/actions` | Execute reconciliation action |

**Source:** `WorkflowReconciliationResource.java` (`/sentinel-api/src/main/java/.../api/workflow/`)

### Maintenance Operations

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/maintenance-operations/recalculate-overdue-sanctions` | Recalculate overdue sanctions |

**Source:** `MaintenanceOperationResource.java` (`/sentinel-api/src/main/java/.../api/operations/`)

## List Query Pattern

All list endpoints follow the same pattern documented in `/docs/api/list-query-pattern.md`:

- **Cursor pagination:** `?cursor=...&amp;limit=20`
- **Quick search:** `?q=keyword` (scans multiple fields)
- **Targeted search:** `?searchField=enum&amp;searchValue=value`
- **Sorting:** `?sortBy=ENUM&amp;sortDirection=ASC|DESC` (whitelisted enums, never raw column names)
- **Cursor scope binding:** Cursor encodes the sort+filter scope to prevent scope mismatch

**MyBatis SQL rules for dynamic queries:**
- Use `<choose>` for enum-to-column mapping (never `${sortBy}`)
- Use `<where>` or `<trim>` for optional filters
- Use `<foreach>` for safe `IN` lists
- Use `<set>` for partial updates

**Examples:** `CaseMyBatisMapper.java` (`findCasePage`), `WorkflowTaskApplicationService.java`.

## Error Handling

All errors return a consistent JSON envelope. Standard HTTP status codes:

| Status | When | Exception Mapper |
|---|---|---|
| 400 | Bad request, validation failure | `BadRequestExceptionMapper`, `ConstraintViolationExceptionMapper` |
| 401 | Missing/invalid token | `UnauthenticatedExceptionMapper` |
| 403 | Wrong role, jurisdiction, classification, or conflict-of-interest | `AuthorizationDeniedExceptionMapper` |
| 404 | Resource not found | `CaseNotFoundExceptionMapper`, `EvidenceNotFoundExceptionMapper`, `DecisionNotFoundExceptionMapper`, `AppealNotFoundExceptionMapper`, `RecommendationNotFoundExceptionMapper`, `ReportNotFoundExceptionMapper`, `WorkflowTaskNotFoundExceptionMapper` |
| 409 | Conflict (optimistic locking, state transition, duplicate claim) | `CaseConflictExceptionMapper`, `EvidenceConflictExceptionMapper`, `DecisionConflictExceptionMapper`, `AppealConflictExceptionMapper`, `RecommendationConflictExceptionMapper`, `ReportConflictExceptionMapper`, `WorkflowTaskConflictExceptionMapper`, `WorkflowReconciliationConflictExceptionMapper`, `MaintenanceOperationConflictExceptionMapper` |
| 500 | Unexpected errors | `GenericExceptionMapper` |

Source: `/sentinel-api/src/main/java/.../api/error/`

## Security Flow

```
Request &rarr; BearerAuthenticationFilter
  &rarr; extracts Bearer token from Authorization header
  &rarr; KeycloakTokenVerifier.verify(token)
    &rarr; Nimbus JWTProcessor with RemoteJWKSet
    &rarr; validates: issuer, audience, expiration, not-before
    &rarr; extracts claims: roles, jurisdictions, assigned_units,
        case_classifications, conflicted_actor_ids
  &rarr; RequestActorResolver &rarr; ApplicationActor
  &rarr; RoleBasedAuthorizationService.authorize(actor, permission, resource)
```

**Three-layer authorization** in `RoleBasedAuthorizationService`:
1. **Role check** — maps each `Permission` to required roles (e.g., `CREATE_REPORT` &rarr; `CASE_INTAKE_OFFICER`)
2. **Scope checks** (in order):
   - Jurisdiction — actor must have resource's jurisdiction code
   - Case classification — actor must have required clearance
   - Conflict-of-interest — blocked if resource owner is in actor's conflict list
   - Assigned unit — unit-scoped actors aligned with resource unit
3. **Direct assignment** — investigators need explicit case assignment for read/transition/evidence ops

**Default users** for development: `/README.md` lists 15+ users with various roles, jurisdictions, classifications, and conflict configurations.

Source: `/sentinel-security/src/main/java/.../security/RoleBasedAuthorizationService.java`, `/sentinel-application/src/main/java/.../security/Permission.java`.

The list query pattern is used by `GET /api/v1/cases`, `GET /api/v1/cases/{caseId}/audit-events`, and `GET /api/v1/tasks`. See the [testing strategy](/openwiki/testing/strategy.md#5-karate-bdd-suites) for how these endpoints are covered by Karate full-suite features.

## OpenAPI Contract

The API is contract-first: `/docs/api/openapi.yaml` (79 KB) defines all endpoints, schemas, and error responses. DTOs are generated at build time via `openapi-generator-maven-plugin`. MapStruct mappers (`ApiCaseMapper`, `ApiEvidenceMapper`, etc.) convert between generated DTOs and application-layer objects.

Run `make openapi-validate` to validate the spec and regenerate DTOs.
