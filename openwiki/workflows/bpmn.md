---
type: Playbook
title: BPMN Workflows
description: Embedded Camunda 7 integration details, BPMN process definitions, workflow correlation, and reconciliation tooling in Sentinel.
tags: [workflow, bpmn, camunda, processes]
---

# BPMN Workflows

## Embedded Camunda Integration

Sentinel uses **Embedded Camunda 7.24.0** as the BPMN workflow engine. The engine runs in-process within the Grizzly application (no separate Camunda deployment).

The [domain model](/openwiki/domain/concepts.md) state transitions (e.g., `CREATED &rarr; UNDER_TRIAGE`) are correlated to workflow tasks via the `workflow_instance` table. Lifecycle events from workflow progression flow through the [transactional outbox](/openwiki/integrations/messaging-storage.md#transactional-outbox-pattern) for reliable event delivery.

### Architecture

```
Application service
  &rarr; CaseWorkflowPort (interface)
    &rarr; CamundaCaseWorkflowAdapter
      &rarr; RuntimeService, TaskService (Camunda Java API)
        &rarr; ACT_* tables in shared PostgreSQL
```

Key design characteristics (from `/docs/architecture/camunda-embedded-integration.md`):

- **Schema split**: Liquibase manages application schema (`sentinel_*` tables); Camunda manages `ACT_*` tables via official SQL resources
- **Explicit migration**: `CamundaSchemaMigrator` runs before app startup; `databaseSchemaUpdate=false`
- **Singleton engine**: `SingleProcessEngineProvider` bound via HK2
- **Correlation**: `workflow_instance` table (application-owned, MyBatis-managed) maps case IDs to process instance IDs
- **Operational reads**: Use public Camunda Java API only — never direct SQL against `ACT_*` tables
- **Transaction boundary**: Compensation-based (create case + start workflow in same transaction, but not fully atomic via outbox)

## BPMN Processes

Two BPMN files are auto-deployed on application startup:

### 1. `regulatory-enforcement-case.bpmn`
**File:** `/sentinel-workflow/src/main/resources/bpmn/regulatory-enforcement-case.bpmn` (74 KB)

The main enforcement case process. Key flow elements (validated in `BpmnModelValidationTest`):
- `caseCreatedStartEvent` &mdash; start event
- `triageTask` &mdash; triage officer reviews the case
- `investigationTask` &mdash; investigator gathers evidence
- `reviewTask` &mdash; reviewer evaluates recommendation
- `decisionTask` &mdash; decision maker prepares decision
- `appealReviewTask` &mdash; appeal officer handles appeals
- Timer boundary events &mdash; escalation for overdue investigations
- Sub-processes for detailed workflows
- Message/signal events for cross-process communication

### 2. `decision-appeal-review.bpmn`
**File:** `/sentinel-workflow/src/main/resources/bpmn/decision-appeal-review.bpmn` (10 KB)

A focused sub-process for handling decision appeals.

## Workflow Adapter Classes

### `CamundaCaseWorkflowAdapter`
**File:** `/sentinel-workflow/src/main/java/.../workflow/CamundaCaseWorkflowAdapter.java`

Implements `CaseWorkflowPort` (from `sentinel-application`). Key methods:
- `startWorkflow(caseId)` &mdash; starts a new process instance for a case
- `claimTask(taskId, actorId)` &mdash; claims a user task
- `completeTask(taskId, variables)` &mdash; completes a task with output variables
- `findTasks(query)` &mdash; lists tasks with cursor, search, sort
- Correlates the `workflow_instance` table with Camunda process instances

### `CamundaWorkflowAdministrationAdapter`
**File:** `/sentinel-workflow/src/main/java/.../workflow/CamundaWorkflowAdministrationAdapter.java`

Implements `WorkflowAdministrationPort` for reconciliation operations.

### `PreTriageRoutingDelegate`
**File:** `/sentinel-workflow/src/main/java/.../workflow/PreTriageRoutingDelegate.java`

JavaDelegate that classifies cases during workflow execution based on case data.

### `InvestigationEscalationDelegate`
**File:** `/sentinel-workflow/src/main/java/.../workflow/InvestigationEscalationDelegate.java`

Handles timer-based escalation when investigations exceed the configured duration (`WORKFLOW_INVESTIGATION_ESCALATION_DURATION`, default `PT30M`).

### `WorkflowModule`
**File:** `/sentinel-workflow/src/main/java/.../workflow/WorkflowModule.java`

HK2 DI module that binds all workflow components, including `ProcessEngineProvider`, adapters, and delegates.

## Workflow Reconciliation

The **Workflow Reconciliation** feature detects and resolves mismatches between the application's domain state (`workflow_instance` table) and the Camunda runtime/history state.

**API:** `GET /api/v1/workflow-reconciliation`, `POST /api/v1/workflow-reconciliation/{caseId}/actions`

**Reconciliation actions:**
- `AUTO_REPAIR` &mdash; Creates or restores the `workflow_instance` correlation row from Camunda runtime data
- `TERMINATE_RUNTIME` &mdash; Ends orphaned running process instances when the case is already in a terminal state

**Source:** `WorkflowReconciliationApplicationService.java`, `WorkflowReconciliationResource.java`.

## BPMN Model Validation

**Test class:** `BpmnModelValidationTest.java` (`/sentinel-workflow/src/test/java/.../workflow/`)

Validates structural integrity of both BPMN models: start events, user tasks, service tasks, boundary events, sub-processes, sequences, and message flows. Run with `make bpmn-validate` or `mvn -pl sentinel-workflow -am test`.

## Source Map

| Artifact | Path |
|---|---|
| BPMN processes | `/sentinel-workflow/src/main/resources/bpmn/` |
| Workflow Java classes | `/sentinel-workflow/src/main/java/com/sentinel/enforcement/workflow/` |
| Camunda integration docs | `/docs/architecture/camunda-embedded-integration.md` |
| Schema migration runbook | `/docs/runbooks/camunda-embedded-schema-migration.md` |
| Reconciliation runbook | `/docs/runbooks/domain-workflow-mismatch-reconciliation.md` |
| Application workflow port | `/sentinel-application/src/main/java/.../application/workflow/CaseWorkflowPort.java` |
| Reconciliation query port | `/sentinel-application/src/main/java/.../application/workflow/WorkflowReconciliationQueryPort.java` |
| Workflow persistence | `/sentinel-persistence/src/main/java/.../persistence/workflow/` |
