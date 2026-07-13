package com.sentinel.enforcement.application.casefile;

import com.sentinel.enforcement.application.report.ReportNotFoundException;
import com.sentinel.enforcement.application.report.ReportRepository;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.Permission;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseActionContext;
import com.sentinel.enforcement.domain.casefile.CaseAssignment;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import com.sentinel.enforcement.domain.casefile.CaseStatusHistoryEntry;
import com.sentinel.enforcement.domain.report.Report;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CaseApplicationService {
  private static final String CASE_RESOURCE_TYPE = "CASE";

  private final AuthorizationService authorizationService;
  private final CaseRepository caseRepository;
  private final ReportRepository reportRepository;
  private final Clock clock;

  public CaseApplicationService(
      AuthorizationService authorizationService,
      CaseRepository caseRepository,
      ReportRepository reportRepository,
      Clock clock) {
    this.authorizationService = authorizationService;
    this.caseRepository = caseRepository;
    this.reportRepository = reportRepository;
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

    Instant now = clock.instant();
    String caseNumber =
        caseRepository.nextCaseNumber(
            report.jurisdictionCode(), now.atOffset(ZoneOffset.UTC).getYear());
    CaseRecord caseRecord =
        CaseRecord.create(
            UUID.randomUUID(),
            caseNumber,
            report.id(),
            command.title(),
            command.summary(),
            report.jurisdictionCode(),
            now,
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
    caseRepository.save(caseRecord, historyEntry, auditEvent);
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

    List<CaseRecord> loaded =
        caseRepository.findPage(
            new CasePageRequest(
                actor.jurisdictions(),
                restrictedAssignee(actor),
                query.cursorCreatedAt(),
                query.cursorId(),
                query.limit() + 1));
    boolean hasNextPage = loaded.size() > query.limit();
    List<CaseRecord> trimmed =
        hasNextPage ? new ArrayList<>(loaded.subList(0, query.limit())) : new ArrayList<>(loaded);
    trimmed.sort(
        Comparator.comparing(CaseRecord::createdAt)
            .reversed()
            .thenComparing(CaseRecord::id, Comparator.reverseOrder()));

    Instant nextCursorCreatedAt = null;
    UUID nextCursorId = null;
    if (hasNextPage && !trimmed.isEmpty()) {
      CaseRecord nextCursorRecord = trimmed.get(trimmed.size() - 1);
      nextCursorCreatedAt = nextCursorRecord.createdAt();
      nextCursorId = nextCursorRecord.id();
    }
    return new CasePage(trimmed, nextCursorCreatedAt, nextCursorId, hasNextPage);
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
    caseRepository.assign(updated, assignment, auditEvent);
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
    caseRepository.transition(updated, historyEntry, auditEvent);
    return updated;
  }

  public List<AuditEvent> getCaseAuditEvents(ApplicationActor actor, UUID caseId, int limit) {
    CaseRecord caseRecord = getRequiredCase(caseId);
    authorizationService.requirePermission(
        actor,
        Permission.READ_CASE_AUDIT,
        new AuthorizationContext(
            caseRecord.jurisdictionCode(),
            CASE_RESOURCE_TYPE,
            caseRecord.id().toString(),
            caseRecord.assigneeUserId()));
    return caseRepository.findAuditEvents(caseId, limit);
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
}
