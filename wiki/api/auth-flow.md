# Authentication & Authorization Flow

The API uses Bearer JWT authentication with multi-axis authorization.

---

## Request Flow

```
Client Request
  │
  ▼
CorrelationIdFilter
  ├─ Reads/generates X-Correlation-Id
  └─ Binds to MDC
  │
  ▼
BearerAuthenticationFilter
  ├─ Skips /health (public)
  ├─ Extracts Bearer token
  ├─ KeycloakTokenVerifier.verify(token)
  │   ├─ JWS signature (RS256, JWKS endpoint)
  │   ├─ Issuer check
  │   ├─ Audience check
  │   ├─ Expiration check
  │   └─ Returns ApplicationActor
  └─ Stores actor in request context
  │
  ▼
JAX-RS Resource Method
  ├─ Receives ApplicationActor via @Context
  ├─ Delegates to ApplicationService
  │   └─ AuthorizationService.requirePermission(actor, permission, context)
  └─ Returns response
```

---

## Token Verification (KeycloakTokenVerifier)

Uses **Nimbus JOSE+JWT** library with `RemoteJWKSet` to verify RS256-signed JWTs.

### Verifier Configuration

| Parameter | Env Variable | Example |
|-----------|-------------|---------|
| Issuer | `KEYCLOAK_ISSUER` | `http://localhost:8081/realms/sentinel` |
| Audience | `KEYCLOAK_AUDIENCE` | `sentinel-api` |
| JWKS URL | `KEYCLOAK_JWKS_URL` | `http://localhost:8081/realms/sentinel/protocol/openid-connect/certs` |

### Claims Extracted from JWT

| JWT Claim | ApplicationActor Field | Required |
|-----------|----------------------|----------|
| `sub` | `subject()` | Yes |
| `preferred_username` | `username()` | Yes |
| `realm_access.roles` | `roles()` | No (default: empty) |
| `jurisdictions` | `jurisdictions()` | No (default: empty) |
| `assigned_units` | `assignedUnits()` | No (default: empty) |
| `case_classifications` | `caseClassifications()` | No (default: ALL) |
| `conflicted_actor_ids` | `conflictedActorIds()` | No (default: empty) |

### Verification Steps

1. **Signature**: Fetches JWKS from Keycloak, selects RSA key matching JWT `kid`
2. **Issuer**: Must match configured issuer URI
3. **Audience**: Configured audience must be in JWT `aud` claim
4. **Expiration**: Must not be expired
5. **Not-before**: If present, must be in the past

---

## Authorization (RoleBasedAuthorizationService)

### 7-Axis Evaluation

```
requirePermission(actor, permission, context)
  │
  AXIS 0: SYSTEM_ADMIN? → GRANT
  AXIS 1: Has required role? → DENY
  AXIS 2: Has jurisdiction? → DENY
  AXIS 3: Has case classification clearance? → DENY
  AXIS 4: Conflicted with resource owner? → DENY
  AXIS 5: Has assigned unit access? → DENY
  AXIS 6: Directly assigned? (pure investigators only) → DENY
  │
  ▼
  GRANT
```

### Axis Details

**Axis 0 — System Admin Bypass**
- `SYSTEM_ADMIN` role → immediate GRANT, all other axes skipped

**Axis 1 — Role Check**
- Maps `Permission` to allowed roles
- Actor must have at least one of the required roles

**Axis 2 — Jurisdiction Check**
- If resource has a jurisdiction code, actor must have it in their jurisdictions set
- Null jurisdiction → skip

**Axis 3 — Case Classification Clearance**
- Actor's `caseClassifications` set must include the resource's classification
- Null classification → skip

**Axis 4 — Conflict of Interest**
- If resource has an owner, check if actor is conflicted with that owner
- `conflicted_actor_ids` from JWT

**Axis 5 — Assigned Unit Scope**
- Actor's `assignedUnits` must include the resource's `assignedUnitId`
- Not enforced for AUDITOR or SYSTEM_ADMIN
- Only enforced when resource has a unit assignment

**Axis 6 — Direct Assignment (Pure Investigator Guard)**
- Only applies when actor is a **pure INVESTIGATOR** (has INVESTIGATOR role but NO other operational role)
- For specific permissions, actor's username must match resource's `assigneeUserId`
- Affected permissions: READ_CASE, TRANSITION_CASE, evidence operations, recommendation operations

---

## AuthorizationContext

```java
public record AuthorizationContext(
    String jurisdictionCode,          // Territorial scope
    String resourceType,              // "CASE", "REPORT", "EVIDENCE"
    String resourceId,                // Resource identifier
    UUID caseId,                      // Related case
    String assigneeUserId,            // Assigned user
    String assignedUnitId,            // Assigned unit
    CaseClassification caseClassification,  // Classification level
    String resourceOwnerId,           // Owner/creator (for conflict check)
    CaseAuthorizationScope authorizationScope  // Unit scope mode
) {}
```

---

## Obtaining a Token

```bash
curl -s -X POST http://localhost:8081/realms/sentinel/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=sentinel-api" \
  -d "username=intake-jkt" \
  -d "password=sentinel" \
  -d "grant_type=password" \
  | jq -r '.access_token'
```
