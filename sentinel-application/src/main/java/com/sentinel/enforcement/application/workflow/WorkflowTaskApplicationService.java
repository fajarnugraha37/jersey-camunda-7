package com.sentinel.enforcement.application.workflow;

import com.sentinel.enforcement.application.appeal.AppealApplicationService;
import com.sentinel.enforcement.application.casefile.CaseApplicationService;
import com.sentinel.enforcement.application.casefile.CaseRepository;
import com.sentinel.enforcement.application.casefile.SortDirection;
import com.sentinel.enforcement.application.casefile.TransitionCaseCommand;
import com.sentinel.enforcement.application.decision.DecisionRepository;
import com.sentinel.enforcement.application.recommendation.RecommendationRepository;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationDeniedException;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.CaseAuthorizationScope;
import com.sentinel.enforcement.application.security.Permission;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class WorkflowTaskApplicationService {
  private static final String CASE_RESOURCE_TYPE = "CASE";
  private static final String TRIAGE_TASK_KEY = "triageTask";
  private static final String OPTIONAL_LEGAL_ADVISORY_TASK_KEY = "optionalLegalAdvisoryTask";
  private static final String FINANCIAL_REVIEW_TASK_KEY = "financialReviewTask";
  private static final String INVESTIGATION_TASK_KEY = "investigationTask";
  private static final String LEGAL_ADVISORY_TASK_KEY = "legalAdvisoryTask";
  private static final String REVIEW_TASK_KEY = "reviewTask";
  private static final String SUPERVISOR_REVIEW_TASK_KEY = "supervisorReviewTask";
  private static final String RECOMMENDATION_REVISION_TASK_KEY = "recommendationRevisionTask";
  private static final String DECISION_TASK_KEY = "decisionTask";
  private static final String REVIEW_REGISTRY_FAILURE_TASK_KEY = "reviewRegistryFailureTask";
  private static final String REVIEW_NOTIFICATION_FAILURE_TASK_KEY =
      "reviewNotificationFailureTask";
  private static final String SUPERVISOR_OVERRIDE_REVIEW_TASK_KEY = "supervisorOverrideReviewTask";
  private static final String GLOBAL_HOLD_OVERRIDE_REVIEW_TASK_KEY = "globalHoldOverrideReviewTask";
  private static final String MONITOR_PAYMENT_OBLIGATION_TASK_KEY = "monitorPaymentObligationTask";
  private static final String MONITOR_CORRECTIVE_ACTION_TASK_KEY = "monitorCorrectiveActionTask";
  private static final String MONITOR_REPORTING_OBLIGATION_TASK_KEY =
      "monitorReportingObligationTask";
  private static final String ADDITIONAL_ENFORCEMENT_ACTION_TASK_KEY =
      "additionalEnforcementActionTask";
  private static final String APPEAL_REVIEW_TASK_KEY = "appealReviewTask";
  private static final String APPEAL_SUPERVISOR_OVERRIDE_REVIEW_TASK_KEY =
      "appealSupervisorOverrideReviewTask";

  private final AuthorizationService authorizationService;
  private final CaseRepository caseRepository;
  private final CaseApplicationService caseApplicationService;
  private final RecommendationRepository recommendationRepository;
  private final DecisionRepository decisionRepository;
  private final AppealApplicationService appealApplicationService;
  private final CaseWorkflowPort workflowPort;

  public WorkflowTaskApplicationService(
      AuthorizationService authorizationService,
      CaseRepository caseRepository,
      CaseApplicationService caseApplicationService,
      RecommendationRepository recommendationRepository,
      DecisionRepository decisionRepository,
      AppealApplicationService appealApplicationService,
      CaseWorkflowPort workflowPort) {
    this.authorizationService = authorizationService;
    this.caseRepository = caseRepository;
    this.caseApplicationService = caseApplicationService;
    this.recommendationRepository = recommendationRepository;
    this.decisionRepository = decisionRepository;
    this.appealApplicationService = appealApplicationService;
    this.workflowPort = workflowPort;
  }

  public WorkflowTaskPage listTasks(ApplicationActor actor, ListWorkflowTasksQuery query) {
    authorizationService.requirePermission(
        actor,
        Permission.LIST_TASKS,
        new AuthorizationContext(null, CASE_RESOURCE_TYPE, null, null));

    List<WorkflowTaskView> visibleTasks =
        workflowPort.listActiveTasks().stream()
            .filter(
                task ->
                    isVisibleToActor(
                        actor, task, caseRepository.findById(task.caseId()).orElse(null)))
            .filter(task -> matchesFilters(task, query))
            .sorted(buildComparator(query.sortBy(), query.sortDirection()))
            .toList();

    List<WorkflowTaskView> sliced = applyCursor(visibleTasks, query);
    boolean hasNextPage = sliced.size() > query.limit();
    List<WorkflowTaskView> trimmed =
        hasNextPage ? new ArrayList<>(sliced.subList(0, query.limit())) : new ArrayList<>(sliced);

    String nextCursorValue = null;
    String nextCursorTaskId = null;
    if (hasNextPage && !trimmed.isEmpty()) {
      WorkflowTaskView cursorTask = trimmed.get(trimmed.size() - 1);
      nextCursorValue = extractCursorValue(cursorTask, query.sortBy());
      nextCursorTaskId = cursorTask.taskId();
    }
    return new WorkflowTaskPage(trimmed, nextCursorValue, nextCursorTaskId, hasNextPage);
  }

  public WorkflowTaskView claimTask(
      ApplicationActor actor, String taskId, String correlationId, String sourceIp) {
    authorizationService.requirePermission(
        actor,
        Permission.CLAIM_TASK,
        new AuthorizationContext(null, CASE_RESOURCE_TYPE, null, null));

    WorkflowTaskView task =
        workflowPort
            .findActiveTask(taskId)
            .orElseThrow(() -> new WorkflowTaskNotFoundException(taskId));
    CaseRecord caseRecord =
        caseRepository
            .findById(task.caseId())
            .orElseThrow(
                () ->
                    new WorkflowTaskConflictException(
                        "TASK_CASE_NOT_FOUND",
                        "Workflow task " + taskId + " references a missing case."));
    if (!isVisibleToActor(actor, task, caseRecord)) {
      throw new com.sentinel.enforcement.application.security.AuthorizationDeniedException(
          "Actor cannot claim workflow task " + taskId + ".");
    }
    if (task.assigneeUserId() != null) {
      if (task.assigneeUserId().equals(actor.username())) {
        return task;
      }
      throw new WorkflowTaskConflictException(
          "TASK_ALREADY_CLAIMED",
          "Workflow task " + taskId + " is already claimed by " + task.assigneeUserId() + ".");
    }
    return workflowPort.claimTask(taskId, actor.username());
  }

  public void completeTask(
      ApplicationActor actor, String taskId, String correlationId, String sourceIp) {
    authorizationService.requirePermission(
        actor,
        Permission.COMPLETE_TASK,
        new AuthorizationContext(null, CASE_RESOURCE_TYPE, null, null));

    WorkflowTaskView activeTask = workflowPort.findActiveTask(taskId).orElse(null);
    if (activeTask == null) {
      if (workflowPort.isTaskCompleted(taskId)) {
        return;
      }
      throw new WorkflowTaskNotFoundException(taskId);
    }
    if (!actor.username().equals(activeTask.assigneeUserId())) {
      throw new WorkflowTaskConflictException(
          "TASK_NOT_CLAIMED_BY_ACTOR",
          "Workflow task " + taskId + " must be claimed by the completing actor.");
    }

    CaseRecord currentCase = caseApplicationService.getCase(actor, activeTask.caseId());
    Map<String, Object> completionVariables = completionVariablesForTask(activeTask, currentCase);
    advanceCaseForTask(actor, activeTask, currentCase, correlationId, sourceIp);
    workflowPort.completeTask(taskId, completionVariables);
    finalizeCaseAfterTaskCompletion(actor, activeTask, correlationId, sourceIp);
  }

  private void advanceCaseForTask(
      ApplicationActor actor,
      WorkflowTaskView task,
      CaseRecord currentCase,
      String correlationId,
      String sourceIp) {
    switch (task.taskDefinitionKey()) {
      case TRIAGE_TASK_KEY -> advanceFromTriage(actor, currentCase, correlationId, sourceIp);
      case INVESTIGATION_TASK_KEY ->
          advanceSingleStep(
              actor,
              currentCase,
              CaseStatus.UNDER_INVESTIGATION,
              CaseStatus.PENDING_REVIEW,
              "Workflow investigation task completed after submitted recommendation.",
              correlationId,
              sourceIp);
      case REVIEW_TASK_KEY ->
          advanceSingleStep(
              actor,
              currentCase,
              CaseStatus.PENDING_REVIEW,
              CaseStatus.PENDING_DECISION,
              "Workflow review task completed after approved recommendation.",
              correlationId,
              sourceIp);
      case DECISION_TASK_KEY ->
          advanceSingleStep(
              actor,
              currentCase,
              CaseStatus.PENDING_DECISION,
              CaseStatus.DECIDED,
              "Workflow decision task completed after published decision.",
              correlationId,
              sourceIp);
      case OPTIONAL_LEGAL_ADVISORY_TASK_KEY,
          FINANCIAL_REVIEW_TASK_KEY,
          LEGAL_ADVISORY_TASK_KEY,
          SUPERVISOR_REVIEW_TASK_KEY,
          RECOMMENDATION_REVISION_TASK_KEY,
          REVIEW_REGISTRY_FAILURE_TASK_KEY,
          REVIEW_NOTIFICATION_FAILURE_TASK_KEY,
          SUPERVISOR_OVERRIDE_REVIEW_TASK_KEY,
          GLOBAL_HOLD_OVERRIDE_REVIEW_TASK_KEY,
          MONITOR_PAYMENT_OBLIGATION_TASK_KEY,
          MONITOR_CORRECTIVE_ACTION_TASK_KEY,
          MONITOR_REPORTING_OBLIGATION_TASK_KEY,
          ADDITIONAL_ENFORCEMENT_ACTION_TASK_KEY,
          APPEAL_SUPERVISOR_OVERRIDE_REVIEW_TASK_KEY -> {
        // These tasks only advance the workflow token; domain transitions are handled elsewhere.
      }
      case APPEAL_REVIEW_TASK_KEY ->
          appealApplicationService.finalizeAppealWorkflowTask(
              actor, currentCase.id(), correlationId, sourceIp);
      default ->
          throw new WorkflowTaskConflictException(
              "TASK_TRANSITION_NOT_SUPPORTED",
              "Workflow task " + task.taskDefinitionKey() + " is not mapped to a case transition.");
    }
  }

  private Map<String, Object> completionVariablesForTask(
      WorkflowTaskView task, CaseRecord currentCase) {
    return switch (task.taskDefinitionKey()) {
      case INVESTIGATION_TASK_KEY -> Map.of("additionalEvidenceRequired", false);
      case REVIEW_TASK_KEY, SUPERVISOR_REVIEW_TASK_KEY -> Map.of("reviewRequiresRevision", false);
      case DECISION_TASK_KEY -> decisionCompletionVariables(currentCase);
      case REVIEW_NOTIFICATION_FAILURE_TASK_KEY -> Map.of("abortPublicationFinalization", false);
      case SUPERVISOR_OVERRIDE_REVIEW_TASK_KEY, GLOBAL_HOLD_OVERRIDE_REVIEW_TASK_KEY ->
          Map.of("overrideCancel", false, "overrideSuspend", false);
      case MONITOR_PAYMENT_OBLIGATION_TASK_KEY,
              MONITOR_CORRECTIVE_ACTION_TASK_KEY,
              MONITOR_REPORTING_OBLIGATION_TASK_KEY ->
          Map.of("allObligationsComplete", true, "obligationBreachDetected", false);
      case APPEAL_SUPERVISOR_OVERRIDE_REVIEW_TASK_KEY -> Map.of("appealOverrideTerminate", false);
      default -> Map.of();
    };
  }

  private Map<String, Object> decisionCompletionVariables(CaseRecord currentCase) {
    ensureTaskPrerequisite(currentCase, CaseStatus.PENDING_DECISION);
    var publishedDecision =
        decisionRepository
            .findByCaseId(currentCase.id())
            .filter(decision -> decision.publishedAt() != null)
            .orElseThrow(
                () ->
                    new WorkflowTaskConflictException(
                        "TASK_PREREQUISITE_MISSING",
                        "Decision task requires a published decision before completion."));
    return Map.of(
        "sanctionPublicationRequired", publishedDecision.violationProven(),
        "enforcementMonitoringRequired", publishedDecision.violationProven(),
        "registryAcknowledgmentFailed", false,
        "notificationResultFailed", false,
        "abortPublicationFinalization", false);
  }

  private void finalizeCaseAfterTaskCompletion(
      ApplicationActor actor, WorkflowTaskView task, String correlationId, String sourceIp) {
    if (!isEnforcementTerminalTask(task.taskDefinitionKey())) {
      return;
    }
    boolean hasRemainingTasks =
        workflowPort.listActiveTasks().stream()
            .anyMatch(active -> active.caseId().equals(task.caseId()));
    if (hasRemainingTasks) {
      return;
    }
    CaseRecord currentCase = caseApplicationService.getCase(actor, task.caseId());
    if (currentCase.status() != CaseStatus.ENFORCEMENT_IN_PROGRESS) {
      return;
    }
    caseApplicationService.transitionCase(
        actor,
        currentCase.id(),
        new TransitionCaseCommand(
            CaseStatus.CLOSED,
            currentCase.version(),
            "Workflow enforcement monitoring completed.",
            correlationId,
            sourceIp));
  }

  private void advanceFromTriage(
      ApplicationActor actor, CaseRecord currentCase, String correlationId, String sourceIp) {
    CaseRecord updated = currentCase;
    if (updated.status() == CaseStatus.CREATED) {
      updated =
          caseApplicationService.transitionCase(
              actor,
              updated.id(),
              new TransitionCaseCommand(
                  CaseStatus.UNDER_TRIAGE,
                  updated.version(),
                  "Workflow triage task completed.",
                  correlationId,
                  sourceIp));
    } else if (updated.status().ordinal() < CaseStatus.UNDER_TRIAGE.ordinal()) {
      throw new WorkflowTaskConflictException(
          "TASK_CASE_STATE_INVALID",
          "Case " + updated.caseNumber() + " is not ready for triage completion.");
    }
    if (updated.status() == CaseStatus.UNDER_TRIAGE) {
      caseApplicationService.transitionCase(
          actor,
          updated.id(),
          new TransitionCaseCommand(
              CaseStatus.UNDER_INVESTIGATION,
              updated.version(),
              "Workflow opened investigation after triage completion.",
              correlationId,
              sourceIp));
    } else if (updated.status().ordinal() < CaseStatus.UNDER_INVESTIGATION.ordinal()) {
      throw new WorkflowTaskConflictException(
          "TASK_CASE_STATE_INVALID",
          "Case " + updated.caseNumber() + " did not reach investigation-ready status.");
    }
  }

  private void advanceSingleStep(
      ApplicationActor actor,
      CaseRecord currentCase,
      CaseStatus expectedCurrent,
      CaseStatus targetStatus,
      String reason,
      String correlationId,
      String sourceIp) {
    ensureTaskPrerequisite(currentCase, expectedCurrent);
    if (currentCase.status() == expectedCurrent) {
      caseApplicationService.transitionCase(
          actor,
          currentCase.id(),
          new TransitionCaseCommand(
              targetStatus, currentCase.version(), reason, correlationId, sourceIp));
      return;
    }
    if (currentCase.status() != targetStatus) {
      throw new WorkflowTaskConflictException(
          "TASK_CASE_STATE_INVALID",
          "Case " + currentCase.caseNumber() + " is not ready for task completion.");
    }
  }

  private void ensureTaskPrerequisite(CaseRecord currentCase, CaseStatus expectedCurrent) {
    switch (expectedCurrent) {
      case UNDER_INVESTIGATION -> {
        if (!recommendationRepository.existsSubmittedForCase(currentCase.id())) {
          throw new WorkflowTaskConflictException(
              "TASK_PREREQUISITE_MISSING",
              "Investigation task requires a submitted recommendation before completion.");
        }
      }
      case PENDING_REVIEW -> {
        if (!recommendationRepository.existsApprovedForCase(currentCase.id())) {
          throw new WorkflowTaskConflictException(
              "TASK_PREREQUISITE_MISSING",
              "Review task requires an approved recommendation before completion.");
        }
      }
      case PENDING_DECISION -> {
        if (!decisionRepository.existsPublishedForCase(currentCase.id())) {
          throw new WorkflowTaskConflictException(
              "TASK_PREREQUISITE_MISSING",
              "Decision task requires a published decision before completion.");
        }
      }
      default -> {}
    }
  }

  private boolean isVisibleToActor(
      ApplicationActor actor, WorkflowTaskView task, CaseRecord caseRecord) {
    if (caseRecord == null || !actor.hasJurisdiction(task.jurisdictionCode())) {
      return false;
    }
    if (!hasTaskRole(actor, task)) {
      return false;
    }
    try {
      authorizationService.requirePermission(
          actor,
          Permission.READ_CASE,
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
    } catch (AuthorizationDeniedException exception) {
      return false;
    }
  }

  private boolean hasTaskRole(ApplicationActor actor, WorkflowTaskView task) {
    return switch (task.taskDefinitionKey()) {
      case TRIAGE_TASK_KEY -> actor.hasRole("TRIAGE_OFFICER") || actor.hasRole("SUPERVISOR");
      case OPTIONAL_LEGAL_ADVISORY_TASK_KEY,
              FINANCIAL_REVIEW_TASK_KEY,
              LEGAL_ADVISORY_TASK_KEY,
              REVIEW_TASK_KEY ->
          actor.hasRole("CASE_REVIEWER") || actor.hasRole("SUPERVISOR");
      case INVESTIGATION_TASK_KEY ->
          actor.hasRole("SUPERVISOR")
              || (actor.hasRole("INVESTIGATOR")
                  && actor.username().equals(resolveAssignedUserId(task.caseId())));
      case RECOMMENDATION_REVISION_TASK_KEY ->
          actor.hasRole("SUPERVISOR")
              || (actor.hasRole("INVESTIGATOR")
                  && actor.username().equals(resolveAssignedUserId(task.caseId())));
      case SUPERVISOR_REVIEW_TASK_KEY,
              REVIEW_REGISTRY_FAILURE_TASK_KEY,
              REVIEW_NOTIFICATION_FAILURE_TASK_KEY,
              SUPERVISOR_OVERRIDE_REVIEW_TASK_KEY,
              GLOBAL_HOLD_OVERRIDE_REVIEW_TASK_KEY,
              ADDITIONAL_ENFORCEMENT_ACTION_TASK_KEY,
              APPEAL_SUPERVISOR_OVERRIDE_REVIEW_TASK_KEY ->
          actor.hasRole("SUPERVISOR") || actor.hasRole("SYSTEM_ADMIN");
      case DECISION_TASK_KEY -> actor.hasRole("DECISION_MAKER") || actor.hasRole("SUPERVISOR");
      case MONITOR_PAYMENT_OBLIGATION_TASK_KEY,
              MONITOR_CORRECTIVE_ACTION_TASK_KEY,
              MONITOR_REPORTING_OBLIGATION_TASK_KEY ->
          actor.hasRole("CASE_REVIEWER") || actor.hasRole("SUPERVISOR");
      case APPEAL_REVIEW_TASK_KEY -> actor.hasRole("APPEAL_OFFICER") || actor.hasRole("SUPERVISOR");
      default -> false;
    };
  }

  private static boolean isEnforcementTerminalTask(String taskDefinitionKey) {
    return MONITOR_PAYMENT_OBLIGATION_TASK_KEY.equals(taskDefinitionKey)
        || MONITOR_CORRECTIVE_ACTION_TASK_KEY.equals(taskDefinitionKey)
        || MONITOR_REPORTING_OBLIGATION_TASK_KEY.equals(taskDefinitionKey)
        || ADDITIONAL_ENFORCEMENT_ACTION_TASK_KEY.equals(taskDefinitionKey);
  }

  private boolean matchesFilters(WorkflowTaskView task, ListWorkflowTasksQuery query) {
    if (query.caseId() != null && !query.caseId().equals(task.caseId())) {
      return false;
    }
    if (query.assigneeUserId() != null
        && !query.assigneeUserId().equals(normalize(task.assigneeUserId()))) {
      return false;
    }
    if (query.state() != null && query.state() != task.state()) {
      return false;
    }
    if (query.quickSearch() != null && !matchesQuickSearch(task, query.quickSearch())) {
      return false;
    }
    return query.searchField() == null
        || matchesTargetedSearch(task, query.searchField(), query.searchValue());
  }

  private boolean matchesQuickSearch(WorkflowTaskView task, String quickSearch) {
    String pattern = quickSearch.toLowerCase(Locale.ROOT);
    return contains(task.name(), pattern)
        || contains(task.taskDefinitionKey(), pattern)
        || contains(task.caseNumber(), pattern)
        || contains(task.caseTitle(), pattern)
        || contains(task.caseSummary(), pattern)
        || contains(task.assigneeUserId(), pattern)
        || contains(task.jurisdictionCode(), pattern);
  }

  private boolean matchesTargetedSearch(
      WorkflowTaskView task, WorkflowTaskSearchField searchField, String searchValue) {
    String pattern = searchValue.toLowerCase(Locale.ROOT);
    return switch (searchField) {
      case TASK_NAME -> contains(task.name(), pattern);
      case TASK_DEFINITION_KEY -> contains(task.taskDefinitionKey(), pattern);
      case CASE_NUMBER -> contains(task.caseNumber(), pattern);
      case CASE_TITLE -> contains(task.caseTitle(), pattern);
      case CASE_SUMMARY -> contains(task.caseSummary(), pattern);
      case ASSIGNEE_USER_ID -> contains(task.assigneeUserId(), pattern);
      case JURISDICTION_CODE -> contains(task.jurisdictionCode(), pattern);
    };
  }

  private Comparator<WorkflowTaskView> buildComparator(
      WorkflowTaskSortBy sortBy, SortDirection sortDirection) {
    Comparator<WorkflowTaskView> comparator =
        switch (sortBy) {
          case CREATED_AT -> Comparator.comparing(WorkflowTaskView::createdAt);
          case TASK_NAME -> Comparator.comparing(task -> normalize(task.name()));
          case CASE_NUMBER -> Comparator.comparing(task -> normalize(task.caseNumber()));
          case CASE_STATUS -> Comparator.comparing(task -> task.caseStatus().name());
        };
    comparator = comparator.thenComparing(WorkflowTaskView::taskId);
    return sortDirection == SortDirection.ASC ? comparator : comparator.reversed();
  }

  private List<WorkflowTaskView> applyCursor(
      List<WorkflowTaskView> tasks, ListWorkflowTasksQuery query) {
    if (query.cursorTaskId() == null) {
      return tasks;
    }
    return tasks.stream().filter(task -> compareToCursor(task, query) > 0).toList();
  }

  private String extractCursorValue(WorkflowTaskView task, WorkflowTaskSortBy sortBy) {
    return switch (sortBy) {
      case CREATED_AT -> task.createdAt().toString();
      case TASK_NAME -> normalize(task.name());
      case CASE_NUMBER -> normalize(task.caseNumber());
      case CASE_STATUS -> normalize(task.caseStatus().name());
    };
  }

  private String resolveAssignedUserId(UUID caseId) {
    return caseRepository.findById(caseId).map(CaseRecord::assigneeUserId).orElse(null);
  }

  private int compareToCursor(WorkflowTaskView task, ListWorkflowTasksQuery query) {
    int comparison =
        switch (query.sortBy()) {
          case CREATED_AT ->
              task.createdAt().compareTo(java.time.Instant.parse(query.cursorValue()));
          case TASK_NAME -> normalize(task.name()).compareTo(query.cursorValue());
          case CASE_NUMBER -> normalize(task.caseNumber()).compareTo(query.cursorValue());
          case CASE_STATUS -> normalize(task.caseStatus().name()).compareTo(query.cursorValue());
        };
    if (comparison == 0) {
      comparison = task.taskId().compareTo(query.cursorTaskId());
    }
    return query.sortDirection() == SortDirection.ASC ? comparison : -comparison;
  }

  private static boolean contains(String value, String pattern) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(pattern);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }
}
