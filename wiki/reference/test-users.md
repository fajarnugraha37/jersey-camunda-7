# Test Users

14 users in the `sentinel` Keycloak realm. **Default password for all: `sentinel`**

---

## User Table

| Username | Role | Jurisdiction | Assigned Unit | Classifications | Special |
|----------|------|-------------|---------------|-----------------|---------|
| `intake-jkt` | CASE_INTAKE_OFFICER | JKT | — | PUBLIC, CONFIDENTIAL, SECRET | — |
| `intake-bdg` | CASE_INTAKE_OFFICER | BDG | — | PUBLIC, CONFIDENTIAL, SECRET | — |
| `triage-jkt` | TRIAGE_OFFICER | JKT | JKT-UNIT-1 | PUBLIC, CONFIDENTIAL, SECRET | — |
| `triage-bdg` | TRIAGE_OFFICER | BDG | BDG-UNIT-1 | PUBLIC, CONFIDENTIAL, SECRET | — |
| `investigator-jkt` | INVESTIGATOR | JKT | JKT-UNIT-1 | PUBLIC, CONFIDENTIAL | — |
| `reviewer-jkt` | CASE_REVIEWER | JKT | JKT-UNIT-1 | PUBLIC, CONFIDENTIAL, SECRET | — |
| `reviewer-jkt-public` | CASE_REVIEWER | JKT | JKT-UNIT-1 | PUBLIC only | — |
| `reviewer-jkt-conflicted` | CASE_REVIEWER | JKT | JKT-UNIT-1 | PUBLIC, CONFIDENTIAL, SECRET | Conflicted with `investigator-jkt` |
| `decision-jkt` | DECISION_MAKER | JKT | JKT-UNIT-1 | PUBLIC, CONFIDENTIAL, SECRET | — |
| `appeal-jkt` | APPEAL_OFFICER | JKT | JKT-UNIT-1 | PUBLIC, CONFIDENTIAL, SECRET | — |
| `supervisor-jkt` | SUPERVISOR | JKT | JKT-UNIT-1 | PUBLIC, CONFIDENTIAL, SECRET | — |
| `supervisor-jkt-unit-2` | SUPERVISOR | JKT | JKT-UNIT-2 | PUBLIC, CONFIDENTIAL, SECRET | Different unit |
| `auditor-jkt` | AUDITOR | JKT | — | PUBLIC, CONFIDENTIAL, SECRET | Read-only across units |
| `system-admin` | SYSTEM_ADMIN | JKT, BDG | — | PUBLIC, CONFIDENTIAL, SECRET | Bypasses all auth |

---

## Authorization Matrix

| User | Can Create Reports | Can Triage | Can Investigate | Can Review | Can Decide | Can Appeal | Can Supervise | Can Audit |
|------|-------------------|-----------|----------------|-----------|-----------|-----------|--------------|-----------|
| `intake-jkt` | ✅ JKT | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `intake-bdg` | ✅ BDG | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `triage-jkt` | ❌ | ✅ JKT | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `investigator-jkt` | ❌ | ❌ | ✅ JKT | ❌ | ❌ | ❌ | ❌ | ❌ |
| `reviewer-jkt` | ❌ | ❌ | ❌ | ✅ JKT | ❌ | ❌ | ❌ | ❌ |
| `decision-jkt` | ❌ | ❌ | ❌ | ❌ | ✅ JKT | ❌ | ❌ | ❌ |
| `appeal-jkt` | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ JKT | ❌ | ❌ |
| `supervisor-jkt` | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `auditor-jkt` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

---

## Keycloak Realm Roles

| Role | Description |
|------|-------------|
| `CASE_INTAKE_OFFICER` | Create and read reports |
| `TRIAGE_OFFICER` | Triage reports, create/assign cases |
| `INVESTIGATOR` | Work on assigned cases, evidence, recommendations |
| `CASE_REVIEWER` | Review recommendations |
| `DECISION_MAKER` | Create, approve, publish decisions |
| `APPEAL_OFFICER` | File and decide appeals |
| `SUPERVISOR` | All permissions (except pure intake) |
| `AUDITOR` | Read-only access across units |
| `SYSTEM_ADMIN` | Bypasses all authorization checks |

---

## Custom Claims (via Keycloak User Attributes)

| Claim | Source | Type |
|-------|--------|------|
| `jurisdictions` | User attribute | Multi-value string |
| `assigned_units` | User attribute | Multi-value string |
| `case_classifications` | User attribute | Multi-value string (PUBLIC, CONFIDENTIAL, SECRET) |
| `conflicted_actor_ids` | User attribute | Multi-value string (usernames) |

---

## Obtaining a Token

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/sentinel/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=sentinel-api" \
  -d "username=supervisor-jkt" \
  -d "password=sentinel" \
  -d "grant_type=password" | jq -r '.access_token')

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/cases
```
