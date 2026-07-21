# Authorization

## 7-Axis Evaluation Model

The `RoleBasedAuthorizationService` evaluates access along **seven axes in strict order**. Each axis acts as a gate — if any fails, access is denied.

---

## Evaluation Order

```
requirePermission(actor, permission, context)
  │
  AXIS 0: SYSTEM_ADMIN? → GRANT (immediate bypass)
  AXIS 1: Has required role(s)?
  AXIS 2: Has jurisdiction?
  AXIS 3: Has case classification clearance?
  AXIS 4: Conflicted with resource owner?
  AXIS 5: Has assigned unit access?
  AXIS 6: Directly assigned? (pure investigators only)
  │
  ▼
  GRANT
```

---

## Axis 0 — System Admin Bypass

- If actor has role `SYSTEM_ADMIN` → **immediate GRANT**
- All other axes are skipped
- This is a hardcoded bypass, not a permission mapping

---

## Axis 1 — Role Check

Each `Permission` maps to a set of allowed role strings. Actor must hold at least one.

[Full permission-to-role mapping table](../domain/permissions.md)

---

## Axis 2 — Jurisdiction Check

- Resource `jurisdictionCode` vs actor `jurisdictions`
- If resource jurisdiction is null → skip (no territorial scope)
- Example: case in `JKT` jurisdiction → only actors with `JKT` in their jurisdictions can access

---

## Axis 3 — Case Classification Clearance

- Resource `classification` vs actor `caseClassifications`
- If classification is null → skip
- Classifications: `PUBLIC < CONFIDENTIAL < SECRET`
- Default (when JWT claim is absent): **all classifications**

---

## Axis 4 — Conflict of Interest

- Resource `resourceOwnerId` vs actor `conflictedActorIds`
- If matched → blocked from accessing that resource
- Configured via Keycloak user attribute `conflicted_actor_ids`

---

## Axis 5 — Assigned Unit Scope

Only enforced when:
1. Resource has a non-null `assignedUnitId`
2. Authorization scope is not `NONE`
3. Actor is "unit-scoped" (has an operational role)

**Not unit-scoped:** `AUDITOR`, `SYSTEM_ADMIN`

---

## Axis 6 — Direct Assignment (Investigator Guard)

Only applies to **pure INVESTIGATOR** (has `INVESTIGATOR` role but NO other operational role).

For pure investigators, these permissions require the actor's username to match the resource's `assigneeUserId`:
- `READ_CASE`
- `TRANSITION_CASE`
- `CREATE_EVIDENCE_UPLOAD_SESSION`
- `FINALIZE_EVIDENCE`
- `READ_EVIDENCE`
- `CREATE_EVIDENCE_DOWNLOAD_SESSION`
- `CREATE_RECOMMENDATION`
- `SUBMIT_RECOMMENDATION`

If the investigator also holds any other role (SUPERVISOR, TRIAGE_OFFICER, etc.), this axis is not enforced.

---

## Authorization Scopes

```java
public enum CaseAuthorizationScope {
    NONE,                                    // No unit scope enforcement
    RESTRICTED_TO_ASSIGNED_UNITS,            // Always enforce (future use)
    RESTRICTED_TO_ASSIGNED_UNITS_WHEN_PRESENT // Enforce when unitId is non-null
}
```

---

## AuthorizationContext

```java
public record AuthorizationContext(
    String jurisdictionCode,
    String resourceType,
    String resourceId,
    UUID caseId,
    String assigneeUserId,
    String assignedUnitId,
    CaseClassification caseClassification,
    String resourceOwnerId,
    CaseAuthorizationScope authorizationScope
) {}
```

---

## Example: Investigator Access

```
Actor: investigator-jkt
Roles: [INVESTIGATOR]
Jurisdictions: [JKT]
Assigned Units: [JKT-UNIT-1]
Classifications: [PUBLIC, CONFIDENTIAL]

Permission: READ_CASE
Case jurisdiction: JKT
Case assigned unit: JKT-UNIT-1
Case assignee: investigator-jkt

Axis 0: Not SYSTEM_ADMIN → continue
Axis 1: INVESTIGATOR has READ_CASE → pass
Axis 2: JKT == JKT → pass
Axis 3: Classification check → pass
Axis 4: No conflict → pass
Axis 5: JKT-UNIT-1 in actor's units → pass
Axis 6: Pure investigator + assignee matches → pass
→ GRANT
```

---

## Example: Blocked Cross-Jurisdiction

```
Actor: intake-bdg
Roles: [CASE_INTAKE_OFFICER]
Jurisdictions: [BDG]

Permission: READ_REPORT
Report jurisdiction: JKT

Axis 0: Not SYSTEM_ADMIN → continue
Axis 1: CASE_INTAKE_OFFICER has READ_REPORT → pass
Axis 2: JKT NOT in actor's jurisdictions [BDG] → DENY
→ 403 Forbidden
```
