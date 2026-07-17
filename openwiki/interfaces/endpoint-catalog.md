---
type: API Endpoint Catalog
title: REST API Endpoint Catalog
description: Complete catalog of all REST API endpoints grouped by resource, with HTTP methods, paths, auth requirements, purposes, cursor-based pagination pattern, and error envelope format.
tags: [sentinel, api, rest, endpoints]
---

# REST API Endpoint Catalog

All REST endpoints are implemented as JAX-RS resources in the `sentinel-api` module under package `com.sentinel.enforcement.api.*Resource.java`. The HTTP server is Grizzly, served through Jersey. All endpoints produce and consume `application/json`.

## Base URL

```
http://localhost:8080
```

## Authentication

All endpoints except `GET /health` require a valid Bearer JWT token issued by Keycloak. The `BearerAuthenticationFilter` (`sentinel-api/src/main/java/.../security/BearerAuthenticationFilter.java`) extracts and verifies the token, setting the `ApplicationActor` on the request context. Unauthenticated requests receive `401 Unauthenticated`.

## Error Envelope Format

Errors follow **RFC 7807 Problem JSON** format, produced by `ErrorResponseFactory` (`sentinel-api/src/main/java/.../error/ErrorResponseFactory.java`):

```json
{
  "type": "https://sentinel.local/errors/report-triage-not-allowed",
  "title": "Conflict",
  "status": 409,
  "code": "REPORT_TRIAGE_NOT_ALLOWED",
  "detail": "Report 123e4567-e89b-12d3-a456-426614174000 cannot be triaged from status TRIAGED.",
  "instance": "/api/v1/reports/123e4567-e89b-12d3-a456-426614174000/triage",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "violations": []
}
```

| Field | Description |
|---|---|
| `type` | URI identifying the error type (`https://sentinel.local/errors/{code-in-kebab-case}`) |
| `title` | HTTP status reason phrase (e.g., "Conflict", "Not Found") |
| `status` | HTTP status code |
| `code` | Machine-readable error code (e.g., `CONCURRENT_MODIFICATION`, `CASE_TRANSITION_NOT_ALLOWED`) |
| `detail` | Human-readable error detail |
| `instance` | Request URI that caused the error |
| `correlationId` | Request correlation ID for tracing |
| `violations` | Array of field-level constraint violations (when applicable) |

### HTTP Status Codes Used

| Status | Usage |
|---|---|
| `200 OK` | Successful GET, POST operations returning a resource |
| `201 Created` | Resource creation (POST) |
| `204 No Content` | Successful operation with no response body (e.g., task completion) |
| `400 Bad Request` | Bean validation failure or malformed request body |
| `401 Unauthenticated` | Missing or invalid Bearer token |
| `403 Authorization Denied` | Authenticated but not authorized for this action/resource |
| `404 Not Found` | Resource not found by ID |
| `409 Conflict` | Optimistic locking failure (`CONCURRENT_MODIFICATION`) or business rule violation |
| `410 Gone` | Evidence object missing from storage |
| `503 Service Unavailable` | Evidence storage unavailable |

---

## 1. Health

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/health/HealthResource.java`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/health` | Public | Returns application health status (database, Kafka, Redis, Mailpit, workflow) |

**Response (200 OK):**
```json
{
  "status": "UP",
  "database": "connected",
  "dependencies": [
    { "name": "kafka", "healthy": true },
    { "name": "redis", "healthy": true }
  ],
  "timestamp": "2026-07-16T12:00:00Z"
}
```

---

## 2. Reports

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/report/ReportResource.java`

Base path: `/api/v1/reports`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/reports` | Required | Create a new enforcement report |
| `GET` | `/api/v1/reports/{reportId}` | Required | Get report details by ID |
| `POST` | `/api/v1/reports/{reportId}/triage` | Required | Triage a report (transition from SUBMITTED to TRIAGED) |

---

## 3. Cases

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/casefile/CaseResource.java`

Base path: `/api/v1/cases`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/cases` | Required | Create a new case from a triaged report |
| `GET` | `/api/v1/cases` | Required | List/search cases with cursor-based pagination |
| `GET` | `/api/v1/cases/{caseId}` | Required | Get case details by ID |
| `POST` | `/api/v1/cases/{caseId}/assignments` | Required | Assign case to a unit/officer |
| `POST` | `/api/v1/cases/{caseId}/transitions` | Required | Transition case to a new status |
| `POST` | `/api/v1/cases/{caseId}/relationships` | Required | Create a case relationship (MERGE, DERIVATION, SPLIT) |
| `GET` | `/api/v1/cases/{caseId}/relationships` | Required | List case relationships (with direction, maxDepth, type filters) |
| `GET` | `/api/v1/cases/{caseId}/audit-events` | Required | List audit trail events for a case |

### Cursor-Based Pagination Pattern

All list endpoints (`GET /api/v1/cases`, `GET /api/v1/tasks`, `GET /api/v1/workflow-reconciliation`, `GET /api/v1/cases/{caseId}/audit-events`) follow the same pattern:

**Request query parameters:**
| Parameter | Type | Default | Description |
|---|---|---|---|
| `cursor` | string | null | Opaque cursor for the next page (from previous response) |
| `limit` | int | 20 (50 for audit) | Page size (min 1, max 50 or 100 for audit) |
| `q` | string | null | Quick search across all text fields |
| `searchField` | string | null | Specific field to search (e.g., `caseNumber`, `title`) |
| `searchValue` | string | null | Value for the specific field search |
| `sortBy` | string | null | Sort field (e.g., `createdAt`, `caseNumber`) |
| `sortDirection` | string | null | `asc` or `desc` |

**Additional list filters per resource:**
- Cases: `status`, `classification`, `assignedUnitId`, `assigneeUserId`, `createdBy`, `reportId`
- Tasks: `caseId`, `assigneeUserId`, `state`
- Workflow reconciliation: `issueType`, `caseStatus`, `workflowCorrelationStatus`
- Audit events: `actorId`, `eventType`, `action`, `result`

**Response format:**
```json
{
  "data": [ ... ],
  "nextCursor": "eyJzb3J0QnkiOiJjcmVhdGVkQXQiLCJ...",
  "limit": 20
}
```

The `nextCursor` is an opaque base64-encoded value. The cursor codecs (`CaseCursorCodec`, `TaskCursorCodec`, `WorkflowReconciliationCursorCodec`, `AuditCursorCodec`) encode/decode the sort key and filter state. A `null` `nextCursor` indicates the last page.

---

## 4. Evidence

### Evidence Resource (top-level)

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/evidence/EvidenceResource.java`

Base path: `/api/v1/evidence`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/evidence/{evidenceId}` | Required | Get evidence metadata by ID |
| `POST` | `/api/v1/evidence/{evidenceId}/versions/finalize` | Required | Finalize an evidence version after upload completes |
| `POST` | `/api/v1/evidence/{evidenceId}/download-sessions` | Required | Create a presigned download URL session |

### Case Evidence Resource (scoped to case)

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/evidence/CaseEvidenceResource.java`

Base path: `/api/v1/cases/{caseId}/evidence`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/cases/{caseId}/evidence/upload-sessions` | Required | Create a presigned upload URL session for a new evidence item |

---

## 5. Tasks (Workflow)

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/workflow/TaskResource.java`

Base path: `/api/v1/tasks`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/tasks` | Required | List workflow tasks with cursor-based pagination |
| `POST` | `/api/v1/tasks/{taskId}/claim` | Required | Claim a workflow task for the current user |
| `POST` | `/api/v1/tasks/{taskId}/complete` | Required | Complete a claimed workflow task (returns 204 No Content) |

Tasks represent Camunda BPMN user tasks exposed to the API layer. The `WorkflowTaskApplicationService` coordinates between the Camunda engine and the case progression guard.

---

## 6. Workflow Reconciliation

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/workflow/WorkflowReconciliationResource.java`

Base path: `/api/v1/workflow-reconciliation`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/workflow-reconciliation` | Required | List workflow reconciliation issues with cursor pagination |
| `POST` | `/api/v1/workflow-reconciliation/{caseId}/actions` | Required | Reconcile a case workflow mismatch |

This endpoint handles cases where the domain `CaseStatus` and Camunda workflow execution state have drifted (see ADR-002).

---

## 7. Recommendations

### Recommendation Resource (top-level)

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/recommendation/RecommendationResource.java`

Base path: `/api/v1/recommendations`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/recommendations/{recommendationId}/submit` | Required | Submit a recommendation for review |
| `POST` | `/api/v1/recommendations/{recommendationId}/reviews` | Required | Approve a submitted recommendation |

### Case Recommendation Resource (scoped to case)

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/recommendation/CaseRecommendationResource.java`

Base path: `/api/v1/cases/{caseId}/recommendations`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/cases/{caseId}/recommendations` | Required | Create a new recommendation in draft state |

---

## 8. Decisions

### Decision Resource (top-level)

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/decision/DecisionResource.java`

Base path: `/api/v1/decisions`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/decisions/{decisionId}/approve` | Required | Approve a draft decision |
| `POST` | `/api/v1/decisions/{decisionId}/publish` | Required | Publish an approved decision |
| `POST` | `/api/v1/decisions/{decisionId}/appeals` | Required | Create an appeal against a published decision |

### Case Decision Resource (scoped to case)

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/decision/CaseDecisionResource.java`

Base path: `/api/v1/cases/{caseId}/decisions`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/cases/{caseId}/decisions` | Required | Create a new decision in draft state |

---

## 9. Appeals

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/appeal/AppealResource.java`

Base path: `/api/v1/appeals`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/appeals/{appealId}/decisions` | Required | Decide an active appeal (GRANTED or DENIED) |

Note: Creating an appeal is done through `POST /api/v1/decisions/{decisionId}/appeals` (see Decisions resource).

---

## 10. Maintenance Operations

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/operations/MaintenanceOperationResource.java`

Base path: `/api/v1/operations`

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/operations/sanction-obligations/recalculate-overdue` | Required | Recalculate overdue sanction obligations based on current date |

---

## Summary: Complete Endpoint List

| # | Method | Path | Section |
|---|---|---|---|
| 1 | `GET` | `/health` | Health |
| 2 | `POST` | `/api/v1/reports` | Reports |
| 3 | `GET` | `/api/v1/reports/{reportId}` | Reports |
| 4 | `POST` | `/api/v1/reports/{reportId}/triage` | Reports |
| 5 | `POST` | `/api/v1/cases` | Cases |
| 6 | `GET` | `/api/v1/cases` | Cases |
| 7 | `GET` | `/api/v1/cases/{caseId}` | Cases |
| 8 | `POST` | `/api/v1/cases/{caseId}/assignments` | Cases |
| 9 | `POST` | `/api/v1/cases/{caseId}/transitions` | Cases |
| 10 | `POST` | `/api/v1/cases/{caseId}/relationships` | Cases |
| 11 | `GET` | `/api/v1/cases/{caseId}/relationships` | Cases |
| 12 | `GET` | `/api/v1/cases/{caseId}/audit-events` | Cases |
| 13 | `GET` | `/api/v1/evidence/{evidenceId}` | Evidence |
| 14 | `POST` | `/api/v1/evidence/{evidenceId}/versions/finalize` | Evidence |
| 15 | `POST` | `/api/v1/evidence/{evidenceId}/download-sessions` | Evidence |
| 16 | `POST` | `/api/v1/cases/{caseId}/evidence/upload-sessions` | Evidence (Case-scoped) |
| 17 | `GET` | `/api/v1/tasks` | Tasks |
| 18 | `POST` | `/api/v1/tasks/{taskId}/claim` | Tasks |
| 19 | `POST` | `/api/v1/tasks/{taskId}/complete` | Tasks |
| 20 | `GET` | `/api/v1/workflow-reconciliation` | Workflow Reconciliation |
| 21 | `POST` | `/api/v1/workflow-reconciliation/{caseId}/actions` | Workflow Reconciliation |
| 22 | `POST` | `/api/v1/recommendations/{recommendationId}/submit` | Recommendations |
| 23 | `POST` | `/api/v1/recommendations/{recommendationId}/reviews` | Recommendations |
| 24 | `POST` | `/api/v1/cases/{caseId}/recommendations` | Recommendations (Case-scoped) |
| 25 | `POST` | `/api/v1/decisions/{decisionId}/approve` | Decisions |
| 26 | `POST` | `/api/v1/decisions/{decisionId}/publish` | Decisions |
| 27 | `POST` | `/api/v1/decisions/{decisionId}/appeals` | Decisions (Appeal creation) |
| 28 | `POST` | `/api/v1/cases/{caseId}/decisions` | Decisions (Case-scoped) |
| 29 | `POST` | `/api/v1/appeals/{appealId}/decisions` | Appeals |
| 30 | `POST` | `/api/v1/operations/sanction-obligations/recalculate-overdue` | Maintenance Operations |
