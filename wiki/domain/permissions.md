# Permissions & Role Mapping

## Permission Enum (30 values)

**Location:** `sentinel-application/src/main/java/.../application/security/Permission.java`

```java
public enum Permission {
    CREATE_REPORT,
    READ_REPORT,
    TRIAGE_REPORT,
    CREATE_CASE,
    READ_CASE,
    LIST_CASES,
    ASSIGN_CASE,
    TRANSITION_CASE,
    READ_CASE_AUDIT,
    CREATE_EVIDENCE_UPLOAD_SESSION,
    FINALIZE_EVIDENCE,
    READ_EVIDENCE,
    CREATE_EVIDENCE_DOWNLOAD_SESSION,
    CREATE_RECOMMENDATION,
    SUBMIT_RECOMMENDATION,
    REVIEW_RECOMMENDATION,
    CREATE_DECISION,
    APPROVE_DECISION,
    PUBLISH_DECISION,
    CREATE_APPEAL,
    DECIDE_APPEAL,
    LIST_TASKS,
    CLAIM_TASK,
    COMPLETE_TASK,
    RECONCILE_WORKFLOW,
    MANAGE_CASE_RELATIONSHIPS,
    RUN_MAINTENANCE_OPERATION
}
```

---

## Permission → Role Mapping

| Permission | Allowed Roles |
|-----------|---------------|
| `CREATE_REPORT` | `CASE_INTAKE_OFFICER` |
| `READ_REPORT` | `CASE_INTAKE_OFFICER`, `TRIAGE_OFFICER`, `AUDITOR` |
| `TRIAGE_REPORT` | `TRIAGE_OFFICER`, `SUPERVISOR` |
| `CREATE_CASE` | `TRIAGE_OFFICER`, `SUPERVISOR` |
| `READ_CASE`, `LIST_CASES` | `TRIAGE_OFFICER`, `INVESTIGATOR`, `CASE_REVIEWER`, `DECISION_MAKER`, `APPEAL_OFFICER`, `SUPERVISOR`, `AUDITOR` |
| `ASSIGN_CASE` | `TRIAGE_OFFICER`, `SUPERVISOR` |
| `TRANSITION_CASE` | `TRIAGE_OFFICER`, `INVESTIGATOR`, `CASE_REVIEWER`, `DECISION_MAKER`, `APPEAL_OFFICER`, `SUPERVISOR` |
| `READ_CASE_AUDIT` | `SUPERVISOR`, `AUDITOR` |
| Evidence permissions | `TRIAGE_OFFICER`, `INVESTIGATOR`, `CASE_REVIEWER`, `DECISION_MAKER`, `APPEAL_OFFICER`, `SUPERVISOR`, `AUDITOR` |
| `CREATE_RECOMMENDATION`, `SUBMIT_RECOMMENDATION` | `INVESTIGATOR`, `SUPERVISOR` |
| `REVIEW_RECOMMENDATION` | `CASE_REVIEWER`, `SUPERVISOR` |
| `CREATE_DECISION`, `APPROVE_DECISION`, `PUBLISH_DECISION` | `DECISION_MAKER`, `SUPERVISOR` |
| `CREATE_APPEAL`, `DECIDE_APPEAL` | `APPEAL_OFFICER`, `SUPERVISOR` |
| Task permissions | `TRIAGE_OFFICER`, `INVESTIGATOR`, `CASE_REVIEWER`, `DECISION_MAKER`, `APPEAL_OFFICER`, `SUPERVISOR` |
| `RECONCILE_WORKFLOW` | `SUPERVISOR` |
| `MANAGE_CASE_RELATIONSHIPS` | `TRIAGE_OFFICER`, `SUPERVISOR` |
| `RUN_MAINTENANCE_OPERATION` | `SUPERVISOR` |

---

## Role Strings

| Role | Category | Operational Scope |
|------|----------|------------------|
| `SYSTEM_ADMIN` | Super-admin | Bypasses ALL authorization checks (Axis 0) |
| `CASE_INTAKE_OFFICER` | Intake | Report creation and reading |
| `TRIAGE_OFFICER` | Triage | Report triage, case creation, case assignment |
| `INVESTIGATOR` | Investigation | Case work, evidence, recommendations |
| `CASE_REVIEWER` | Review | Case review, recommendation review |
| `DECISION_MAKER` | Decision | Decision creation, approval, publication |
| `APPEAL_OFFICER` | Appeal | Appeal filing and decisions |
| `SUPERVISOR` | Supervision | All permissions (except pure intake) |
| `AUDITOR` | Audit | Read-only access to reports, cases, evidence |

---

## Authorization Axes (7-Axis Model)

The authorization service evaluates 7 axes in strict order. Any axis can DENY the request.

```
AXIS 0: SYSTEM_ADMIN? → GRANT (bypass all)
AXIS 1: Has required role(s)?
AXIS 2: Has jurisdiction?
AXIS 3: Has case classification clearance?
AXIS 4: Conflicted with resource owner?
AXIS 5: Has assigned unit access?
AXIS 6: Directly assigned (pure investigators only)?
```

[Full details in Authorization documentation](../security/authorization.md)
