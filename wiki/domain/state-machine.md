# Case Lifecycle State Machine

The `CaseRecord` aggregate governs a **10-state lifecycle** with role-based transition guards.

---

## States

```
CREATED → UNDER_TRIAGE → UNDER_INVESTIGATION → PENDING_REVIEW
    → PENDING_DECISION → DECIDED → ENFORCEMENT_IN_PROGRESS → CLOSED
    ↕                    ↕
CANCELLED (terminal)    UNDER_APPEAL (< → >)
```

### Terminal States
- **CLOSED** — Final terminal state
- **CANCELLED** — Premature terminal state

---

## Transition Matrix

| From Status | Allowed Targets | Required Roles |
|-------------|----------------|----------------|
| CREATED | UNDER_TRIAGE | TRIAGE_OFFICER, SUPERVISOR |
| UNDER_TRIAGE | UNDER_INVESTIGATION, CANCELLED | TRIAGE_OFFICER, SUPERVISOR |
| UNDER_INVESTIGATION | PENDING_REVIEW, CANCELLED | INVESTIGATOR, SUPERVISOR |
| PENDING_REVIEW | UNDER_INVESTIGATION, PENDING_DECISION | CASE_REVIEWER, SUPERVISOR |
| PENDING_DECISION | UNDER_INVESTIGATION, DECIDED | DECISION_MAKER, SUPERVISOR |
| DECIDED | UNDER_APPEAL, ENFORCEMENT_IN_PROGRESS | APPEAL_OFFICER/SUPERVISOR or DECISION_MAKER/SUPERVISOR |
| UNDER_APPEAL | DECIDED, ENFORCEMENT_IN_PROGRESS, CLOSED | APPEAL_OFFICER, SUPERVISOR |
| ENFORCEMENT_IN_PROGRESS | CLOSED | SUPERVISOR |
| CLOSED | — | Terminal |
| CANCELLED | — | Terminal |

---

## Transition Rules

1. **OCC enforced**: Every transition requires the caller to provide `expectedVersion` matching the current aggregate version
2. **Role enforced**: Only actors with the required role can perform the transition
3. **No skipping**: Transitions must follow the allowed matrix (no jumping from CREATED to DECIDED)
4. **Audit trail**: Every transition creates a `CaseStatusHistoryEntry` and an `AuditEvent`
5. **Workflow correlation**: Status changes are emitted to `case.lifecycle.v1` Kafka topic via outbox

---

## Case Classification

Each case has a classification level that affects authorization:

```
PUBLIC < CONFIDENTIAL < SECRET
```

- Classification is set at case creation
- Actors must have clearance in their JWT `case_classifications` claim
- Default clearance (when claim is absent): all classifications

[See Authorization for full details](../security/authorization.md)

---

## Enums

```java
public enum CaseStatus {
    CREATED,
    UNDER_TRIAGE,
    UNDER_INVESTIGATION,
    PENDING_REVIEW,
    PENDING_DECISION,
    DECIDED,
    UNDER_APPEAL,
    ENFORCEMENT_IN_PROGRESS,
    CLOSED,
    CANCELLED;

    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }
}

public enum CaseClassification {
    PUBLIC,
    CONFIDENTIAL,
    SECRET
}

public enum CaseRelationshipType {
    MERGE,
    DERIVATION,
    SPLIT
}
```
