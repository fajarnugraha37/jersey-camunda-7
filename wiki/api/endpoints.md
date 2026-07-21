# API Endpoints

All 30 REST endpoints. Base URL: `http://localhost:8080`

All endpoints require `Authorization: Bearer <token>` except `/health`.

---

## Health

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/health` | Health check with dependency statuses | Public |

**Response:** `{"status":"UP","dependencies":{"database":"UP","kafka":"UP","redis":"UP","mailpit":"UP","workflow":"UP"},"timestamp":"..."}`

---

## Reports

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/reports` | Create a report | `CREATE_REPORT` |
| GET | `/api/v1/reports/{reportId}` | Get report by ID | `READ_REPORT` |
| POST | `/api/v1/reports/{reportId}/triage` | Triage a report | `TRIAGE_REPORT` |

---

## Cases

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/cases` | Create case from triaged report | `CREATE_CASE` |
| GET | `/api/v1/cases` | List cases (cursor-paginated) | `LIST_CASES` |
| GET | `/api/v1/cases/{caseId}` | Get case by ID | `READ_CASE` |
| POST | `/api/v1/cases/{caseId}/assignments` | Assign case to unit/user | `ASSIGN_CASE` |
| POST | `/api/v1/cases/{caseId}/relationships` | Create case relationship | `MANAGE_CASE_RELATIONSHIPS` |
| GET | `/api/v1/cases/{caseId}/relationships` | List case relationships | `READ_CASE` |
| POST | `/api/v1/cases/{caseId}/transitions` | Transition case status | `TRANSITION_CASE` |
| GET | `/api/v1/cases/{caseId}/audit-events` | List audit events | `READ_CASE_AUDIT` |

---

## Recommendations

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/cases/{caseId}/recommendations` | Create recommendation | `CREATE_RECOMMENDATION` |
| POST | `/api/v1/recommendations/{recommendationId}/submit` | Submit recommendation | `SUBMIT_RECOMMENDATION` |
| POST | `/api/v1/recommendations/{recommendationId}/reviews` | Review recommendation | `REVIEW_RECOMMENDATION` |

---

## Decisions

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/cases/{caseId}/decisions` | Create decision | `CREATE_DECISION` |
| POST | `/api/v1/decisions/{decisionId}/approve` | Approve decision | `APPROVE_DECISION` |
| POST | `/api/v1/decisions/{decisionId}/publish` | Publish decision | `PUBLISH_DECISION` |

---

## Appeals

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/decisions/{decisionId}/appeals` | File appeal | `CREATE_APPEAL` |
| POST | `/api/v1/appeals/{appealId}/decisions` | Decide appeal | `DECIDE_APPEAL` |

---

## Evidence

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/cases/{caseId}/evidence/upload-sessions` | Create upload session | `CREATE_EVIDENCE_UPLOAD_SESSION` |
| POST | `/api/v1/evidence/{evidenceId}/versions/finalize` | Finalize evidence version | `FINALIZE_EVIDENCE` |
| GET | `/api/v1/evidence/{evidenceId}` | Get evidence metadata | `READ_EVIDENCE` |
| POST | `/api/v1/evidence/{evidenceId}/download-sessions` | Create download session | `CREATE_EVIDENCE_DOWNLOAD_SESSION` |

---

## Workflow Tasks

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/tasks` | List active tasks (cursor-paginated) | `LIST_TASKS` |
| POST | `/api/v1/tasks/{taskId}/claim` | Claim a task | `CLAIM_TASK` |
| POST | `/api/v1/tasks/{taskId}/complete` | Complete a task | `COMPLETE_TASK` |

---

## Workflow Reconciliation

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/workflow-reconciliation` | List reconciliation issues | `RECONCILE_WORKFLOW` |
| POST | `/api/v1/workflow-reconciliation/{caseId}/actions` | Execute reconciliation action | `RECONCILE_WORKFLOW` |

---

## Maintenance Operations

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/operations/sanction-obligations/recalculate-overdue` | Recalculate overdue obligations | `RUN_MAINTENANCE_OPERATION` |

---

## Query Parameters (List Endpoints)

All list endpoints use cursor-based pagination:

| Parameter | Type | Description |
|-----------|------|-------------|
| `cursor` | String | Opaque cursor for pagination |
| `q` | String | Quick search (across multiple fields) |
| `searchField` | String | Field-targeted search |
| `searchValue` | String | Value for field-targeted search |
| `sortBy` | String | Sort field (whitelisted values only) |
| `sortDirection` | String | `ASC` or `DESC` |
| `limit` | Integer | Max results per page |

### Endpoint-Specific Filters

| Endpoint | Additional Filter Params |
|----------|------------------------|
| `GET /cases` | `status`, `classification`, `assignedUnitId`, `assigneeUserId`, `createdBy`, `reportId` |
| `GET /cases/{caseId}/audit-events` | `actorId`, `eventType`, `action`, `result` |
| `GET /tasks` | `caseId`, `assigneeUserId`, `state` |
| `GET /workflow-reconciliation` | `issueType`, `caseStatus`, `workflowCorrelationStatus` |

---

## HTTP Status Codes

| Code | Meaning |
|------|---------|
| `200 OK` | Successful GET |
| `201 Created` | Successful POST (resource created) |
| `204 No Content` | Successful task completion |
| `400 Bad Request` | Validation error, malformed request |
| `401 Unauthorized` | Missing or invalid token |
| `403 Forbidden` | Insufficient permissions |
| `404 Not Found` | Resource not found |
| `409 Conflict` | Optimistic lock, state transition, or duplicate |
| `500 Internal Server Error` | Unexpected error |
| `503 Service Unavailable` | Dependency unavailable |

All error responses follow [RFC 7807 Problem Details](error-handling.md).
