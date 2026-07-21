# Authentication

## Keycloak JWT Authentication

Uses **Keycloak 26.6** as the identity provider with **Nimbus JOSE+JWT** library for token verification.

---

## Token Verification Flow

```
Bearer Token
    │
    ▼
KeycloakTokenVerifier.verify(bearerToken)
    │
    ├─ 1. Parse JWT
    │
    ├─ 2. Signature verification (Nimbus RemoteJWKSet)
    │     ├─ Fetch JWKS from Keycloak /certs endpoint
    │     ├─ Select RSA public key matching JWT's "kid"
    │     └─ Verify RS256 signature
    │
    ├─ 3. Claim validation
    │     ├─ Issuer: matches KEYCLOAK_ISSUER
    │     ├─ Audience: KEYCLOAK_AUDIENCE in "aud" list
    │     ├─ Expiration: not expired
    │     └─ Not-before: if present, must be in past
    │
    └─ 4. Build ApplicationActor
          ├─ subject ← "sub" (required)
          ├─ username ← "preferred_username" (required)
          ├─ roles ← "realm_access.roles"
          ├─ jurisdictions ← "jurisdictions"
          ├─ assignedUnits ← "assigned_units"
          ├─ caseClassifications ← "case_classifications" (default: ALL)
          └─ conflictedActorIds ← "conflicted_actor_ids"
```

---

## Keycloak Realm Configuration

| Setting | Value |
|---------|-------|
| Realm | `sentinel` |
| Client ID | `sentinel-api` |
| Client protocol | OpenID Connect |
| Access type | Public (no client secret) |
| Standard flow | Enabled |
| Direct access grants | Enabled |

### Custom Claim Mappers (Client `sentinel-api`)

| Mapper | Claim | Source |
|--------|-------|--------|
| User attribute | `jurisdictions` | User attribute `jurisdictions` |
| User attribute | `assigned_units` | User attribute `assigned_units` |
| User attribute | `case_classifications` | User attribute `case_classifications` |
| User attribute | `conflicted_actor_ids` | User attribute `conflicted_actor_ids` |
| Audience | `audience-sentinel-api` | Hardcoded |

---

## JWT Structure

```json
{
  "sub": "b3f1a2c4-...",
  "preferred_username": "intake-jkt",
  "realm_access": {
    "roles": ["CASE_INTAKE_OFFICER"]
  },
  "jurisdictions": ["JKT"],
  "assigned_units": [],
  "case_classifications": ["PUBLIC", "CONFIDENTIAL", "SECRET"],
  "conflicted_actor_ids": [],
  "aud": ["sentinel-api"],
  "iss": "http://localhost:8081/realms/sentinel",
  "exp": 1721625600,
  "iat": 1721622000
}
```

---

## ApplicationActor

```java
public record ApplicationActor(
    String subject,
    String username,
    Set<String> roles,
    Set<String> jurisdictions,
    Set<String> assignedUnits,
    Set<CaseClassification> caseClassifications,
    Set<String> conflictedActorIds
) {}
```

All set fields are defensively copied. Empty sets for missing claims.

---

## Obtaining a Token

```bash
curl -s -X POST http://localhost:8081/realms/sentinel/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=sentinel-api" \
  -d "username=intake-jkt" \
  -d "password=sentinel" \
  -d "grant_type=password"
```

---

## Configuration

| Env Variable | Description | Example |
|-------------|-------------|---------|
| `KEYCLOAK_ISSUER` | Keycloak realm issuer | `http://localhost:8081/realms/sentinel` |
| `KEYCLOAK_AUDIENCE` | Expected JWT audience | `sentinel-api` |
| `KEYCLOAK_JWKS_URL` | JWKS certificate endpoint | `http://localhost:8081/realms/sentinel/protocol/openid-connect/certs` |
