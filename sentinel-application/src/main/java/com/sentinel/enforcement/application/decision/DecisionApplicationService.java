package com.sentinel.enforcement.application.decision;

import com.sentinel.enforcement.application.casefile.CaseNotFoundException;
import com.sentinel.enforcement.application.casefile.CaseRepository;
import com.sentinel.enforcement.application.messaging.ApplicationTransactionManager;
import com.sentinel.enforcement.application.messaging.MessagingEventFactory;
import com.sentinel.enforcement.application.messaging.OutboxRepository;
import com.sentinel.enforcement.application.recommendation.RecommendationRepository;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.CaseAuthorizationScope;
import com.sentinel.enforcement.application.security.Permission;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import com.sentinel.enforcement.domain.decision.Decision;
import com.sentinel.enforcement.domain.decision.DecisionVersion;
import com.sentinel.enforcement.domain.recommendation.Recommendation;
import com.sentinel.enforcement.domain.recommendation.RecommendationStatus;
import com.sentinel.enforcement.domain.sanction.Sanction;
import com.sentinel.enforcement.domain.sanction.SanctionObligation;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public final class DecisionApplicationService {
  private static final String CASE_RESOURCE_TYPE = "CASE";
  private static final String DECISION_RESOURCE_TYPE = "DECISION";

  private final AuthorizationService authorizationService;
  private final ApplicationTransactionManager transactionManager;
  private final CaseRepository caseRepository;
  private final RecommendationRepository recommendationRepository;
  private final DecisionRepository decisionRepository;
  private final OutboxRepository outboxRepository;
  private final Clock clock;

  public DecisionApplicationService(
      AuthorizationService authorizationService,
      ApplicationTransactionManager transactionManager,
      CaseRepository caseRepository,
      RecommendationRepository recommendationRepository,
      DecisionRepository decisionRepository,
      OutboxRepository outboxRepository,
      Clock clock) {
    this.authorizationService = authorizationService;
    this.transactionManager = transactionManager;
    this.caseRepository = caseRepository;
    this.recommendationRepository = recommendationRepository;
    this.decisionRepository = decisionRepository;
    this.outboxRepository = outboxRepository;
    this.clock = clock;
  }

  public Decision createDecision(
      ApplicationActor actor, UUID caseId, CreateDecisionCommand command) {
    CaseRecord caseRecord = getRequiredCase(caseId);
    Recommendation recommendation =
        recommendationRepository
            .findByCaseId(caseId)
            .filter(value -> value.status() == RecommendationStatus.APPROVED)
            .orElseThrow(
                () ->
                    new com.sentinel.enforcement.domain.decision.DecisionConflictException(
                        "DECISION_CREATE_NOT_ALLOWED",
                        "Approved recommendation is required before creating a decision."));
    authorizationService.requirePermission(
        actor,
        Permission.CREATE_DECISION,
        authorizationContext(
            caseRecord,
            CASE_RESOURCE_TYPE,
            caseRecord.id().toString(),
            recommendation.createdBy()));
    if (caseRecord.status() != CaseStatus.PENDING_DECISION) {
      throw new com.sentinel.enforcement.domain.decision.DecisionConflictException(
          "DECISION_CREATE_NOT_ALLOWED",
          "Decision can only be created while the case is pending decision.");
    }
    if (decisionRepository.findByCaseId(caseId).isPresent()) {
      throw new com.sentinel.enforcement.domain.decision.DecisionConflictException(
          "DECISION_ALREADY_EXISTS", "Case already has a decision.");
    }
    Instant now = clock.instant();
    Decision decision =
        Decision.create(
            UUID.randomUUID(),
            caseId,
            recommendation.id(),
            command.title(),
            command.summary(),
            command.violationProven(),
            command.sanctionSummary(),
            command.obligationTitle(),
            command.obligationDetails(),
            command.obligationDueDate(),
            command.appealDeadline(),
            now,
            actor.username());
    AuditEvent auditEvent =
        newAuditEvent(
            actor,
            caseId,
            decision.id(),
            "DecisionCreated",
            "DECISION_CREATED",
            "SUCCESS",
            "Decision created.",
            null,
            decision.auditSummary(),
            "recommendationId=" + recommendation.id(),
            command.correlationId(),
            command.sourceIp(),
            now);
    transactionManager.required(
        () -> {
          decisionRepository.save(decision);
          caseRepository.appendAuditEvent(auditEvent);
          return null;
        });
    return decision;
  }

  public Decision approveDecision(
      ApplicationActor actor, UUID decisionId, ApproveDecisionCommand command) {
    Decision current = getRequiredDecision(decisionId);
    CaseRecord caseRecord = getRequiredCase(current.caseId());
    authorizationService.requirePermission(
        actor,
        Permission.APPROVE_DECISION,
        authorizationContext(
            caseRecord, DECISION_RESOURCE_TYPE, decisionId.toString(), current.createdBy()));
    Instant now = clock.instant();
    Decision updated = current.approve(now, actor.username());
    AuditEvent auditEvent =
        newAuditEvent(
            actor,
            caseRecord.id(),
            updated.id(),
            "DecisionApproved",
            "DECISION_APPROVED",
            "SUCCESS",
            "Decision approved.",
            current.auditSummary(),
            updated.auditSummary(),
            null,
            command.correlationId(),
            command.sourceIp(),
            now);
    transactionManager.required(
        () -> {
          decisionRepository.approve(updated);
          caseRepository.appendAuditEvent(auditEvent);
          return null;
        });
    return updated;
  }

  public Decision publishDecision(
      ApplicationActor actor, UUID decisionId, PublishDecisionCommand command) {
    Decision current = getRequiredDecision(decisionId);
    CaseRecord caseRecord = getRequiredCase(current.caseId());
    authorizationService.requirePermission(
        actor,
        Permission.PUBLISH_DECISION,
        authorizationContext(
            caseRecord, DECISION_RESOURCE_TYPE, decisionId.toString(), current.createdBy()));
    Instant now = clock.instant();
    Decision updated = current.publish(now, actor.username());
    DecisionVersion decisionVersion =
        new DecisionVersion(
            UUID.randomUUID(),
            updated.id(),
            1,
            updated.title(),
            updated.summary(),
            updated.violationProven(),
            updated.sanctionSummary(),
            updated.obligationTitle(),
            updated.obligationDetails(),
            updated.obligationDueDate(),
            updated.appealDeadline(),
            updated.publishedAt(),
            updated.publishedBy(),
            now,
            actor.username());
    Sanction sanction =
        updated.violationProven()
            ? Sanction.create(
                UUID.randomUUID(),
                updated.caseId(),
                updated.id(),
                updated.sanctionSummary(),
                now,
                actor.username())
            : null;
    SanctionObligation obligation =
        sanction == null
            ? null
            : SanctionObligation.create(
                UUID.randomUUID(),
                sanction.id(),
                updated.obligationTitle(),
                updated.obligationDetails(),
                updated.obligationDueDate(),
                now,
                actor.username());
    AuditEvent auditEvent =
        newAuditEvent(
            actor,
            caseRecord.id(),
            updated.id(),
            "DecisionPublished",
            "DECISION_PUBLISHED",
            "SUCCESS",
            "Decision published.",
            current.auditSummary(),
            updated.auditSummary(),
            "violationProven=" + updated.violationProven(),
            command.correlationId(),
            command.sourceIp(),
            now);
    transactionManager.required(
        () -> {
          decisionRepository.publish(updated, decisionVersion, sanction, obligation);
          caseRepository.appendAuditEvent(auditEvent);
          outboxRepository.enqueue(
              MessagingEventFactory.decisionPublished(
                  actor, updated, command.correlationId(), now));
          if (sanction != null && obligation != null) {
            outboxRepository.enqueue(
                MessagingEventFactory.sanctionCreated(
                    actor, sanction, obligation, command.correlationId(), now));
          }
          return null;
        });
    return updated;
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
      UUID decisionId,
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
        DECISION_RESOURCE_TYPE,
        decisionId.toString(),
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
