---
type: API Contracts
title: API Contracts — OpenAPI, DTO Generation, and MapStruct Mapping
description: Documentation of the Sentinel Enforcement Platform's contract-first API approach including OpenAPI specification, code generation via OpenAPI Generator, MapStruct mapper layer, error envelope format (RFC 7807), and the cursor-based list query pattern.
tags: [sentinel, api, openapi, rest, mapstruct, dto, validation]
---

# API Contracts

## Contract-First Approach

The Sentinel Enforcement Platform uses an **OpenAPI contract-first** approach: `docs/api/openapi.yaml` is the single source of truth for all REST API request and response DTOs. Java code is generated from this contract, and the API layer (JAX-RS resources) never defines its own DTOs manually.

**Source:** `/docs/api/openapi.yaml`

```yaml
openapi: 3.0.3
info:
  title: Sentinel Enforcement Platform API
  version: 0.1.0
  description: Phase 0-5 vertical slice for health, report intake, authorization, case lifecycle, workflow, and storage-backed evidence intake.
servers:
  - url: http://localhost:8080
```

---

## OpenAPI Generator Integration

### Maven Plugin Configuration

**Source:** `/sentinel-api/pom.xml` (lines 124-159)

```xml
<plugin>
  <groupId>org.openapitools</groupId>
  <artifactId>openapi-generator-maven-plugin</artifactId>
  <executions>
    <execution>
      <id>generate-openapi-models</id>
      <goals>
        <goal>generate</goal>
      </goals>
      <phase>generate-sources</phase>
      <configuration>
        <inputSpec>${project.basedir}/../docs/api/openapi.yaml</inputSpec>
        <generatorName>jaxrs-spec</generatorName>
        <modelPackage>com.sentinel.enforcement.api.generated.model</modelPackage>
        <apiPackage>com.sentinel.enforcement.api.generated.api</apiPackage>
        <invokerPackage>com.sentinel.enforcement.api.generated.invoker</invokerPackage>
        <generateApis>false</generateApis>
        <generateModelTests>false</generateModelTests>
        <generateModelDocumentation>false</generateModelDocumentation>
        <generateApiTests>false</generateApiTests>
        <generateApiDocumentation>false</generateApiDocumentation>
        <generateSupportingFiles>false</generateSupportingFiles>
        <configOptions>
          <dateLibrary>java8</dateLibrary>
          <useBeanValidation>true</useBeanValidation>
          <useJakartaEe>true</useJakartaEe>
          <hideGenerationTimestamp>true</hideGenerationTimestamp>
          <interfaceOnly>true</interfaceOnly>
        </configOptions>
        <globalProperties>
          <models/>
        </globalProperties>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**Key decisions:**

- Only **models** are generated (`<generateApis>false</generateApis>`). JAX-RS resources are hand-written.
- Generated package: `com.sentinel.enforcement.api.generated.model`.
- `useBeanValidation: true` — generated DTOs include `jakarta.validation.constraints` annotations derived from the OpenAPI schema.
- `useJakartaEe: true` — uses Jakarta EE (not javax).
- The generated source directory is registered via the `build-helper-maven-plugin`:

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>build-helper-maven-plugin</artifactId>
  <executions>
    <execution>
      <id>add-openapi-generated-sources</id>
      <goals><goal>add-source</goal></goals>
      <phase>generate-sources</phase>
      <configuration>
        <sources>
          <source>${project.build.directory}/generated-sources/openapi/src/gen/java</source>
        </sources>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### Make Targets

**Source:** `/Makefile` (lines 93-97)

```makefile
openapi-generate:
    mvn -q -pl sentinel-api -am generate-sources

openapi-validate:
    mvn -q -pl sentinel-api -am generate-sources
```

Both targets run the same Maven command (`generate-sources` phase on the `sentinel-api` module with upstream module resolution). The `generate-sources` phase triggers the OpenAPI Generator plugin, which validates the OpenAPI spec and generates DTOs. Spec validation failures cause the build to fail.

---

## MapStruct Mapping Layer

Generated DTOs are never used directly in domain logic. Instead, **MapStruct mappers** convert between generated API DTOs and application-layer command/response types.

### Mapper Classes

All mappers implement `org.mapstruct.Mapper` with `unmappedTargetPolicy = ReportingPolicy.ERROR` — any unmapped field causes a compile-time error.

**Source paths:**

| Mapper | Path |
|---|---|
| `ApiCaseMapper` | `sentinel-api/src/main/java/.../api/casefile/ApiCaseMapper.java` |
| `ApiReportMapper` | `sentinel-api/src/main/java/.../api/report/ApiReportMapper.java` |
| `ApiDecisionMapper` | `sentinel-api/src/main/java/.../api/decision/ApiDecisionMapper.java` |
| `ApiEvidenceMapper` | `sentinel-api/src/main/java/.../api/evidence/ApiEvidenceMapper.java` |
| `ApiAppealMapper` | `sentinel-api/src/main/java/.../api/appeal/ApiAppealMapper.java` |
| `ApiRecommendationMapper` | `sentinel-api/src/main/java/.../api/recommendation/ApiRecommendationMapper.java` |
| `ApiMaintenanceOperationMapper` | `sentinel-api/src/main/java/.../api/operations/ApiMaintenanceOperationMapper.java` |
| `ApiWorkflowReconciliationMapper` | `sentinel-api/src/main/java/.../api/workflow/ApiWorkflowReconciliationMapper.java` |
| `ApiWorkflowTaskMapper` | `sentinel-api/src/main/java/.../api/workflow/ApiWorkflowTaskMapper.java` |

### Example: ApiCaseMapper

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/casefile/ApiCaseMapper.java`

```java
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ApiCaseMapper {
    ApiCaseMapper INSTANCE = Mappers.getMapper(ApiCaseMapper.class);

    @Mapping(target = "correlationId", source = "correlationId")
    @Mapping(target = "sourceIp", source = "sourceIp")
    @Mapping(target = "classification",
             expression = "java(toDomainClassification(request.getClassification()))")
    CreateCaseCommand toCreateCaseCommand(
        CreateCaseRequest request, String correlationId, String sourceIp);

    @Mapping(target = "correlationId", source = "correlationId")
    @Mapping(target = "sourceIp", source = "sourceIp")
    AssignCaseCommand toAssignCaseCommand(
        AssignCaseRequest request, String correlationId, String sourceIp);

    @Mapping(target = "status", source = "status")
    CaseResponse toResponse(CaseRecord caseRecord);

    default CaseListResponse toListResponse(CasePage casePage, String nextCursor) {
        return new CaseListResponse()
            .items(casePage.items().stream().map(this::toResponse).toList())
            .nextCursor(nextCursor);
    }
}
```

**Mapping conventions:**

- **Request → Command:** Generated DTO (`CreateCaseRequest`) → Application command (`CreateCaseCommand`). Cross-cutting fields like `correlationId` and `sourceIp` are injected as separate method parameters.
- **Domain → Response:** Domain object (`CaseRecord`) → Generated DTO (`CaseResponse`). Enum conversions happen via `expression = "java(...)"` using helper methods on the mapper.
- **List responses:** Custom `default` methods assemble paginated list responses from domain page objects.

---

## API Error Envelope (RFC 7807)

All API errors follow the **RFC 7807 Problem JSON** format.

### OpenAPI Schema

**Source:** `/docs/api/openapi.yaml` (lines 2856-2895)

```yaml
ErrorResponse:
  type: object
  required:
    - type
    - title
    - status
    - code
    - detail
    - instance
    - correlationId
    - violations
  properties:
    type:      { type: string }
    title:     { type: string }
    status:    { type: integer }
    code:      { type: string }
    detail:    { type: string }
    instance:  { type: string }
    correlationId: { type: string }
    violations:
      type: array
      items:
        $ref: '#/components/schemas/Violation'

Violation:
  type: object
  required:
    - field
    - message
  properties:
    field:   { type: string }
    message: { type: string }
```

### ErrorResponseFactory

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/error/ErrorResponseFactory.java`

```java
public static ErrorResponse create(
    Response.Status status,
    String code,
    String detail,
    String instance,
    String correlationId,
    List<Violation> violations) {
    return new ErrorResponse()
        .type("https://sentinel.local/errors/" + code.toLowerCase().replace('_', '-'))
        .title(status.getReasonPhrase())
        .status(status.getStatusCode())
        .code(code)
        .detail(detail)
        .instance(instance)
        .correlationId(correlationId)
        .violations(violations);
}
```

**Error `type` URI pattern:** `https://sentinel.local/errors/{code-in-kebab-case}`

  - Example: `CONCURRENT_MODIFICATION` → `https://sentinel.local/errors/concurrent-modification`
  - Example: `CASE_TRANSITION_NOT_ALLOWED` → `https://sentinel.local/errors/case-transition-not-allowed`

### Example Error Response

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

### HTTP Status Codes Used

| Status | Usage |
|---|---|
| `200 OK` | Successful GET, POST operations returning a resource |
| `201 Created` | Resource creation (POST) |
| `204 No Content` | Successful operation with no response body |
| `400 Bad Request` | Bean validation failure or malformed request body |
| `401 Unauthenticated` | Missing or invalid Bearer token |
| `403 Authorization Denied` | Authenticated but not authorized |
| `404 Not Found` | Resource not found by ID |
| `409 Conflict` | Optimistic locking failure (`CONCURRENT_MODIFICATION`) or business rule violation |
| `410 Gone` | Evidence object missing from storage |
| `503 Service Unavailable` | Evidence storage unavailable |

### Exception Mappers

Each domain exception type has a dedicated JAX-RS `ExceptionMapper` registered in the application's `ResourceConfig`. All mappers live under `sentinel-api/src/main/java/.../api/error/`:

- `ConstraintViolationExceptionMapper` — maps `jakarta.validation.ConstraintViolationException` to `400` with field-level `violations[]`.
- `CaseConflictExceptionMapper`, `ReportConflictExceptionMapper`, etc. — map domain `ConflictException` subtypes to `409`.
- `CaseNotFoundExceptionMapper`, `ReportNotFoundExceptionMapper`, etc. — map domain `NotFoundException` subtypes to `404`.
- `UnauthenticatedExceptionMapper` — maps `UnauthenticatedException` to `401`.
- `AuthorizationDeniedExceptionMapper` — maps `AuthorizationDeniedException` to `403`.
- `EvidenceObjectMissingExceptionMapper` — maps missing evidence to `410`.
- `EvidenceStorageUnavailableExceptionMapper` — maps storage failures to `503`.

## Source References

1. **OpenAPI Spec** — `/docs/api/openapi.yaml`
2. **Module Config** — `sentinel-api/pom.xml`
3. **MapStruct Mappers** — `sentinel-api/src/main/java/.../api/` (ApiCaseMapper, ApiReportMapper, ApiDecisionMapper, ApiEvidenceMapper, ApiAppealMapper, ApiRecommendationMapper, ApiMaintenanceOperationMapper, ApiWorkflowReconciliationMapper, ApiWorkflowTaskMapper)
4. **Error Handling** — `sentinel-api/src/main/java/.../api/error/ErrorResponseFactory.java` and all `*ExceptionMapper.java` files
5. **Query Pattern** — `/docs/api/list-query-pattern.md`
6. **Build** — `/Makefile`
- `GenericExceptionMapper` — catch-all for unhandled exceptions (returns `500`).

---

## List Query Pattern

All list endpoints follow a consistent cursor-based pagination and query pattern.

**Source:** `/docs/api/list-query-pattern.md`

### Query Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `cursor` | String | No | Opaque cursor for pagination. Binds sort + filter scope — cursors from different scopes are rejected. |
| `limit` | Integer | No | Maximum items per page. Default is implementation-defined (usually 20). |
| `q` | String | No | Quick search term matched across multiple text fields. |
| `searchField` | Enum | No | Field-specific search target (whitelisted enum values only). |
| `searchValue` | String | Conditional | Value for field-specific search (required if `searchField` is set). |
| `sortBy` | Enum | No | Sort field (whitelisted enum values only, e.g., `CREATED_AT`, `TITLE`). |
| `sortDirection` | Enum | No | `ASC` or `DESC`. |

### Security Rules for Dynamic SQL

**Source:** `/docs/api/list-query-pattern.md`

The documented pattern enforces strict rules to prevent SQL injection:

1. **Never** use `${sortBy}` or `${orderBy}` from client input.
2. Use `<choose>` to map a whitelisted `sortBy` enum to a safe column expression.
3. Use `<where>` or `<trim>` for optional filters.
4. Use `<foreach>` for `IN (...)` lists.
5. Use `<set>` for dynamic update statements.

### MyBatis XML Snippets

From `/docs/api/list-query-pattern.md`:

**Column mapping (safe sort):**
```xml
<choose>
  <when test='sortBy == "CREATED_AT"'>created_at</when>
  <when test='sortBy == "TITLE"'>LOWER(title)</when>
  <otherwise>created_at</otherwise>
</choose>
```

**Quick search across fields:**
```xml
<trim prefix="(" suffix=")" prefixOverrides="OR ">
  OR LOWER(title) LIKE #{quickSearchPattern}
  OR LOWER(summary) LIKE #{quickSearchPattern}
</trim>
```

**Cursor pagination:**
```xml
<if test="cursor != null">
  AND (created_at, id) < (#{cursor.createdAt}, #{cursor.id})
</if>
```

### Reference Implementations

- `CaseMyBatisMapper.findCasePage(...)` — demonstrates the full pattern with `if`, `choose`, `when`, `otherwise`, `trim`, `where`, and `foreach`.
- `CaseMyBatisMapper.findAuditEventsPage(...)` — same pattern for audit events.
- `WorkflowTaskApplicationService` — maintains the same public contract when the source data comes from the Camunda workflow engine (not direct MyBatis queries).

---

## Bean Validation

All API request bodies use `@Valid` on the JAX-RS resource method parameters. Generated DTOs carry `jakarta.validation` annotations derived from the OpenAPI schema:

- `@NotNull`, `@Size`, `@Pattern`, `@Min`, `@Max` — generated from OpenAPI `required`, `minLength`, `maxLength`, `pattern`, `minimum`, `maximum`.
- `@Valid` — generated for nested objects.

**ConstraintViolationExceptionMapper** converts validation failures into an RFC 7807 response with field-level `violations[]`:

```json
{
  "type": "https://sentinel.local/errors/validation-error",
  "title": "Bad Request",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "detail": "Request validation failed.",
  "instance": "/api/v1/cases",
  "correlationId": "...",
  "violations": [
    { "field": "createCaseRequest.title", "message": "must not be null" },
    { "field": "createCaseRequest.summary", "message": "size must be between 1 and 5000" }
  ]
}
```

---

## Source Files Reference

| File | Path |
|---|---|
| OpenAPI specification | `/docs/api/openapi.yaml` |
| List query pattern docs | `/docs/api/list-query-pattern.md` |
| sentinel-api POM | `/sentinel-api/pom.xml` |
| ApiCaseMapper.java | `sentinel-api/src/main/java/com/sentinel/enforcement/api/casefile/ApiCaseMapper.java` |
| ApiReportMapper.java | `sentinel-api/src/main/java/com/sentinel/enforcement/api/report/ApiReportMapper.java` |
| ApiDecisionMapper.java | `sentinel-api/src/main/java/com/sentinel/enforcement/api/decision/ApiDecisionMapper.java` |
| ApiEvidenceMapper.java | `sentinel-api/src/main/java/com/sentinel/enforcement/api/evidence/ApiEvidenceMapper.java` |
| ApiAppealMapper.java | `sentinel-api/src/main/java/com/sentinel/enforcement/api/appeal/ApiAppealMapper.java` |
| ApiRecommendationMapper.java | `sentinel-api/src/main/java/com/sentinel/enforcement/api/recommendation/ApiRecommendationMapper.java` |
| ApiMaintenanceOperationMapper.java | `sentinel-api/src/main/java/com/sentinel/enforcement/api/operations/ApiMaintenanceOperationMapper.java` |
| ApiWorkflowReconciliationMapper.java | `sentinel-api/src/main/java/com/sentinel/enforcement/api/workflow/ApiWorkflowReconciliationMapper.java` |
| ApiWorkflowTaskMapper.java | `sentinel-api/src/main/java/com/sentinel/enforcement/api/workflow/ApiWorkflowTaskMapper.java` |
| ErrorResponseFactory.java | `sentinel-api/src/main/java/com/sentinel/enforcement/api/error/ErrorResponseFactory.java` |
| ConstraintViolationExceptionMapper.java | `sentinel-api/src/main/java/com/sentinel/enforcement/api/error/ConstraintViolationExceptionMapper.java` |
| Makefile | `/Makefile` |
