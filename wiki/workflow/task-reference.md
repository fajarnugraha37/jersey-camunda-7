# Workflow Task Reference

## User Tasks (19 total)

### Regulatory Enforcement Case (17)

| # | Task Key | Name | Candidate Groups | Process Area |
|---|----------|------|-----------------|--------------|
| 1 | `triageTask` | Triage Case | TRIAGE_OFFICER, SUPERVISOR | Intake |
| 2 | `optionalLegalAdvisoryTask` | Optional Legal Advisory Review | CASE_REVIEWER, SUPERVISOR | Investigation |
| 3 | `financialReviewTask` | Financial Analysis Review | CASE_REVIEWER, SUPERVISOR | Investigation |
| 4 | `investigationTask` | Investigate Case | INVESTIGATOR, SUPERVISOR | Investigation |
| 5 | `legalAdvisoryTask` | Request Legal Advisory | CASE_REVIEWER, SUPERVISOR | Recommendation |
| 6 | `reviewTask` | Review Recommendation | CASE_REVIEWER, SUPERVISOR | Recommendation |
| 7 | `supervisorReviewTask` | Supervisor Review | SUPERVISOR | Recommendation |
| 8 | `recommendationRevisionTask` | Revise Recommendation | INVESTIGATOR, SUPERVISOR | Recommendation |
| 9 | `decisionTask` | Approve Decision | DECISION_MAKER, SUPERVISOR | Decision |
| 10 | `reviewRegistryFailureTask` | Review Registry Failure | SUPERVISOR, SYSTEM_ADMIN | Publication |
| 11 | `reviewNotificationFailureTask` | Review Notification Failure | SUPERVISOR, SYSTEM_ADMIN | Publication |
| 12 | `supervisorOverrideReviewTask` | Supervisor Override Review | SUPERVISOR, SYSTEM_ADMIN | Escalation |
| 13 | `globalHoldOverrideReviewTask` | Supervisor Review Global Hold | SUPERVISOR, SYSTEM_ADMIN | Escalation |
| 14 | `monitorPaymentObligationTask` | Monitor Payment Obligation | SUPERVISOR, CASE_REVIEWER | Enforcement |
| 15 | `monitorCorrectiveActionTask` | Monitor Corrective Action | SUPERVISOR, CASE_REVIEWER | Enforcement |
| 16 | `monitorReportingObligationTask` | Monitor Reporting Obligation | SUPERVISOR, CASE_REVIEWER | Enforcement |
| 17 | `additionalEnforcementActionTask` | Escalate Enforcement Action | SUPERVISOR, SYSTEM_ADMIN | Enforcement |

### Decision Appeal Review (2)

| # | Task Key | Name | Candidate Groups |
|---|----------|------|-----------------|
| 18 | `appealReviewTask` | Review Appeal | APPEAL_OFFICER, SUPERVISOR |
| 19 | `appealSupervisorOverrideReviewTask` | Appeal Supervisor Override Review | SUPERVISOR, SYSTEM_ADMIN |

---

## Task Lifecycle

```
Task appears → Claim → Complete → Next task(s) appear (or process ends)
```

### Claim Rules
- Task must not already be claimed
- Claimer must be in task's candidate groups
- Duplicate claim → `409 WorkflowTaskConflictException`

### Complete Rules
- Task must be claimed first
- Duplicate complete → **idempotent** (no error, no double-effect)
- If last task in process → process marked COMPLETED in `workflow_instance`

---

## Task List API

```
GET /api/v1/tasks?cursor=...&q=...&searchField=...&caseId=...&assigneeUserId=...&state=...
```

Returns cursor-paginated list of active tasks joined with case data.

### Filters
| Parameter | Description |
|-----------|-------------|
| `cursor` | Pagination cursor |
| `q` | Quick search across fields |
| `searchField`, `searchValue` | Field-targeted search |
| `caseId` | Filter by case |
| `assigneeUserId` | Filter by assignee |
| `state` | Task state filter |
| `sortBy`, `sortDirection` | Sort (whitelisted fields only) |
| `limit` | Max results |

---

## Service Tasks (7, simulated)

Handled by `MockWorkflowServiceDelegate`:

| Activity ID | Variable Set |
|-------------|-------------|
| `createPublicationPackageTask` | `publicationPackageCreated = true` |
| `evidenceSufficiencyRuleTask` | `evidenceSufficient = true` |
| `determineSanctionPackageTask` | `sanctionPackageDetermined = true` |
| `createObligationScheduleTask` | `obligationScheduleCreated = true` |
| `simulateExternalEvidenceDeliveredTask` | `externalEvidenceDelivered = true` |
| `simulateRegistryAcknowledgmentTask` | `registryAcknowledgmentFailed = false` |
| `simulateNotificationResultTask` | `notificationResultFailed = false` |
| `markManualNotificationRequiredTask` | `manualNotificationRequired = true` |
