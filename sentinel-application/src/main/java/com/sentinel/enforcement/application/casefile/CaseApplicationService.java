package com.sentinel.enforcement.application.casefile;

import com.sentinel.enforcement.application.messaging.ApplicationTransactionManager;
import com.sentinel.enforcement.application.messaging.MessagingEventFactory;
import com.sentinel.enforcement.application.messaging.OutboxRepository;
import com.sentinel.enforcement.application.report.ReportNotFoundException;
import com.sentinel.enforcement.application.report.ReportRepository;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.Permission;
import com.sentinel.enforcement.application.workflow.CaseWorkflowPort;
import com.sentinel.enforcement.application.workflow.StartedWorkflowInstance;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseActionContext;
import com.sentinel.enforcement.domain.casefile.CaseAssignment;
import com.sentinel.enforcement.domain.casefile.CaseConflictException;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import com.sentinel.enforcement.domain.casefile.CaseStatusHistoryEntry;
import com.sentinel.enforcement.domain.report.Report;
import com.sentinel.enforcement.domain.report.ReportStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CaseApplicationService {
  private static final String CASE_RESOURCE_TYPE = "CASE";

  private final AuthorizationService authorizationService;
  private final ApplicationTransactionManager transactionManager;
  private final CaseRepository caseRepository;
  private final ReportRepository reportRepository;
  private final OutboxRepository outboxRepository;
  private final CaseProgressionGuard progressionGuard;
  private final CaseWorkflowPort workflowPort;
  private final Duration investigationEscalationDuration;
  private final Clock clock;

  public CaseApplicationService(
      AuthorizationService authorizationService,
      ApplicationTransactionManager transactionManager,
      CaseRepository caseRepository,
      ReportRepository reportRepository,
      OutboxRepository outboxRepository,
      CaseProgressionGuard progressionGuard,
      CaseWorkflowPort workflowPort,
      Duration investigationEscalationDuration,
      Clock clock) {
    this.authorizationService = authorizationService;
    this.transactionManager = transactionManager;
    this.caseRepository = caseRepository;
    this.reportRepository = reportRepository;
    this.outboxRepository = outboxRepository;
    this.progressionGuard = progressionGuard;
    this.workflowPort = workflowPort;
    this.investigationEscalationDuration = investigationEscalationDuration;
    this.clock = clock;
  }

  public CaseRecord createCase(ApplicationActor actor, CreateCaseCommand command) {
    Report report =
        reportRepository
            .findById(command.reportId())
            .orElseThrow(() -> new ReportNotFoundException(command.reportId()));
    authorizationService.requirePermission(
        actor,
        Permission.CREATE_CASE,
        new AuthorizationContext(report.jurisdictionCode(), CASE_RESOURCE_TYPE, null, null));
    if (report.status() != ReportStatus.TRIAGED) {
      throw new CaseConflictException(
          "REPORT_NOT_TRIAGED",
          "Report " + report.id() + " must be triaged before a case can be created.");
    }

    Instant now = clock.instant();
    String caseNumber =
        caseRepository.nextCaseNumber(
            report.jurisdictionCode(), now.atOffset(ZoneOffset.UTC).getYear());
    UUID caseId = UUID.randomUUID();
    CaseRecord caseRecord =
        CaseRecord.create(
            caseId,
            caseNumber,
            report.id(),
            command.title(),
            command.summary(),
            report.jurisdictionCode(),
            now,
            actor.username());
    StartedWorkflowInstance startedWorkflow =
        workflowPort.startCaseWorkflow(
            caseRecord.id(),
            caseRecord.jurisdictionCode(),
            caseRecord.caseNumber(),
            caseRecord.title(),
            investigationEscalationDuration,
            actor.username());
    CaseStatusHistoryEntry historyEntry =
        new CaseStatusHistoryEntry(
            UUID.randomUUID(),
            caseRecord.id(),
            null,
            CaseStatus.CREATED,
            "Case created from triaged report.",
            now,
            actor.username(),
            now,
            actor.username());
    AuditEvent auditEvent =
        newAuditEvent(
            actor,
            caseRecord.id(),
            "CaseCreated",
            "CASE_CREATED",
            "SUCCESS",
            "Case created from triaged report.",
            null,
            caseRecord.auditSummary(),
            "reportId=" + report.id(),
            command.correlationId(),
            command.sourceIp(),
            now);
    try {
      transactionManager.required(
          () -> {
            caseRepository.save(caseRecord, historyEntry, auditEvent);
            outboxRepository.enqueue(
                MessagingEventFactory.caseCreated(actor, caseRecord, command.correlationId(), now));
            return null;
          });
    } catch (RuntimeException exception) {
      try {
        workflowPort.cancelCaseWorkflow(
            startedWorkflow.caseId(), "Compensating cancellation after case persistence failure.");
      } catch (RuntimeException cancellationException) {
        exception.addSuppressed(cancellationException);
      }
      throw exception;
    }
    return caseRecord;
  }

  public CaseRecord getCase(ApplicationActor actor, UUID caseId) {
    CaseRecord caseRecord = getRequiredCase(caseId);
    authorizationService.requirePermission(
        actor,
        Permission.READ_CASE,
        new AuthorizationContext(
            caseRecord.jurisdictionCode(),
            CASE_RESOURCE_TYPE,
            caseRecord.id().toString(),
            caseRecord.assigneeUserId()));
    return caseRecord;
  }

  public CasePage listCases(ApplicationActor actor, ListCasesQuery query) {
    authorizationService.requirePermission(
        actor,
        Permission.LIST_CASES,
        new AuthorizationContext(null, CASE_RESOURCE_TYPE, null, null));

    String restrictedAssignee = restrictedAssignee(actor);
    List<CaseRecord> loaded =
        caseRepository.findPage(
            new CasePageRequest(
                actor.jurisdictions(),
                restrictedAssignee,
                query.assigneeUserId(),
                query.cursorValue(),
                query.cursorId(),
                query.quickSearch(),
                query.searchField(),
                query.searchValue(),
                query.status(),
                query.assignedUnitId(),
                query.createdBy(),
                query.reportId(),
                query.sortBy(),
                query.sortDirection(),
                query.limit() + 1));
    boolean hasNextPage = loaded.size() > query.limit();
    List<CaseRecord> trimmed =
        hasNextPage ? new ArrayList<>(loaded.subList(0, query.limit())) : new ArrayList<>(loaded);

    String nextCursorValue = null;
    UUID nextCursorId = null;
    if (hasNextPage && !trimmed.isEmpty()) {
      CaseRecord nextCursorRecord = trimmed.get(trimmed.size() - 1);
      nextCursorValue = extractCursorValue(nextCursorRecord, query.sortBy());
      nextCursorId = nextCursorRecord.id();
    }
    return new CasePage(trimmed, nextCursorValue, nextCursorId, hasNextPage);
  }

  public CaseRecord assignCase(ApplicationActor actor, UUID caseId, AssignCaseCommand command) {
    CaseRecord current = getRequiredCase(caseId);
    authorizationService.requirePermission(
        actor,
        Permission.ASSIGN_CASE,
        new AuthorizationContext(
            current.jurisdictionCode(), CASE_RESOURCE_TYPE, current.id().toString(), null));

    Instant now = clock.instant();
    CaseActionContext context =
        new CaseActionContext(
            actor.username(), actor.roles(), command.expectedVersion(), command.reason(), now);
    CaseRecord updated =
        current.assignTo(command.assignedUnitId(), command.assigneeUserId(), context);
    CaseAssignment assignment =
        new CaseAssignment(
            UUID.randomUUID(),
            updated.id(),
            updated.assignedUnitId(),
            updated.assigneeUserId(),
            command.reason(),
            now,
            actor.username(),
            now,
            actor.username(),
            now,
            actor.username(),
            0L);
    AuditEvent auditEvent =
        newAuditEvent(
            actor,
            updated.id(),
            "CaseAssigned",
            "CASE_ASSIGNED",
            "SUCCESS",
            command.reason(),
            current.auditSummary(),
            updated.auditSummary(),
            "assignedUnitId="
                + updated.assignedUnitId()
                + ";assigneeUserId="
                + updated.assigneeUserId(),
            command.correlationId(),
            command.sourceIp(),
            now);
    transactionManager.required(
        () -> {
          caseRepository.assign(updated, assignment, auditEvent);
          outboxRepository.enqueue(
              MessagingEventFactory.caseAssigned(
                  actor, updated, command.reason(), command.correlationId(), now));
          return null;
        });
    return updated;
  }

  public CaseRecord transitionCase(
      ApplicationActor actor, UUID caseId, TransitionCaseCommand command) {
    CaseRecord current = getRequiredCase(caseId);
    authorizationService.requirePermission(
        actor,
        Permission.TRANSITION_CASE,
        new AuthorizationContext(
            current.jurisdictionCode(),
            CASE_RESOURCE_TYPE,
            current.id().toString(),
            current.assigneeUserId()));

    Instant now = clock.instant();
    CaseActionContext context =
        new CaseActionContext(
            actor.username(), actor.roles(), command.expectedVersion(), command.reason(), now);
    progressionGuard.requireTargetStatePrerequisites(caseId, command.targetStatus());
    CaseRecord updated = current.transitionTo(command.targetStatus(), context);
    CaseStatusHistoryEntry historyEntry =
        new CaseStatusHistoryEntry(
            UUID.randomUUID(),
            updated.id(),
            current.status(),
            updated.status(),
            command.reason(),
            now,
            actor.username(),
            now,
            actor.username());
    AuditEvent auditEvent =
        newAuditEvent(
            actor,
            updated.id(),
            "CaseTransitioned",
            "CASE_TRANSITIONED",
            "SUCCESS",
            command.reason(),
            current.auditSummary(),
            updated.auditSummary(),
            "fromStatus=" + current.status() + ";toStatus=" + updated.status(),
            command.correlationId(),
            command.sourceIp(),
            now);
    transactionManager.required(
        () -> {
          caseRepository.transition(updated, historyEntry, auditEvent);
          outboxRepository.enqueue(
              MessagingEventFactory.caseTransitioned(
                  actor,
                  updated,
                  current.status(),
                  command.reason(),
                  command.correlationId(),
                  now));
          return null;
        });
    return updated;
  }

  public AuditEventPage getCaseAuditEvents(
      ApplicationActor actor, UUID caseId, ListCaseAuditEventsQuery query) {
    CaseRecord caseRecord = getRequiredCase(caseId);
    authorizationService.requirePermission(
        actor,
        Permission.READ_CASE_AUDIT,
        new AuthorizationContext(
            caseRecord.jurisdictionCode(),
            CASE_RESOURCE_TYPE,
            caseRecord.id().toString(),
            caseRecord.assigneeUserId()));
    List<AuditEvent> loaded =
        caseRepository.findAuditEventsPage(
            new AuditEventPageRequest(
                caseId,
                query.cursorValue(),
                query.cursorId(),
                query.quickSearch(),
                query.searchField(),
                query.searchValue(),
                query.actorId(),
                query.eventType(),
                query.action(),
                query.result(),
                query.sortBy(),
                query.sortDirection(),
                query.limit() + 1));
    boolean hasNextPage = loaded.size() > query.limit();
    List<AuditEvent> trimmed =
        hasNextPage ? new ArrayList<>(loaded.subList(0, query.limit())) : new ArrayList<>(loaded);

    String nextCursorValue = null;
    UUID nextCursorId = null;
    if (hasNextPage && !trimmed.isEmpty()) {
      AuditEvent nextCursorEvent = trimmed.get(trimmed.size() - 1);
      nextCursorValue = extractAuditCursorValue(nextCursorEvent, query.sortBy());
      nextCursorId = nextCursorEvent.eventId();
    }
    return new AuditEventPage(trimmed, nextCursorValue, nextCursorId, hasNextPage);
  }

  private CaseRecord getRequiredCase(UUID caseId) {
    return caseRepository.findById(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
  }

  private String restrictedAssignee(ApplicationActor actor) {
    if (!actor.hasRole("INVESTIGATOR")) {
      return null;
    }
    if (hasAnyRole(
        actor.roles(),
        Set.of(
            "TRIAGE_OFFICER",
            "CASE_REVIEWER",
            "DECISION_MAKER",
            "APPEAL_OFFICER",
            "SUPERVISOR",
            "AUDITOR",
            "SYSTEM_ADMIN"))) {
      return null;
    }
    return actor.username();
  }

  private AuditEvent newAuditEvent(
      ApplicationActor actor,
      UUID caseId,
      String eventType,
      String action,
      String result,
      String reason,
      String beforeSummary,
      String afterSummary,
      String metadata,
      String correlationId,
      String sourceIp,
      Instant timestamp) {
    return new AuditEvent(
        UUID.randomUUID(),
        eventType,
        "USER",
        actor.username(),
        String.join(",", actor.roles().stream().sorted().toList()),
        action,
        CASE_RESOURCE_TYPE,
        caseId.toString(),
        caseId,
        timestamp,
        correlationId,
        sourceIp,
        result,
        reason,
        beforeSummary,
        afterSummary,
        metadata);
  }

  private boolean hasAnyRole(Set<String> roles, Set<String> candidateRoles) {
    for (String candidateRole : candidateRoles) {
      if (roles.contains(candidateRole)) {
        return true;
      }
    }
    return false;
  }

  private String extractCursorValue(CaseRecord caseRecord, CaseListSortBy sortBy) {
    return switch (sortBy) {
      case CREATED_AT -> caseRecord.createdAt().toString();
      case UPDATED_AT -> caseRecord.updatedAt().toString();
      case CASE_NUMBER -> normalizeTextSort(caseRecord.caseNumber());
      case TITLE -> normalizeTextSort(caseRecord.title());
      case STATUS -> normalizeTextSort(caseRecord.status().name());
    };
  }

  private String normalizeTextSort(String value) {
    return value.toLowerCase(java.util.Locale.ROOT);
  }

  private String extractAuditCursorValue(AuditEvent auditEvent, AuditEventListSortBy sortBy) {
    return switch (sortBy) {
      case TIMESTAMP -> auditEvent.timestamp().toString();
      case EVENT_TYPE -> normalizeTextSort(auditEvent.eventType());
      case ACTION -> normalizeTextSort(auditEvent.action());
      case RESULT -> normalizeTextSort(auditEvent.result());
      case ACTOR_ID -> normalizeTextSort(auditEvent.actorId());
    };
  }
}
