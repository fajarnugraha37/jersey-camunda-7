# Error Handling

## RFC 7807 Problem Details

All error responses follow [RFC 7807](https://tools.ietf.org/html/rfc7807) (Problem Details for HTTP APIs).

### Response Envelope

```json
{
  "type": "https://sentinel.local/errors/case-not-found",
  "title": "Not Found",
  "status": 404,
  "code": "CASE_NOT_FOUND",
  "detail": "Case with ID 123e4567-e89b-12d3-a456-426614174000 not found",
  "instance": "/api/v1/cases/123e4567-e89b-12d3-a456-426614174000",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "violations": []
}
```

| Field | Type | Description |
|-------|------|-------------|
| `type` | String | URI identifying the problem type |
| `title` | String | HTTP reason phrase |
| `status` | Integer | HTTP status code |
| `code` | String | Application-specific error code |
| `detail` | String | Human-readable explanation |
| `instance` | String | URI of the request that caused the error |
| `correlationId` | String | Request tracing ID |
| `violations` | Array | Field-level validation errors |

### Violation Object

```json
{
  "field": "title",
  "message": "must not be null"
}
```

---

## Exception Mappers (26)

All mappers are `@Provider` classes registered in the Jersey `ResourceConfig`.

### 4xx Client Errors

| # | Mapper | Exception | Status | Code Pattern |
|---|--------|-----------|--------|-------------|
| 1 | `BadRequestExceptionMapper` | `jakarta.ws.rs.BadRequestException` | 400 | `MALFORMED_REQUEST` |
| 2 | `ConstraintViolationExceptionMapper` | `jakarta.validation.ConstraintViolationException` | 400 | `VALIDATION_ERROR` |
| 3 | `UnauthenticatedExceptionMapper` | `UnauthenticatedException` | 401 | `UNAUTHENTICATED` |
| 4 | `AuthorizationDeniedExceptionMapper` | `AuthorizationDeniedException` | 403 | `FORBIDDEN` |
| 5 | `NotFoundExceptionMapper` | `jakarta.ws.rs.NotFoundException` | 404 | `RESOURCE_NOT_FOUND` |
| 6 | `ReportNotFoundExceptionMapper` | `ReportNotFoundException` | 404 | `REPORT_NOT_FOUND` |
| 7 | `CaseNotFoundExceptionMapper` | `CaseNotFoundException` | 404 | `CASE_NOT_FOUND` |
| 8 | `EvidenceNotFoundExceptionMapper` | `EvidenceNotFoundException` | 404 | `EVIDENCE_NOT_FOUND` |
| 9 | `RecommendationNotFoundExceptionMapper` | `RecommendationNotFoundException` | 404 | `RECOMMENDATION_NOT_FOUND` |
| 10 | `DecisionNotFoundExceptionMapper` | `DecisionNotFoundException` | 404 | `DECISION_NOT_FOUND` |
| 11 | `WorkflowTaskNotFoundExceptionMapper` | `WorkflowTaskNotFoundException` | 404 | `TASK_NOT_FOUND` |
| 12 | `AppealNotFoundExceptionMapper` | `AppealNotFoundException` | 404 | `APPEAL_NOT_FOUND` |
| 13 | `ReportConflictExceptionMapper` | `ReportConflictException` | 409 | Dynamic from exception |
| 14 | `CaseConflictExceptionMapper` | `CaseConflictException` | 409 | Dynamic from exception |
| 15 | `EvidenceConflictExceptionMapper` | `EvidenceConflictException` | 409 | Dynamic from exception |
| 16 | `EvidenceObjectMissingExceptionMapper` | `EvidenceObjectMissingException` | 409 | `EVIDENCE_OBJECT_NOT_FOUND` |
| 17 | `RecommendationConflictExceptionMapper` | `RecommendationConflictException` | 409 | Dynamic from exception |
| 18 | `DecisionConflictExceptionMapper` | `DecisionConflictException` | 409 | Dynamic from exception |
| 19 | `AppealConflictExceptionMapper` | `AppealConflictException` | 409 | Dynamic from exception |
| 20 | `WorkflowTaskConflictExceptionMapper` | `WorkflowTaskConflictException` | 409 | Dynamic from exception |
| 21 | `WorkflowReconciliationConflictExceptionMapper` | `WorkflowReconciliationConflictException` | 409 | Dynamic from exception |
| 22 | `MaintenanceOperationConflictExceptionMapper` | `MaintenanceOperationConflictException` | 409 | Dynamic from exception |

### 5xx Server Errors

| # | Mapper | Exception | Status | Code |
|---|--------|-----------|--------|------|
| 23 | `EvidenceStorageUnavailableExceptionMapper` | `EvidenceStorageUnavailableException` | 503 | `EVIDENCE_STORAGE_UNAVAILABLE` |
| 24 | `GenericExceptionMapper` | `Throwable` (catch-all) | 500 | `UNEXPECTED_ERROR` |

---

## Correlation ID

Every request receives a correlation ID for tracing:

1. **CorrelationIdFilter** reads `X-Correlation-Id` header from the request
2. If missing or invalid, generates a new UUID
3. Bound to MDC for log correlation
4. Echoed back in the `X-Correlation-Id` response header
5. Included in every error response's `correlationId` field

---

## Filters

| Filter | Priority | Purpose |
|--------|----------|---------|
| `CorrelationIdFilter` | `AUTHENTICATION - 10` | Correlation ID management |
| `BearerAuthenticationFilter` | `AUTHENTICATION` | JWT token extraction and verification |
| `RequestMetricsFilter` | After auth | Request timing metrics |

### BearerAuthenticationFilter
- Skips `/health` endpoint (public)
- Extracts `Authorization: Bearer <token>` header
- Delegates verification to `TokenVerifier` (Keycloak JWT)
- Stores `ApplicationActor` as request property
- Returns 401 with `WWW-Authenticate: Bearer realm="sentinel"` on failure
