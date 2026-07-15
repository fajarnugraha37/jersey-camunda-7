package com.sentinel.enforcement.application.appeal;

import com.sentinel.enforcement.application.casefile.CaseNotFoundException;
import com.sentinel.enforcement.application.casefile.CaseRepository;
import com.sentinel.enforcement.application.decision.DecisionNotFoundException;
import com.sentinel.enforcement.application.decision.DecisionRepository;
import com.sentinel.enforcement.application.messaging.ApplicationTransactionManager;
import com.sentinel.enforcement.application.messaging.MessagingEventFactory;
import com.sentinel.enforcement.application.messaging.OutboxRepository;
import com.sentinel.enforcement.application.sanction.SanctionRepository;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.CaseAuthorizationScope;
import com.sentinel.enforcement.application.security.Permission;
import com.sentinel.enforcement.application.workflow.CaseWorkflowPort;
import com.sentinel.enforcement.application.workflow.StartedWorkflowInstance;
import com.sentinel.enforcement.domain.appeal.Appeal;
import com.sentinel.enforcement.domain.appeal.AppealDecision;
import com.sentinel.enforcement.domain.appeal.AppealDecisionOutcome;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseActionContext;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import com.sentinel.enforcement.domain.casefile.CaseStatusHistoryEntry;
import com.sentinel.enforcement.domain.decision.Decision;
import com.sentinel.enforcement.domain.decision.DecisionStatus;
import com.sentinel.enforcement.domain.sanction.Sanction;
import com.sentinel.enforcement.domain.sanction.SanctionObligation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public final class AppealApplicationService {
  private static final String CASE_RESOURCE_TYPE = "CASE";
  private static final String APPEAL_RESOURCE_TYPE = "APPEAL";

  private final AuthorizationService authorizationService;
  private final ApplicationTransactionManager transactionManager;
  private final CaseRepository caseRepository;
  private final DecisionRepository decisionRepository;
  private final AppealRepository appealRepository;
  private final SanctionRepository sanctionRepository;
  private final OutboxRepository outboxRepository;
  private final CaseWorkflowPort workflowPort;
  private final Clock clock;

  public AppealApplicationService(
      AuthorizationService authorizationService,
      ApplicationTransactionManager transactionManager,
      CaseRepository caseRepository,
      DecisionRepository decisionRepository,
      AppealRepository appealRepository,
      SanctionRepository sanctionRepository,
      OutboxRepository outboxRepository,
      CaseWorkflowPort workflowPort,
      Clock clock) {
    this.authorizationService = authorizationService;
    this.transactionManager = transactionManager;
    this.caseRepository = caseRepository;
    this.decisionRepository = decisionRepository;
    this.appealRepository = appealRepository;
    this.sanctionRepository = sanctionRepository;
    this.outboxRepository = outboxRepository;
    this.workflowPort = workflowPort;
    this.clock = clock;
  }

  public Appeal createAppeal(ApplicationActor actor, UUID decisionId, CreateAppealCommand command) {
    Decision decision = getRequiredDecision(decisionId);
    CaseRecord currentCase = getRequiredCase(decision.caseId());
    authorizationService.requirePermission(
        actor,
        Permission.CREATE_APPEAL,
        authorizationContext(
            currentCase, CASE_RESOURCE_TYPE, currentCase.id().toString(), decision.createdBy()));
    if (decision.status() != DecisionStatus.PUBLISHED) {
      throw new com.sentinel.enforcement.domain.appeal.AppealConflictException(
          "APPEAL_CREATE_NOT_ALLOWED", "Appeals can only be filed for published decisions.");
    }
    if (currentCase.status() != CaseStatus.DECIDED) {
      throw new com.sentinel.enforcement.domain.appeal.AppealConflictException(
          "APPEAL_CREATE_NOT_ALLOWED",
          "Appeal can only be created when the case is in DECIDED status.");
    }
    if (appealRepository.findActiveByDecisionId(decisionId).isPresent()) {
      throw new com.sentinel.enforcement.domain.appeal.AppealConflictException(
          "APPEAL_ALREADY_EXISTS", "Decision already has an active appeal.");
    }
    Instant lateThreshold =
        decision.appealDeadline().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    if (command.submittedAt().isAfter(lateThreshold)) {
      if (!command.supervisorOverride() || !actor.hasRole("SUPERVISOR")) {
        throw new com.sentinel.enforcement.domain.appeal.AppealConflictException(
            "APPEAL_LATE_OVERRIDE_REQUIRED", "Late appeal requires explicit supervisor override.");
      }
    }

    Instant now = clock.instant();
    Appeal appeal =
        Appeal.create(
            UUID.randomUUID(),
            currentCase.id(),
            decisionId,
            command.rationale(),
            command.supervisorOverride(),
            command.supervisorOverrideReason(),
            command.submittedAt(),
            actor.username());
    CaseActionContext context =
        new CaseActionContext(
            actor.username(),
            actor.roles(),
            currentCase.version(),
            "Appeal filed for published decision.",
            now);
    CaseRecord updatedCase = currentCase.transitionTo(CaseStatus.UNDER_APPEAL, context);
    CaseStatusHistoryEntry historyEntry =
        new CaseStatusHistoryEntry(
            UUID.randomUUID(),
            updatedCase.id(),
            currentCase.status(),
            updatedCase.status(),
            "Appeal filed for published decision.",
            now,
            actor.username(),
            now,
            actor.username());
    AuditEvent auditEvent =
        newAuditEvent(
            actor,
            currentCase.id(),
            appeal.id(),
            "AppealFiled",
            "APPEAL_FILED",
            "SUCCESS",
            "Appeal filed.",
            currentCase.auditSummary(),
            appeal.auditSummary(),
            "decisionId=" + decisionId,
            command.correlationId(),
            command.sourceIp(),
            now);
    StartedWorkflowInstance startedWorkflow =
        workflowPort.startAppealWorkflow(
            currentCase.id(),
            appeal.id(),
            currentCase.jurisdictionCode(),
            currentCase.caseNumber(),
            currentCase.title(),
            actor.username());
    try {
      transactionManager.required(
          () -> {
            appealRepository.save(appeal);
            caseRepository.transition(updatedCase, historyEntry, auditEvent);
            outboxRepository.enqueue(
                MessagingEventFactory.appealFiled(actor, appeal, command.correlationId(), now));
            return null;
          });
    } catch (RuntimeException exception) {
      try {
        workflowPort.cancelAppealWorkflow(
            currentCase.id(), "Compensating cancellation after appeal persistence failure.");
      } catch (RuntimeException cancellationException) {
        exception.addSuppressed(cancellationException);
      }
      throw exception;
    }
    return appeal;
  }

  public Appeal decideAppeal(ApplicationActor actor, UUID appealId, DecideAppealCommand command) {
    Appeal current = getRequiredAppeal(appealId);
    CaseRecord caseRecord = getRequiredCase(current.caseId());
    authorizationService.requirePermission(
        actor,
        Permission.DECIDE_APPEAL,
        authorizationContext(
            caseRecord, APPEAL_RESOURCE_TYPE, appealId.toString(), current.createdBy()));
    Instant now = clock.instant();
    AppealDecision appealDecision =
        new AppealDecision(
            UUID.randomUUID(),
            appealId,
            command.outcome(),
            command.summary(),
            now,
            actor.username(),
            now,
            actor.username(),
            0L);
    Appeal updated = current.decide(appealDecision.id(), now, actor.username());
    AuditEvent auditEvent =
        newAuditEvent(
            actor,
            caseRecord.id(),
            updated.id(),
            "AppealDecided",
            "APPEAL_DECIDED",
            "SUCCESS",
            "Appeal decision recorded.",
            current.auditSummary(),
            updated.auditSummary(),
            "outcome=" + command.outcome(),
            command.correlationId(),
            command.sourceIp(),
            now);
    transactionManager.required(
        () -> {
          appealRepository.decide(updated, appealDecision);
          caseRepository.appendAuditEvent(auditEvent);
          outboxRepository.enqueue(
              MessagingEventFactory.appealDecided(
                  actor, updated, appealDecision, command.correlationId(), now));
          return null;
        });
    return updated;
  }

  public CaseRecord finalizeAppealWorkflowTask(
      ApplicationActor actor, UUID caseId, String correlationId, String sourceIp) {
    CaseRecord currentCase = getRequiredCase(caseId);
    Appeal appeal =
        appealRepository
            .findLatestByCaseId(caseId)
            .orElseThrow(
                () ->
                    new com.sentinel.enforcement.domain.appeal.AppealConflictException(
                        "APPEAL_WORKFLOW_NOT_READY",
                        "Appeal workflow cannot complete without an active appeal."));
    AppealDecision appealDecision =
        appealRepository
            .findDecisionByAppealId(appeal.id())
            .orElseThrow(
                () ->
                    new com.sentinel.enforcement.domain.appeal.AppealConflictException(
                        "APPEAL_WORKFLOW_NOT_READY",
                        "Appeal workflow cannot complete before an appeal decision exists."));

    Instant now = clock.instant();
    CaseStatus targetStatus =
        appealDecision.outcome() == AppealDecisionOutcome.GRANTED
            ? CaseStatus.CLOSED
            : CaseStatus.ENFORCEMENT_IN_PROGRESS;
    CaseActionContext context =
        new CaseActionContext(
            actor.username(),
            actor.roles(),
            currentCase.version(),
            "Appeal workflow completed after recorded appeal decision.",
            now);
    CaseRecord updatedCase = currentCase.transitionTo(targetStatus, context);
    CaseStatusHistoryEntry historyEntry =
        new CaseStatusHistoryEntry(
            UUID.randomUUID(),
            updatedCase.id(),
            currentCase.status(),
            updatedCase.status(),
            "Appeal workflow completed after recorded appeal decision.",
            now,
            actor.username(),
            now,
            actor.username());
    AuditEvent auditEvent =
        newAuditEvent(
            actor,
            caseId,
            appeal.id(),
            "AppealWorkflowCompleted",
            "APPEAL_WORKFLOW_COMPLETED",
            "SUCCESS",
            "Appeal workflow completed.",
            currentCase.auditSummary(),
            appeal.auditSummary(),
            "outcome=" + appealDecision.outcome(),
            correlationId,
            sourceIp,
            now);

    Sanction sanction = sanctionRepository.findByDecisionId(appeal.decisionId()).orElse(null);
    SanctionObligation obligation =
        sanction == null
            ? null
            : sanctionRepository.findActiveObligationBySanctionId(sanction.id()).orElse(null);
    Sanction cancelledSanction =
        appealDecision.outcome() == AppealDecisionOutcome.GRANTED && sanction != null
            ? sanction.cancel(now, actor.username())
            : null;
    SanctionObligation cancelledObligation =
        appealDecision.outcome() == AppealDecisionOutcome.GRANTED && obligation != null
            ? obligation.cancel(now, actor.username())
            : null;

    transactionManager.required(
        () -> {
          if (cancelledSanction != null && cancelledObligation != null) {
            sanctionRepository.cancelSanctionAndObligation(
                cancelledSanction, cancelledObligation, actor.username());
            outboxRepository.enqueue(
                MessagingEventFactory.sanctionCancelled(
                    actor, cancelledSanction, cancelledObligation, correlationId, now));
          }
          caseRepository.transition(updatedCase, historyEntry, auditEvent);
          return null;
        });
    return updatedCase;
  }

  private Appeal getRequiredAppeal(UUID appealId) {
    return appealRepository
        .findById(appealId)
        .orElseThrow(() -> new AppealNotFoundException(appealId));
  }

  private Decision getRequiredDecision(UUID decisionId) {
    return decisionRepository
        .findById(decisionId)
        .orElseThrow(() -> new DecisionNotFoundException(decisionId));
  }

  private CaseRecord getRequiredCase(UUID caseId) {
    return caseRepository.findById(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
  }

  private AuthorizationContext authorizationContext(
      CaseRecord caseRecord, String resourceType, String resourceId, String resourceOwnerId) {
    return new AuthorizationContext(
        caseRecord.jurisdictionCode(),
        resourceType,
        resourceId,
        caseRecord.id(),
        caseRecord.assigneeUserId(),
        caseRecord.assignedUnitId(),
        caseRecord.classification(),
        resourceOwnerId,
        CaseAuthorizationScope.RESTRICTED_TO_ASSIGNED_UNITS_WHEN_PRESENT);
  }

  private AuditEvent newAuditEvent(
      ApplicationActor actor,
      UUID caseId,
      UUID appealId,
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
        APPEAL_RESOURCE_TYPE,
        appealId.toString(),
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
}
