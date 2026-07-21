# HTTP Endpoint Call Traces

This page documents the **implemented call chain** for each HTTP endpoint:

```text
HTTP request
  → Jersey resource method
  → application service method
  → authorization / domain behavior
  → repository or outbound port
  → audit and/or outbox side effects
```

All endpoints except `/health` pass through `BearerAuthenticationFilter`, which calls `TokenVerifier.verify()`. Resource methods resolve the resulting actor through `RequestActorResolver.resolveRequired()`.

## Reports

### `POST /api/v1/reports`

```text
ReportResource.createReport()
  → ReportApplicationService.createReport()
  → AuthorizationService.requirePermission(CREATE_REPORT)
  → ReportRepository.save()
```

No audit or outbox call is visible in the service flow. The repository transaction behavior is adapter-specific and is not established by the resource/service source.

### `GET /api/v1/reports/{reportId}`

```text
ReportResource.getReport()
  → ReportApplicationService.getReport()
  → ReportRepository.findById()
  → AuthorizationService.requirePermission(READ_REPORT)
```

Missing reports produce `ReportNotFoundException`.

### `POST /api/v1/reports/{reportId}/triage`

```text
ReportResource.triageReport()
  → ReportApplicationService.triageReport()
  → ReportRepository.findById()
  → AuthorizationService.requirePermission(TRIAGE_REPORT)
  → Report.triage()
  → ReportRepository.update()
```

`Report.triage()` validates the current status, actor, reason, and expected version. The inspected service flow has no explicit audit or outbox call.

## Cases

### `POST /api/v1/cases`

```text
CaseResource.createCase()
  → CaseApplicationService.createCase()
  → ReportRepository.findById()
  → AuthorizationService.requirePermission(CREATE_CASE)
  → CaseRepository.nextCaseNumber()
  → CaseWorkflowPort.startCaseWorkflow()
      → CamundaCaseWorkflowAdapter.startCaseWorkflow()
      → RuntimeService.startProcessInstanceByMessage()
      → WorkflowInstanceStore.saveStarted()
  → ApplicationTransactionManager.required()
      → CaseRepository.save()
      → CaseRepository.appendAuditEvent()
      → OutboxRepository.enqueue(audit.integration.v1)
      → OutboxRepository.enqueue(case.lifecycle.v1 / CaseCreated)
```

Workflow start occurs before the database transaction. If persistence fails, the service attempts compensating workflow cancellation.

### `GET /api/v1/cases`

```text
CaseResource.listCases()
  → CaseCursorCodec.decode()
  → CaseApplicationService.listCases()
  → AuthorizationService.requirePermission(LIST_CASES)
  → CaseRepository.findPage(CasePageRequest)
  → cursor/page trimming
  → CaseCursorCodec.encode()
```

The repository query applies status, classification, unit, assignee, search, sort, and cursor filters.

### `GET /api/v1/cases/{caseId}`

```text
CaseResource.getCase()
  → CaseApplicationService.getCase()
  → CaseRepository.findById()
  → AuthorizationService.requirePermission(READ_CASE)
```

### `POST /api/v1/cases/{caseId}/assignments`

```text
CaseResource.assignCase()
  → CaseApplicationService.assignCase()
  → CaseRepository.findById()
  → AuthorizationService.requirePermission(ASSIGN_CASE)
  → CaseRecord.assignTo()
  → ApplicationTransactionManager.required()
      → CaseRepository.assign()
      → CaseRepository.appendAuditEvent()
      → OutboxRepository.enqueue(audit.integration.v1)
      → OutboxRepository.enqueue(case.assignment.v1 / CaseAssigned)
```

The persistence adapter performs assignment rotation atomically and uses optimistic locking. A no-op assignment produces a conflict.

### `POST /api/v1/cases/{caseId}/relationships`

```text
CaseResource.createCaseRelationship()
  → CaseApplicationService.createRelationship()
  → CaseRepository.findById(anchor)
  → CaseRepository.findById(related)
  → AuthorizationService.requirePermission(MANAGE_CASE_RELATIONSHIPS)
  → CaseRepository.wouldCreateRelationshipCycle()
  → ApplicationTransactionManager.required()
      → CaseRepository.createRelationship()
      → CaseRepository.appendAuditEvent()
      → OutboxRepository.enqueue(audit.integration.v1)
```

The recursive cycle check runs before insertion. Duplicate and self/cycle relationships are rejected.

### `GET /api/v1/cases/{caseId}/relationships`

```text
CaseResource.listCaseRelationships()
  → CaseApplicationService.listRelationships()
  → CaseRepository.findById()
  → AuthorizationService.requirePermission(READ_CASE)
  → CaseRepository.findRelationships()
```

### `POST /api/v1/cases/{caseId}/transitions`

```text
CaseResource.transitionCase()
  → CaseApplicationService.transitionCase()
  → CaseRepository.findById()
  → AuthorizationService.requirePermission(TRANSITION_CASE)
  → CaseProgressionGuard.requireTargetStatePrerequisites()
  → CaseRecord.transitionTo()
  → ApplicationTransactionManager.required()
      → CaseRepository.transition()
      → CaseRepository.appendAuditEvent()
      → OutboxRepository.enqueue(audit.integration.v1)
      → OutboxRepository.enqueue(case.lifecycle.v1 / CaseTransitioned)
```

The domain aggregate validates allowed transitions, role requirements, terminal states, and expected version.

### `GET /api/v1/cases/{caseId}/audit-events`

```text
CaseResource.getCaseAuditEvents()
  → AuditCursorCodec.decode()
  → CaseApplicationService.getCaseAuditEvents()
  → CaseRepository.findById()
  → AuthorizationService.requirePermission(READ_CASE_AUDIT)
  → CaseRepository.findAuditEventsPage()
  → cursor/page trimming
  → AuditCursorCodec.encode()
```

## Evidence

### `POST /api/v1/cases/{caseId}/evidence/upload-sessions`

```text
CaseEvidenceResource.createUploadSession()
  → EvidenceApplicationService.createUploadSession()
  → CaseRepository.findById()
  → AuthorizationService.requirePermission(CREATE_EVIDENCE_UPLOAD_SESSION)
  → EvidenceRepository.findEvidenceById()       [existing evidence/version]
  → EvidenceRepository.prepareNewEvidenceUpload()
    or EvidenceRepository.prepareExistingEvidenceUpload()
  → EvidenceStoragePort.createPresignedUploadUrl()
```

The source does not show an explicit transaction wrapping persistence and presigned URL creation. Storage and persistence failures are translated separately.

### `GET /api/v1/evidence/{evidenceId}`

```text
EvidenceResource.getEvidence()
  → EvidenceApplicationService.getEvidence()
  → EvidenceRepository.findEvidenceById()
  → CaseRepository.findById()
  → AuthorizationService.requirePermission(READ_EVIDENCE)
  → EvidenceRepository.findLatestVersion()
```

### `POST /api/v1/evidence/{evidenceId}/versions/finalize`

```text
EvidenceResource.finalizeEvidenceVersion()
  → EvidenceApplicationService.finalizeEvidenceVersion()
  → EvidenceRepository.findEvidenceById()
  → CaseRepository.findById()
  → AuthorizationService.requirePermission(FINALIZE_EVIDENCE)
  → EvidenceRepository.findUploadSessionById()
  → EvidenceStoragePort.statObject()
  → EvidenceStoragePort.getObjectStream()
      → SHA-256 calculation
  → Evidence domain finalize/activate behavior
  → ApplicationTransactionManager.required()
      → EvidenceRepository.finalizeUpload()
      → CaseRepository.appendAuditEvent()
      → OutboxRepository.enqueue(audit.integration.v1)
      → OutboxRepository.enqueue(evidence.lifecycle.v1 / EvidenceVersionFinalized)
```

Finalization verifies object existence, size, media type, and SHA-256 before activation.

### `POST /api/v1/evidence/{evidenceId}/download-sessions`

```text
EvidenceResource.createDownloadSession()
  → EvidenceApplicationService.createDownloadSession()
  → EvidenceRepository.findEvidenceById()
  → CaseRepository.findById()
  → EvidenceRepository.findLatestVersion()
  → AuthorizationService.requirePermission(CREATE_EVIDENCE_DOWNLOAD_SESSION)
  → ApplicationTransactionManager.required()
      → CaseRepository.appendAuditEvent()
      → OutboxRepository.enqueue(audit.integration.v1)
  → EvidenceStoragePort.createPresignedDownloadUrl()
```

Denied access follows a separate path: the authorization exception is caught, a denied audit event is persisted, and the exception is rethrown.

## Recommendations

### `POST /api/v1/cases/{caseId}/recommendations`

```text
CaseRecommendationResource.createRecommendation()
  → RecommendationApplicationService.createRecommendation()
  → CaseRepository.findById()
  → AuthorizationService.requirePermission(CREATE_RECOMMENDATION)
  → RecommendationRepository.findByCaseId()
  → Recommendation.create()
  → ApplicationTransactionManager.required()
      → RecommendationRepository.save()
      → CaseRepository.appendAuditEvent()
      → OutboxRepository.enqueue(audit.integration.v1)
```

The service rejects cases outside the investigation stage and duplicate recommendations.

### `POST /api/v1/recommendations/{recommendationId}/submit`

```text
RecommendationResource.submitRecommendation()
  → RecommendationApplicationService.submitRecommendation()
  → RecommendationRepository.findById()
  → CaseRepository.findById()
  → AuthorizationService.requirePermission(SUBMIT_RECOMMENDATION)
  → author/supervisor check
  → Recommendation.submit()
  → ApplicationTransactionManager.required()
      → RecommendationRepository.submit()
      → CaseRepository.appendAuditEvent()
      → OutboxRepository.enqueue(audit.integration.v1)
```

### `POST /api/v1/recommendations/{recommendationId}/reviews`

```text
RecommendationResource.approveRecommendation()
  → RecommendationApplicationService.approveRecommendation()
  → RecommendationRepository.findById()
  → CaseRepository.findById()
  → AuthorizationService.requirePermission(REVIEW_RECOMMENDATION)
  → case status check
  → Recommendation.approve()
  → ApplicationTransactionManager.required()
      → RecommendationRepository.approve()
      → CaseRepository.appendAuditEvent()
      → OutboxRepository.enqueue(audit.integration.v1)
```

`Recommendation.approve()` enforces maker-checker: the reviewer cannot be the creator.

## Decisions

### `POST /api/v1/cases/{caseId}/decisions`

```text
CaseDecisionResource.createDecision()
  → DecisionApplicationService.createDecision()
  → CaseRepository.findById()
  → RecommendationRepository.findByCaseId()
      → require recommendation status APPROVED
  → AuthorizationService.requirePermission(CREATE_DECISION)
  → case status check
  → DecisionRepository.findByCaseId()
  → Decision.create()
  → ApplicationTransactionManager.required()
      → DecisionRepository.save()
      → CaseRepository.appendAuditEvent()
      → OutboxRepository.enqueue(audit.integration.v1)
```

### `POST /api/v1/decisions/{decisionId}/approve`

```text
DecisionResource.approveDecision()
  → DecisionApplicationService.approveDecision()
  → ApplicationTransactionManager.required(READ_COMMITTED, "approve-decision")
      → DecisionRepository.findByIdForUpdate()
      → CaseRepository.findById()
      → AuthorizationService.requirePermission(APPROVE_DECISION)
      → Decision.approve()
      → DecisionRepository.approve()
      → CaseRepository.appendAuditEvent()
      → OutboxRepository.enqueue(audit.integration.v1)
```

The row lock uses `FOR UPDATE NOWAIT`; lock contention is translated to a conflict response.

### `POST /api/v1/decisions/{decisionId}/publish`

```text
DecisionResource.publishDecision()
  → DecisionApplicationService.publishDecision()
  → DecisionRepository.findById()
  → CaseRepository.findById()
  → AuthorizationService.requirePermission(PUBLISH_DECISION)
  → Decision.publish()
  → optionally Sanction.create()
  → optionally SanctionObligation.create()
  → ApplicationTransactionManager.required()
      → DecisionRepository.publish()
      → CaseRepository.appendAuditEvent()
      → OutboxRepository.enqueue(audit.integration.v1)
      → OutboxRepository.enqueue(decision.lifecycle.v1 / DecisionPublished)
      → optionally OutboxRepository.enqueue(sanction.lifecycle.v1 / SanctionCreated)
```

## Appeals

### `POST /api/v1/decisions/{decisionId}/appeals`

```text
DecisionResource.createAppeal()
  → AppealApplicationService.createAppeal()
  → DecisionRepository.findById()
  → CaseRepository.findById()
  → AuthorizationService.requirePermission(CREATE_APPEAL)
  → published-decision check
  → case DECIDED check
  → AppealRepository.findActiveByDecisionId()
  → late-appeal/supervisor-override checks
  → CaseRecord.transitionTo(UNDER_APPEAL)
  → CaseWorkflowPort.startAppealWorkflow()
  → ApplicationTransactionManager.required()
      → AppealRepository.save()
      → CaseRepository.transition()
      → OutboxRepository.enqueue(audit.integration.v1)
      → OutboxRepository.enqueue(appeal.lifecycle.v1 / AppealFiled)
  → CaseWorkflowPort.correlateAppealFiled()
```

The source starts the appeal workflow before the database transaction and attempts compensation on persistence failure. Correlation failure is returned by the workflow port; callers should consult the implementation before treating it as a hard failure.

### `POST /api/v1/appeals/{appealId}/decisions`

```text
AppealResource.decideAppeal()
  → AppealApplicationService.decideAppeal()
  → AppealRepository.findById()
  → CaseRepository.findById()
  → AuthorizationService.requirePermission(DECIDE_APPEAL)
  → Appeal.decide()
  → ApplicationTransactionManager.required()
      → AppealRepository.decide()
      → CaseRepository.appendAuditEvent()
      → OutboxRepository.enqueue(audit.integration.v1)
      → OutboxRepository.enqueue(appeal.lifecycle.v1 / AppealDecided)
```

## Workflow Tasks

### `GET /api/v1/tasks`

```text
TaskResource.listTasks()
  → TaskCursorCodec.decode()
  → WorkflowTaskApplicationService.listTasks()
  → AuthorizationService.requirePermission(LIST_TASKS)
  → CaseWorkflowPort.listActiveTasks()
      → Camunda task query active().list()
      → resolve case IDs
      → CaseRepository.findByIds()
  → actor visibility/task-role filtering
  → cursor/page calculation
```

### `POST /api/v1/tasks/{taskId}/claim`

```text
TaskResource.claimTask()
  → WorkflowTaskApplicationService.claimTask()
  → AuthorizationService.requirePermission(CLAIM_TASK)
  → CaseWorkflowPort.findActiveTask()
  → CaseRepository.findById()
  → visibility check
  → CaseWorkflowPort.claimTask()
      → Camunda TaskService.claim()
      → reload active task
```

Already-claimed or completed tasks produce workflow conflict errors. No direct audit/outbox call is visible for task claim.

### `POST /api/v1/tasks/{taskId}/complete`

```text
TaskResource.completeTask()
  → WorkflowTaskApplicationService.completeTask()
  → AuthorizationService.requirePermission(COMPLETE_TASK)
  → CaseWorkflowPort.findActiveTask()
  → if absent: CaseWorkflowPort.isTaskCompleted()  [idempotent duplicate]
  → require current actor is assignee
  → prerequisite checks
  → CaseApplicationService.transitionCase()  [mapped domain tasks]
  → CaseWorkflowPort.completeTask()
      → Camunda TaskService.complete()
      → if process ended: WorkflowInstanceStore.markCompleted()
```

Mapped task transitions include triage, investigation, review, decision, appeal review, and enforcement closure. Domain transitions generate their own audit and lifecycle outbox events; task completion itself does not directly emit them.

## Workflow Reconciliation

### `GET /api/v1/workflow-reconciliation`

```text
WorkflowReconciliationResource.listIssues()
  → WorkflowReconciliationCursorCodec.decode()
  → WorkflowReconciliationApplicationService.listIssues()
  → AuthorizationService.requirePermission(RECONCILE_WORKFLOW)
  → WorkflowReconciliationQueryPort.findIssuePage()
  → case visibility authorization
  → cursor/page calculation
```

### `POST /api/v1/workflow-reconciliation/{caseId}/actions`

```text
WorkflowReconciliationResource.reconcileCase()
  → WorkflowReconciliationApplicationService.reconcileCase()
  → WorkflowReconciliationQueryPort.findCandidateByCaseId()
  → authorizeCandidate()
  → WorkflowAdministrationPort.findActiveProcessInstance()
  → detectIssue()
  → AUTO_REPAIR or TERMINATE_RUNTIME
```

Repair persistence:

```text
ApplicationTransactionManager.required()
  → WorkflowInstanceStore.upsert()
  → CaseRepository.appendAuditEvent()
  → OutboxRepository.enqueue(audit.integration.v1)
```

`TERMINATE_RUNTIME` additionally calls `WorkflowAdministrationPort.terminateActiveProcessInstance()`.

## Maintenance and Health

### `POST /api/v1/operations/sanction-obligations/recalculate-overdue`

```text
MaintenanceOperationResource.recalculateOverdueSanctionObligations()
  → MaintenanceOperationApplicationService.recalculateOverdueSanctionObligations()
  → AuthorizationService.requirePermission(RUN_MAINTENANCE_OPERATION)
  → ApplicationTransactionManager.required(REPEATABLE_READ, "recalculate-overdue-sanction-obligations")
      → MaintenanceOperationRepository.lockSanctionObligationTable()
      → MaintenanceOperationRepository.recalculateOverdueSanctionObligations()
      → MaintenanceOperationRepository.findRunById()
```

The table lock uses `NOWAIT`. No audit or outbox call is visible in this service flow.

### `GET /health`

```text
HealthResource.getHealth()
  → CompositeHealthStatusService.currentStatus()
  → DependencyHealthCheck.check() for database, Kafka, Redis, Mailpit, workflow
  → HealthResponse
```

The endpoint is public and returns `200` when healthy and `503` otherwise. It has no transaction, audit, or outbox side effect.

## Error Translation

The resource/service call chain can terminate in:

```text
domain/application exception
  → matching JAX-RS ExceptionMapper
  → RFC 7807 ErrorResponse
  → X-Correlation-Id response header
```

See [Error Handling](error-handling.md) for the mapper matrix.
