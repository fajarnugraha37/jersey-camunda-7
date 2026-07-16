package com.sentinel.enforcement.application.workflow;

import com.sentinel.enforcement.application.casefile.CaseNotFoundException;
import com.sentinel.enforcement.application.casefile.CaseRepository;
import com.sentinel.enforcement.application.messaging.ApplicationTransactionManager;
import com.sentinel.enforcement.application.messaging.MessagingEventFactory;
import com.sentinel.enforcement.application.messaging.OutboxRepository;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.CaseAuthorizationScope;
import com.sentinel.enforcement.application.security.Permission;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class WorkflowReconciliationApplicationService {
  private static final String CASE_RESOURCE_TYPE = "CASE";
  private static final Set<CaseStatus> WORKFLOW_COMPLETED_CASE_STATUSES =
      Set.of(CaseStatus.DECIDED, CaseStatus.CLOSED, CaseStatus.CANCELLED);

  private final AuthorizationService authorizationService;
  private final ApplicationTransactionManager transactionManager;
  private final WorkflowReconciliationQueryPort reconciliationQueryPort;
  private final WorkflowAdministrationPort workflowAdministrationPort;
  private final WorkflowInstanceStore workflowInstanceStore;
  private final CaseRepository caseRepository;
  private final OutboxRepository outboxRepository;
  private final Clock clock;

  public WorkflowReconciliationApplicationService(
      AuthorizationService authorizationService,
      ApplicationTransactionManager transactionManager,
      WorkflowReconciliationQueryPort reconciliationQueryPort,
      WorkflowAdministrationPort workflowAdministrationPort,
      WorkflowInstanceStore workflowInstanceStore,
      CaseRepository caseRepository,
      OutboxRepository outboxRepository,
      Clock clock) {
    this.authorizationService = authorizationService;
    this.transactionManager = transactionManager;
    this.reconciliationQueryPort = reconciliationQueryPort;
    this.workflowAdministrationPort = workflowAdministrationPort;
    this.workflowInstanceStore = workflowInstanceStore;
    this.caseRepository = caseRepository;
    this.outboxRepository = outboxRepository;
    this.clock = clock;
  }

  public WorkflowReconciliationPage listIssues(
      ApplicationActor actor, ListWorkflowReconciliationIssuesQuery query) {
    authorizationService.requirePermission(
        actor,
        Permission.RECONCILE_WORKFLOW,
        new AuthorizationContext(null, CASE_RESOURCE_TYPE, null, null));

    List<WorkflowReconciliationView> collected = new ArrayList<>();
    String cursorValue = query.cursorValue();
    String cursorCaseId = query.cursorCaseId();
    boolean hasMoreSourcePages = true;

    while (collected.size() < query.limit() + 1 && hasMoreSourcePages) {
      WorkflowReconciliationPage sourcePage =
          reconciliationQueryPort.findIssuePage(
              new ListWorkflowReconciliationIssuesQuery(
                  cursorValue,
                  cursorCaseId,
                  sourceBatchLimit(query.limit()),
                  query.quickSearch(),
                  query.searchField(),
                  query.searchValue(),
                  query.issueType(),
                  query.caseStatus(),
                  query.workflowCorrelationStatus(),
                  query.sortBy(),
                  query.sortDirection()));
      for (WorkflowReconciliationView issue : sourcePage.items()) {
        if (isVisibleToActor(actor, issue.caseId())) {
          collected.add(issue);
          if (collected.size() == query.limit() + 1) {
            break;
          }
        }
      }
      hasMoreSourcePages = sourcePage.hasNextPage();
      cursorValue = sourcePage.nextCursorValue();
      cursorCaseId = sourcePage.nextCursorCaseId();
    }

    boolean hasNextPage = collected.size() > query.limit();
    List<WorkflowReconciliationView> trimmed =
        hasNextPage
            ? new ArrayList<>(collected.subList(0, query.limit()))
            : new ArrayList<>(collected);

    String nextCursorValue = null;
    String nextCursorCaseId = null;
    if (hasNextPage && !trimmed.isEmpty()) {
      WorkflowReconciliationView cursorItem = trimmed.get(trimmed.size() - 1);
      nextCursorValue = extractCursorValue(cursorItem, query.sortBy());
      nextCursorCaseId = cursorItem.caseId().toString();
    }
    return new WorkflowReconciliationPage(trimmed, nextCursorValue, nextCursorCaseId, hasNextPage);
  }

  private int sourceBatchLimit(int requestedLimit) {
    return Math.min(150, Math.max(requestedLimit * 3, requestedLimit + 1));
  }

  public WorkflowReconciliationActionResult reconcileCase(
      ApplicationActor actor, UUID caseId, WorkflowReconciliationActionCommand command) {
    WorkflowReconciliationCandidate candidate =
        reconciliationQueryPort
            .findCandidateByCaseId(caseId)
            .orElseThrow(() -> new CaseNotFoundException(caseId));
    authorizeCandidate(actor, candidate);

    WorkflowProcessSnapshot runtimeSnapshot =
        workflowAdministrationPort.findActiveProcessInstance(caseId).orElse(null);
    WorkflowReconciliationView currentIssue =
        detectIssue(candidate, runtimeSnapshot)
            .orElseThrow(
                () ->
                    new WorkflowReconciliationConflictException(
                        "WORKFLOW_ALREADY_RECONCILED",
                        "Case "
                            + candidate.caseNumber()
                            + " does not currently have a workflow mismatch."));
    Instant now = clock.instant();

    return switch (command.action()) {
      case AUTO_REPAIR -> autoRepair(actor, candidate, currentIssue, runtimeSnapshot, command, now);
      case TERMINATE_RUNTIME ->
          terminateRuntime(actor, candidate, currentIssue, runtimeSnapshot, command, now);
    };
  }

  private WorkflowReconciliationActionResult autoRepair(
      ApplicationActor actor,
      WorkflowReconciliationCandidate candidate,
      WorkflowReconciliationView currentIssue,
      WorkflowProcessSnapshot runtimeSnapshot,
      WorkflowReconciliationActionCommand command,
      Instant now) {
    if (runtimeSnapshot != null && shouldWorkflowBeActive(candidate.caseStatus())) {
      WorkflowInstanceCorrelation activeCorrelation =
          new WorkflowInstanceCorrelation(
              candidate.caseId(),
              runtimeSnapshot.processInstanceId(),
              runtimeSnapshot.processDefinitionId(),
              runtimeSnapshot.processDefinitionVersion(),
              runtimeSnapshot.businessKey(),
              "ACTIVE");
      persistRepair(actor, candidate, command, now, "AUTO_REPAIR", "ACTIVE", activeCorrelation);
      return new WorkflowReconciliationActionResult(
          candidate.caseId(),
          command.action(),
          WorkflowReconciliationActionResultStatus.REPAIRED,
          currentIssue.issueType(),
          "Workflow correlation synchronized from active runtime instance.",
          "ACTIVE",
          runtimeSnapshot.processInstanceId());
    }

    if (!shouldWorkflowBeActive(candidate.caseStatus())) {
      WorkflowHistoricProcessSnapshot historicSnapshot =
          workflowAdministrationPort
              .findLatestFinishedProcessInstance(candidate.caseId())
              .orElse(null);
      if (historicSnapshot != null) {
        String repairedStatus =
            candidate.caseStatus() == CaseStatus.CANCELLED ? "CANCELLED" : "COMPLETED";
        persistRepair(
            actor,
            candidate,
            command,
            now,
            "AUTO_REPAIR",
            repairedStatus,
            new WorkflowInstanceCorrelation(
                candidate.caseId(),
                historicSnapshot.processInstanceId(),
                historicSnapshot.processDefinitionId(),
                historicSnapshot.processDefinitionVersion(),
                historicSnapshot.businessKey(),
                repairedStatus));
        return new WorkflowReconciliationActionResult(
            candidate.caseId(),
            command.action(),
            WorkflowReconciliationActionResultStatus.REPAIRED,
            currentIssue.issueType(),
            "Workflow correlation synchronized from finished workflow history.",
            repairedStatus,
            historicSnapshot.processInstanceId());
      }
    }

    throw new WorkflowReconciliationConflictException(
        "WORKFLOW_RECONCILIATION_MANUAL_ACTION_REQUIRED",
        "Case "
            + candidate.caseNumber()
            + " requires manual workflow investigation before it can be repaired.");
  }

  private WorkflowReconciliationActionResult terminateRuntime(
      ApplicationActor actor,
      WorkflowReconciliationCandidate candidate,
      WorkflowReconciliationView currentIssue,
      WorkflowProcessSnapshot runtimeSnapshot,
      WorkflowReconciliationActionCommand command,
      Instant now) {
    if (runtimeSnapshot == null) {
      throw new WorkflowReconciliationConflictException(
          "WORKFLOW_RUNTIME_NOT_ACTIVE",
          "Case "
              + candidate.caseNumber()
              + " does not have an active runtime instance to terminate.");
    }
    if (shouldWorkflowBeActive(candidate.caseStatus())) {
      throw new WorkflowReconciliationConflictException(
          "WORKFLOW_TERMINATION_NOT_ALLOWED",
          "Case " + candidate.caseNumber() + " still expects an active workflow instance.");
    }

    workflowAdministrationPort.terminateActiveProcessInstance(candidate.caseId(), command.reason());
    String repairedStatus =
        candidate.caseStatus() == CaseStatus.CANCELLED ? "CANCELLED" : "COMPLETED";
    persistRepair(
        actor,
        candidate,
        command,
        now,
        "TERMINATE_RUNTIME",
        repairedStatus,
        new WorkflowInstanceCorrelation(
            candidate.caseId(),
            runtimeSnapshot.processInstanceId(),
            runtimeSnapshot.processDefinitionId(),
            runtimeSnapshot.processDefinitionVersion(),
            runtimeSnapshot.businessKey(),
            repairedStatus));
    return new WorkflowReconciliationActionResult(
        candidate.caseId(),
        command.action(),
        WorkflowReconciliationActionResultStatus.REPAIRED,
        currentIssue.issueType(),
        "Active runtime instance terminated and workflow correlation updated.",
        repairedStatus,
        runtimeSnapshot.processInstanceId());
  }

  private Optional<WorkflowReconciliationView> detectIssue(
      WorkflowReconciliationCandidate candidate, WorkflowProcessSnapshot runtimeSnapshot) {
    WorkflowInstanceCorrelation correlation = candidate.workflowInstanceCorrelation();
    boolean workflowShouldBeActive = shouldWorkflowBeActive(candidate.caseStatus());

    if (runtimeSnapshot != null) {
      if (!workflowShouldBeActive) {
        return Optional.of(
            newIssue(
                candidate,
                WorkflowReconciliationIssueType.TERMINAL_CASE_RUNTIME_ACTIVE,
                "Case reached a post-decision state but still has an active workflow runtime instance.",
                correlation,
                runtimeSnapshot,
                List.of(WorkflowReconciliationAction.TERMINATE_RUNTIME)));
      }
      if (correlation == null) {
        return Optional.of(
            newIssue(
                candidate,
                WorkflowReconciliationIssueType.ACTIVE_RUNTIME_MISSING_CORRELATION,
                "Active workflow runtime exists but workflow correlation row is missing.",
                null,
                runtimeSnapshot,
                List.of(WorkflowReconciliationAction.AUTO_REPAIR)));
      }
      if (!"ACTIVE".equals(correlation.status())
          || !runtimeSnapshot.processInstanceId().equals(correlation.processInstanceId())) {
        return Optional.of(
            newIssue(
                candidate,
                WorkflowReconciliationIssueType.ACTIVE_RUNTIME_CORRELATION_MISMATCH,
                "Active workflow runtime and stored workflow correlation disagree on status or process instance.",
                correlation,
                runtimeSnapshot,
                List.of(WorkflowReconciliationAction.AUTO_REPAIR)));
      }
      return Optional.empty();
    }

    if (workflowShouldBeActive) {
      if (correlation == null) {
        return Optional.of(
            newIssue(
                candidate,
                WorkflowReconciliationIssueType.ACTIVE_CASE_WORKFLOW_NOT_RUNNING,
                "Case is still in an active workflow state but no runtime instance or workflow correlation is present.",
                null,
                null,
                List.of()));
      }
      if (!"ACTIVE".equals(correlation.status())) {
        return Optional.of(
            newIssue(
                candidate,
                WorkflowReconciliationIssueType.ACTIVE_CASE_CORRELATION_TERMINAL,
                "Case is still in an active workflow state but the stored workflow correlation is terminal.",
                correlation,
                null,
                List.of()));
      }
      return Optional.of(
          newIssue(
              candidate,
              WorkflowReconciliationIssueType.ACTIVE_CASE_WORKFLOW_NOT_RUNNING,
              "Case is still in an active workflow state but its active runtime instance is missing.",
              correlation,
              null,
              List.of()));
    }

    if (correlation == null) {
      return Optional.of(
          newIssue(
              candidate,
              WorkflowReconciliationIssueType.TERMINAL_CASE_MISSING_CORRELATION,
              "Case reached a post-decision state but the workflow correlation row is missing.",
              null,
              null,
              List.of(WorkflowReconciliationAction.AUTO_REPAIR)));
    }
    if ("ACTIVE".equals(correlation.status())) {
      return Optional.of(
          newIssue(
              candidate,
              WorkflowReconciliationIssueType.TERMINAL_CASE_CORRELATION_ACTIVE,
              "Case reached a post-decision state but the workflow correlation still reports ACTIVE.",
              correlation,
              null,
              List.of(WorkflowReconciliationAction.AUTO_REPAIR)));
    }
    return Optional.empty();
  }

  private WorkflowReconciliationView newIssue(
      WorkflowReconciliationCandidate candidate,
      WorkflowReconciliationIssueType issueType,
      String detail,
      WorkflowInstanceCorrelation correlation,
      WorkflowProcessSnapshot runtimeSnapshot,
      List<WorkflowReconciliationAction> availableActions) {
    return new WorkflowReconciliationView(
        candidate.caseId(),
        candidate.caseNumber(),
        candidate.caseTitle(),
        candidate.caseStatus(),
        candidate.jurisdictionCode(),
        candidate.assigneeUserId(),
        candidate.caseUpdatedAt(),
        issueType,
        detail,
        correlation == null ? null : correlation.status(),
        correlation == null ? null : correlation.processInstanceId(),
        runtimeSnapshot == null ? null : runtimeSnapshot.processInstanceId(),
        availableActions);
  }

  private String extractCursorValue(
      WorkflowReconciliationView issue, WorkflowReconciliationSortBy sortBy) {
    return switch (sortBy) {
      case CASE_UPDATED_AT -> issue.caseUpdatedAt().toString();
      case CASE_NUMBER -> normalize(issue.caseNumber());
      case CASE_STATUS -> normalize(issue.caseStatus().name());
      case ISSUE_TYPE -> normalize(issue.issueType().name());
      case CORRELATION_STATUS -> normalize(issue.workflowCorrelationStatus());
    };
  }

  private void authorizeCandidate(
      ApplicationActor actor, WorkflowReconciliationCandidate candidate) {
    CaseRecord caseRecord =
        caseRepository
            .findById(candidate.caseId())
            .orElseThrow(() -> new CaseNotFoundException(candidate.caseId()));
    authorizationService.requirePermission(
        actor,
        Permission.RECONCILE_WORKFLOW,
        new AuthorizationContext(
            caseRecord.jurisdictionCode(),
            CASE_RESOURCE_TYPE,
            caseRecord.id().toString(),
            caseRecord.id(),
            caseRecord.assigneeUserId(),
            caseRecord.assignedUnitId(),
            caseRecord.classification(),
            caseRecord.createdBy(),
            CaseAuthorizationScope.RESTRICTED_TO_ASSIGNED_UNITS_WHEN_PRESENT));
  }

  private boolean isVisibleToActor(ApplicationActor actor, UUID caseId) {
    CaseRecord caseRecord = caseRepository.findById(caseId).orElse(null);
    if (caseRecord == null) {
      return false;
    }
    try {
      authorizationService.requirePermission(
          actor,
          Permission.RECONCILE_WORKFLOW,
          new AuthorizationContext(
              caseRecord.jurisdictionCode(),
              CASE_RESOURCE_TYPE,
              caseRecord.id().toString(),
              caseRecord.id(),
              caseRecord.assigneeUserId(),
              caseRecord.assignedUnitId(),
              caseRecord.classification(),
              caseRecord.createdBy(),
              CaseAuthorizationScope.RESTRICTED_TO_ASSIGNED_UNITS_WHEN_PRESENT));
      return true;
    } catch (com.sentinel.enforcement.application.security.AuthorizationDeniedException exception) {
      return false;
    }
  }

  private boolean shouldWorkflowBeActive(CaseStatus caseStatus) {
    return !WORKFLOW_COMPLETED_CASE_STATUSES.contains(caseStatus);
  }

  private void appendAuditEvent(
      ApplicationActor actor,
      WorkflowReconciliationCandidate candidate,
      WorkflowReconciliationActionCommand command,
      Instant now,
      String action,
      String resultingStatus) {
    AuditEvent auditEvent =
        new AuditEvent(
            UUID.randomUUID(),
            "WorkflowReconciliationPerformed",
            "USER",
            actor.username(),
            String.join(",", actor.roles().stream().sorted().toList()),
            action,
            CASE_RESOURCE_TYPE,
            candidate.caseId().toString(),
            candidate.caseId(),
            now,
            command.correlationId(),
            command.sourceIp(),
            "SUCCESS",
            command.reason(),
            candidate.workflowInstanceCorrelation() == null
                ? "workflowCorrelation=NONE"
                : "workflowCorrelation="
                    + candidate.workflowInstanceCorrelation().status()
                    + ":"
                    + candidate.workflowInstanceCorrelation().processInstanceId(),
            "workflowCorrelation=" + resultingStatus,
            "issueType=" + candidate.caseStatus() + ";action=" + action);
    caseRepository.appendAuditEvent(auditEvent);
    outboxRepository.enqueue(MessagingEventFactory.auditIntegrated(auditEvent, now));
  }

  private void persistRepair(
      ApplicationActor actor,
      WorkflowReconciliationCandidate candidate,
      WorkflowReconciliationActionCommand command,
      Instant now,
      String action,
      String resultingStatus,
      WorkflowInstanceCorrelation correlation) {
    transactionManager.required(
        () -> {
          workflowInstanceStore.upsert(correlation, now);
          appendAuditEvent(actor, candidate, command, now, action, resultingStatus);
          return null;
        });
  }

  private static boolean contains(String value, String pattern) {
    return normalize(value).contains(pattern);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }
}
