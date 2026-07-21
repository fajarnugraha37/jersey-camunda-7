# Workflow Reconciliation

Detects and repairs mismatches between database domain state and Camunda workflow runtime.

---

## Why Reconciliation?

Per ADR-002, domain state (database) is the source of truth for business state. Camunda holds orchestration state. These can diverge due to:

- Manual database intervention
- Workflow engine failures during process start
- Race conditions in message correlation
- Partial failure in two-phase operations

---

## Reconciliation Candidates

A database view joins `case_record` with `workflow_instance_correlation` to produce candidates.

### 7 Issue Types Detected

| # | Issue Type | Condition | Available Actions |
|---|-----------|-----------|------------------|
| 1 | `TERMINAL_CASE_RUNTIME_ACTIVE` | Case is DECIDED/CLOSED/CANCELLED but Camunda runtime still exists | `TERMINATE_RUNTIME` |
| 2 | `ACTIVE_RUNTIME_MISSING_CORRELATION` | Camunda runtime exists but no correlation row | `AUTO_REPAIR` |
| 3 | `ACTIVE_RUNTIME_CORRELATION_MISMATCH` | Runtime and correlation disagree on process instance ID | `AUTO_REPAIR` |
| 4 | `ACTIVE_CASE_WORKFLOW_NOT_RUNNING` | Case is pre-decision but no runtime exists | None (manual) |
| 5 | `ACTIVE_CASE_CORRELATION_TERMINAL` | Case active but correlation says COMPLETED/CANCELLED | None (manual) |
| 6 | `TERMINAL_CASE_MISSING_CORRELATION` | Case post-decision but no correlation row | `AUTO_REPAIR` |
| 7 | `TERMINAL_CASE_CORRELATION_ACTIVE` | Case post-decision but correlation says ACTIVE | `AUTO_REPAIR` |

---

## Repair Actions

### AUTO_REPAIR
1. If runtime exists and case expects active workflow → sync correlation from runtime
2. If case is post-decision → sync from finished history
3. If neither available → mark `WORKFLOW_RECONCILIATION_MANUAL_ACTION_REQUIRED`

### TERMINATE_RUNTIME
1. Call `WorkflowAdministrationPort.terminateActiveProcessInstance()`
2. Upsert correlation as terminal
3. Only allowed when case status does NOT expect active workflow

### Transaction Boundary
Both actions execute in a single transaction:
```
transactionManager.required()
  ├─ upsert workflow_instance_correlation
  ├─ append audit event
  └─ enqueue outbox event
```

---

## API

```
# List reconciliation issues
GET /api/v1/workflow-reconciliation?cursor=...&issueType=...&caseStatus=...

# Execute repair action
POST /api/v1/workflow-reconciliation/{caseId}/actions
{
  "action": "AUTO_REPAIR",
  "reason": "Auto-repairing missing correlation"
}
```

### Authorization
- Permission: `RECONCILE_WORKFLOW`
- Scope: `RESTRICTED_TO_ASSIGNED_UNITS_WHEN_PRESENT`
- Only `SUPERVISOR` role has this permission

---

## Verification via Integration Tests

The `WorkflowReconciliationApiIT` test validates:
1. Supervisor lists and auto-repairs missing active workflow correlation
2. Auto-repair terminal case from workflow history
3. Terminate runtime when case forced into terminal state
4. Reconciliation list search, sort, cursor pagination
5. Supervisor-only permission enforcement (triage officer gets 403)
