# BPMN Models

Two Camunda BPMN processes deployed at startup.

---

## 1. Regulatory Enforcement Case

**Process ID:** `regulatoryEnforcementCase`  
**File:** `bpmn/regulatory-enforcement-case.bpmn`  
**History TTL:** 30 days

### Top-Level Structure

```
MessageStart (CaseCreatedMessage)
    │
    ▼
intake subprocess
    │
    ▼
investigationAndEvidenceSubProcess
    │
    ▼
recommendationReviewSubProcess
    │
    ▼
decisionPublicationSubProcess
    │
    ▼
enforcementMonitoringSubProcess
```

### Subprocesses

| Subprocess | Key Tasks |
|------------|-----------|
| **intake** | `preTriageValidationTask` → gateway → `triageTask` |
| **investigationAndEvidenceSubProcess** | Investigation tracks + evidence collection |
| **recommendationReviewSubProcess** | Multi-party review with revision loop |
| **decisionPublicationSubProcess** | Decision, sanction, publication, appeal window |
| **enforcementMonitoringSubProcess** | Parallel monitoring tracks (payment, corrective action, reporting) |
| **supervisorOverrideEventSubProcess** | Non-interrupting event subprocess for escalation handling |
| **globalHoldEventSubProcess** | Interrupting event subprocess for global hold |

### User Tasks (15)

| Task Key | Name | Candidate Groups |
|----------|------|-----------------|
| `triageTask` | Triage Case | TRIAGE_OFFICER, SUPERVISOR |
| `optionalLegalAdvisoryTask` | Optional Legal Advisory Review | CASE_REVIEWER, SUPERVISOR |
| `financialReviewTask` | Financial Analysis Review | CASE_REVIEWER, SUPERVISOR |
| `investigationTask` | Investigate Case | INVESTIGATOR, SUPERVISOR |
| `legalAdvisoryTask` | Request Legal Advisory | CASE_REVIEWER, SUPERVISOR |
| `reviewTask` | Review Recommendation | CASE_REVIEWER, SUPERVISOR |
| `supervisorReviewTask` | Supervisor Review | SUPERVISOR |
| `recommendationRevisionTask` | Revise Recommendation | INVESTIGATOR, SUPERVISOR |
| `decisionTask` | Approve Decision | DECISION_MAKER, SUPERVISOR |
| `reviewRegistryFailureTask` | Review Registry Failure | SUPERVISOR, SYSTEM_ADMIN |
| `reviewNotificationFailureTask` | Review Notification Failure | SUPERVISOR, SYSTEM_ADMIN |
| `supervisorOverrideReviewTask` | Supervisor Override Review | SUPERVISOR, SYSTEM_ADMIN |
| `globalHoldOverrideReviewTask` | Supervisor Review Global Hold | SUPERVISOR, SYSTEM_ADMIN |
| `monitorPaymentObligationTask` | Monitor Payment Obligation | SUPERVISOR, CASE_REVIEWER |
| `monitorCorrectiveActionTask` | Monitor Corrective Action | SUPERVISOR, CASE_REVIEWER |
| `monitorReportingObligationTask` | Monitor Reporting Obligation | SUPERVISOR, CASE_REVIEWER |
| `additionalEnforcementActionTask` | Escalate Enforcement Action | SUPERVISOR, SYSTEM_ADMIN |

### Messages

| Message Name | Usage |
|-------------|-------|
| `CaseCreatedMessage` | Start event |
| `ExternalEvidenceDelivered` | Receive task (await external evidence) |
| `SanctionRegistryAcknowledged` | Receive task (await registry ack) |
| `NotificationResultReceived` | Receive task (await notification result) |
| `AppealFiled` | Intermediate catch event |
| `AppealResolved` | Intermediate catch event |

### Escalation Timers

| Timer | Attachment | Duration | Effect |
|-------|-----------|----------|--------|
| Triage SLA | `triageTask` | PT30M (non-interrupting) | Fires supervisor override escalation |
| Investigation | `investigationTask` | Dynamic `${investigationEscalationDuration}` | Writes audit event + supervisor escalation |
| Appeal period | decision subprocess | P14D | Closes case if no appeal filed |

---

## 2. Decision Appeal Review

**Process ID:** `decisionAppealReview`  
**File:** `bpmn/decision-appeal-review.bpmn`  
**History TTL:** 30 days

### Structure

```
MessageStart (AppealWorkflowStarted)
    │
    ▼
appealReviewTask
    │
    ▼
appealDecisionOutcomeGateway
    ├── Granted → end
    └── Denied → enforcement monitoring
```

### User Tasks

| Task Key | Name | Candidate Groups |
|----------|------|-----------------|
| `appealReviewTask` | Review Appeal | APPEAL_OFFICER, SUPERVISOR |
| `appealSupervisorOverrideReviewTask` | Appeal Supervisor Override Review | SUPERVISOR, SYSTEM_ADMIN |

### Timer

| Timer | Attachment | Duration | Effect |
|-------|-----------|----------|--------|
| Appeal reminder | `appealReviewTask` | P1D (non-interrupting) | Informational reminder only |

### Signal
| Signal | Thrown By | Caught By |
|--------|-----------|-----------|
| `GlobalHoldSignal` | Appeal workflow (on `appealGlobalHoldRequested` path) | Main process global hold subprocess |

---

## Java Delegates

| Delegate | Purpose |
|----------|---------|
| `PreTriageRoutingDelegate` | Seeds 20 default boolean variables at process start |
| `InvestigationEscalationDelegate` | Writes audit event on investigation timer expiry |
| `MockWorkflowServiceDelegate` | Simulates 7 service tasks (publication, sufficiency, sanction, obligation, evidence, registry, notification) |
