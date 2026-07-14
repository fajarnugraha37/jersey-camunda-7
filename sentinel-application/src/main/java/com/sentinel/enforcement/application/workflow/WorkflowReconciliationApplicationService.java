package com.sentinel.enforcement.application.workflow;

import com.sentinel.enforcement.application.casefile.CaseNotFoundException;
import com.sentinel.enforcement.application.casefile.CaseRepository;
import com.sentinel.enforcement.application.casefile.SortDirection;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.Permission;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class WorkflowReconciliationApplicationService {
  private static final String CASE_RESOURCE_TYPE = "CASE";
  private static final Set<CaseStatus> WORKFLOW_COMPLETED_CASE_STATUSES =
      Set.of(CaseStatus.DECIDED, CaseStatus.CLOSED, CaseStatus.CANCELLED);

  private final AuthorizationService authorizationService;
  private final WorkflowReconciliationQueryPort reconciliationQueryPort;
  private final WorkflowAdministrationPort workflowAdministrationPort;
  private final WorkflowInstanceStore workflowInstanceStore;
  private final CaseRepository caseRepository;
  private final Clock clock;

  public WorkflowReconciliationApplicationService(
      AuthorizationService authorizationService,
      WorkflowReconciliationQueryPort reconciliationQueryPort,
      WorkflowAdministrationPort workflowAdministrationPort,
      WorkflowInstanceStore workflowInstanceStore,
      CaseRepository caseRepository,
      Clock clock) {
    this.authorizationService = authorizationService;
    this.reconciliationQueryPort = reconciliationQueryPort;
    this.workflowAdministrationPort = workflowAdministrationPort;
    this.workflowInstanceStore = workflowInstanceStore;
    this.caseRepository = caseRepository;
    this.clock = clock;
  }

  public WorkflowReconciliationPage listIssues(
      ApplicationActor actor, ListWorkflowReconciliationIssuesQuery query) {
    authorizationService.requirePermission(
        actor,
        Permission.RECONCILE_WORKFLOW,
        new AuthorizationContext(null, CASE_RESOURCE_TYPE, null, null));

    Map<UUID, WorkflowProcessSnapshot> runtimeByCaseId = activeRuntimeByCaseId();
    List<WorkflowReconciliationView> issues =
        reconciliationQueryPort.findCandidates().stream()
            .filter(candidate -> actor.hasJurisdiction(candidate.jurisdictionCode()))
            .map(candidate -> detectIssue(candidate, runtimeByCaseId.get(candidate.caseId())))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(issue -> matchesFilters(issue, query))
            .sorted(buildComparator(query.sortBy(), query.sortDirection()))
            .toList();

    List<WorkflowReconciliationView> sliced = applyCursor(issues, query);
    boolean hasNextPage = sliced.size() > query.limit();
    List<WorkflowReconciliationView> trimmed =
        hasNextPage ? new ArrayList<>(sliced.subList(0, query.limit())) : new ArrayList<>(sliced);

    String nextCursorValue = null;
    String nextCursorCaseId = null;
    if (hasNextPage && !trimmed.isEmpty()) {
      WorkflowReconciliationView cursorItem = trimmed.get(trimmed.size() - 1);
      nextCursorValue = extractCursorValue(cursorItem, query.sortBy());
      nextCursorCaseId = cursorItem.caseId().toString();
    }
    return new WorkflowReconciliationPage(trimmed, nextCursorValue, nextCursorCaseId, hasNextPage);
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
      workflowInstanceStore.upsert(activeCorrelation, now);
      appendAuditEvent(actor, candidate, command, now, "AUTO_REPAIR", "ACTIVE");
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
        workflowInstanceStore.upsert(
            new WorkflowInstanceCorrelation(
                candidate.caseId(),
                historicSnapshot.processInstanceId(),
                historicSnapshot.processDefinitionId(),
                historicSnapshot.processDefinitionVersion(),
                historicSnapshot.businessKey(),
                repairedStatus),
            now);
        appendAuditEvent(actor, candidate, command, now, "AUTO_REPAIR", repairedStatus);
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
    workflowInstanceStore.upsert(
        new WorkflowInstanceCorrelation(
            candidate.caseId(),
            runtimeSnapshot.processInstanceId(),
            runtimeSnapshot.processDefinitionId(),
            runtimeSnapshot.processDefinitionVersion(),
            runtimeSnapshot.businessKey(),
            repairedStatus),
        now);
    appendAuditEvent(actor, candidate, command, now, "TERMINATE_RUNTIME", repairedStatus);
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

  private boolean matchesFilters(
      WorkflowReconciliationView issue, ListWorkflowReconciliationIssuesQuery query) {
    if (query.issueType() != null && query.issueType() != issue.issueType()) {
      return false;
    }
    if (query.caseStatus() != null && query.caseStatus() != issue.caseStatus()) {
      return false;
    }
    if (query.workflowCorrelationStatus() != null
        && !query
            .workflowCorrelationStatus()
            .equals(normalize(issue.workflowCorrelationStatus()))) {
      return false;
    }
    if (query.quickSearch() != null && !matchesQuickSearch(issue, query.quickSearch())) {
      return false;
    }
    return query.searchField() == null
        || matchesTargetedSearch(issue, query.searchField(), query.searchValue());
  }

  private boolean matchesQuickSearch(WorkflowReconciliationView issue, String quickSearch) {
    String pattern = normalize(quickSearch);
    return contains(issue.caseNumber(), pattern)
        || contains(issue.caseTitle(), pattern)
        || contains(issue.jurisdictionCode(), pattern)
        || contains(issue.issueType().name(), pattern)
        || contains(issue.correlationProcessInstanceId(), pattern)
        || contains(issue.runtimeProcessInstanceId(), pattern);
  }

  private boolean matchesTargetedSearch(
      WorkflowReconciliationView issue,
      WorkflowReconciliationSearchField searchField,
      String searchValue) {
    String pattern = normalize(searchValue);
    return switch (searchField) {
      case CASE_NUMBER -> contains(issue.caseNumber(), pattern);
      case CASE_TITLE -> contains(issue.caseTitle(), pattern);
      case ISSUE_TYPE -> contains(issue.issueType().name(), pattern);
      case PROCESS_INSTANCE_ID ->
          contains(issue.correlationProcessInstanceId(), pattern)
              || contains(issue.runtimeProcessInstanceId(), pattern);
      case JURISDICTION_CODE -> contains(issue.jurisdictionCode(), pattern);
    };
  }

  private Comparator<WorkflowReconciliationView> buildComparator(
      WorkflowReconciliationSortBy sortBy, SortDirection sortDirection) {
    Comparator<WorkflowReconciliationView> comparator =
        switch (sortBy) {
          case CASE_UPDATED_AT -> Comparator.comparing(WorkflowReconciliationView::caseUpdatedAt);
          case CASE_NUMBER -> Comparator.comparing(item -> normalize(item.caseNumber()));
          case CASE_STATUS -> Comparator.comparing(item -> normalize(item.caseStatus().name()));
          case ISSUE_TYPE -> Comparator.comparing(item -> normalize(item.issueType().name()));
          case CORRELATION_STATUS ->
              Comparator.comparing(item -> normalize(item.workflowCorrelationStatus()));
        };
    comparator = comparator.thenComparing(item -> item.caseId().toString());
    return sortDirection == SortDirection.ASC ? comparator : comparator.reversed();
  }

  private List<WorkflowReconciliationView> applyCursor(
      List<WorkflowReconciliationView> issues, ListWorkflowReconciliationIssuesQuery query) {
    if (query.cursorCaseId() == null) {
      return issues;
    }
    return issues.stream().filter(issue -> compareToCursor(issue, query) > 0).toList();
  }

  private int compareToCursor(
      WorkflowReconciliationView issue, ListWorkflowReconciliationIssuesQuery query) {
    int comparison =
        switch (query.sortBy()) {
          case CASE_UPDATED_AT ->
              issue.caseUpdatedAt().compareTo(Instant.parse(query.cursorValue()));
          case CASE_NUMBER -> normalize(issue.caseNumber()).compareTo(query.cursorValue());
          case CASE_STATUS -> normalize(issue.caseStatus().name()).compareTo(query.cursorValue());
          case ISSUE_TYPE -> normalize(issue.issueType().name()).compareTo(query.cursorValue());
          case CORRELATION_STATUS ->
              normalize(issue.workflowCorrelationStatus()).compareTo(query.cursorValue());
        };
    if (comparison == 0) {
      comparison = issue.caseId().toString().compareTo(query.cursorCaseId());
    }
    return query.sortDirection() == SortDirection.ASC ? comparison : -comparison;
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

  private Map<UUID, WorkflowProcessSnapshot> activeRuntimeByCaseId() {
    Map<UUID, WorkflowProcessSnapshot> result = new HashMap<>();
    for (WorkflowProcessSnapshot snapshot :
        workflowAdministrationPort.listActiveProcessInstances()) {
      result.put(snapshot.caseId(), snapshot);
    }
    return result;
  }

  private void authorizeCandidate(
      ApplicationActor actor, WorkflowReconciliationCandidate candidate) {
    authorizationService.requirePermission(
        actor,
        Permission.RECONCILE_WORKFLOW,
        new AuthorizationContext(
            candidate.jurisdictionCode(),
            CASE_RESOURCE_TYPE,
            candidate.caseId().toString(),
            candidate.assigneeUserId()));
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
    caseRepository.appendAuditEvent(
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
            "issueType=" + candidate.caseStatus() + ";action=" + action));
  }

  private static boolean contains(String value, String pattern) {
    return normalize(value).contains(pattern);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }
}
