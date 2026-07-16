package com.sentinel.enforcement.application.recommendation;

import com.sentinel.enforcement.application.casefile.CaseNotFoundException;
import com.sentinel.enforcement.application.casefile.CaseRepository;
import com.sentinel.enforcement.application.messaging.ApplicationTransactionManager;
import com.sentinel.enforcement.application.messaging.MessagingEventFactory;
import com.sentinel.enforcement.application.messaging.OutboxRepository;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationDeniedException;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.CaseAuthorizationScope;
import com.sentinel.enforcement.application.security.Permission;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import com.sentinel.enforcement.domain.recommendation.Recommendation;
import com.sentinel.enforcement.domain.recommendation.RecommendationConflictException;
import com.sentinel.enforcement.domain.recommendation.RecommendationReview;
import com.sentinel.enforcement.domain.recommendation.RecommendationReviewOutcome;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public final class RecommendationApplicationService {
  private static final String CASE_RESOURCE_TYPE = "CASE";
  private static final String RECOMMENDATION_RESOURCE_TYPE = "RECOMMENDATION";

  private final AuthorizationService authorizationService;
  private final ApplicationTransactionManager transactionManager;
  private final CaseRepository caseRepository;
  private final RecommendationRepository recommendationRepository;
  private final OutboxRepository outboxRepository;
  private final Clock clock;

  public RecommendationApplicationService(
      AuthorizationService authorizationService,
      ApplicationTransactionManager transactionManager,
      CaseRepository caseRepository,
      RecommendationRepository recommendationRepository,
      OutboxRepository outboxRepository,
      Clock clock) {
    this.authorizationService = authorizationService;
    this.transactionManager = transactionManager;
    this.caseRepository = caseRepository;
    this.recommendationRepository = recommendationRepository;
    this.outboxRepository = outboxRepository;
    this.clock = clock;
  }

  public Recommendation createRecommendation(
      ApplicationActor actor, UUID caseId, CreateRecommendationCommand command) {
    CaseRecord caseRecord = getRequiredCase(caseId);
    authorizationService.requirePermission(
        actor,
        Permission.CREATE_RECOMMENDATION,
        authorizationContext(
            caseRecord, CASE_RESOURCE_TYPE, caseRecord.id().toString(), caseRecord.createdBy()));
    if (caseRecord.status() != CaseStatus.UNDER_INVESTIGATION) {
      throw new RecommendationConflictException(
          "RECOMMENDATION_CREATE_NOT_ALLOWED",
          "Recommendation can only be created while the case is under investigation.");
    }
    if (recommendationRepository.findByCaseId(caseId).isPresent()) {
      throw new RecommendationConflictException(
          "RECOMMENDATION_ALREADY_EXISTS",
          "Case " + caseRecord.caseNumber() + " already has a recommendation.");
    }

    Instant now = clock.instant();
    Recommendation recommendation =
        Recommendation.create(
            UUID.randomUUID(),
            caseId,
            command.title(),
            command.summary(),
            command.proposedDecision(),
            command.proposedSanction(),
            now,
            actor.username());
    AuditEvent auditEvent =
        newAuditEvent(
            actor,
            caseId,
            recommendation.id(),
            "RecommendationCreated",
            "RECOMMENDATION_CREATED",
            "SUCCESS",
            "Recommendation created.",
            null,
            recommendation.auditSummary(),
            "title=" + recommendation.title(),
            command.correlationId(),
            command.sourceIp(),
            now);
    transactionManager.required(
        () -> {
          recommendationRepository.save(recommendation);
          caseRepository.appendAuditEvent(auditEvent);
          outboxRepository.enqueue(MessagingEventFactory.auditIntegrated(auditEvent, now));
          return null;
        });
    return recommendation;
  }

  public Recommendation submitRecommendation(
      ApplicationActor actor, UUID recommendationId, SubmitRecommendationCommand command) {
    Recommendation current = getRequiredRecommendation(recommendationId);
    CaseRecord caseRecord = getRequiredCase(current.caseId());
    authorizationService.requirePermission(
        actor,
        Permission.SUBMIT_RECOMMENDATION,
        authorizationContext(
            caseRecord,
            RECOMMENDATION_RESOURCE_TYPE,
            recommendationId.toString(),
            current.createdBy()));
    if (!actor.username().equals(current.createdBy()) && !actor.hasRole("SUPERVISOR")) {
      throw new AuthorizationDeniedException(
          "Only the recommendation author or supervisor may submit the recommendation.");
    }
    Recommendation updated = current.submit(clock.instant(), actor.username());
    AuditEvent auditEvent =
        newAuditEvent(
            actor,
            caseRecord.id(),
            updated.id(),
            "RecommendationSubmitted",
            "RECOMMENDATION_SUBMITTED",
            "SUCCESS",
            "Recommendation submitted.",
            current.auditSummary(),
            updated.auditSummary(),
            null,
            command.correlationId(),
            command.sourceIp(),
            clock.instant());
    transactionManager.required(
        () -> {
          recommendationRepository.submit(updated);
          caseRepository.appendAuditEvent(auditEvent);
          outboxRepository.enqueue(
              MessagingEventFactory.auditIntegrated(auditEvent, auditEvent.timestamp()));
          return null;
        });
    return updated;
  }

  public Recommendation approveRecommendation(
      ApplicationActor actor, UUID recommendationId, ReviewRecommendationCommand command) {
    Recommendation current = getRequiredRecommendation(recommendationId);
    CaseRecord caseRecord = getRequiredCase(current.caseId());
    authorizationService.requirePermission(
        actor,
        Permission.REVIEW_RECOMMENDATION,
        authorizationContext(
            caseRecord,
            RECOMMENDATION_RESOURCE_TYPE,
            recommendationId.toString(),
            current.createdBy()));
    if (caseRecord.status() != CaseStatus.PENDING_REVIEW) {
      throw new RecommendationConflictException(
          "RECOMMENDATION_REVIEW_NOT_ALLOWED",
          "Recommendation can only be approved while the case is pending review.");
    }

    Instant now = clock.instant();
    RecommendationReview review =
        new RecommendationReview(
            UUID.randomUUID(),
            recommendationId,
            RecommendationReviewOutcome.APPROVED,
            command.reviewSummary(),
            now,
            actor.username(),
            now,
            actor.username(),
            0L);
    Recommendation updated = current.approve(review.id(), now, actor.username());
    AuditEvent auditEvent =
        newAuditEvent(
            actor,
            caseRecord.id(),
            updated.id(),
            "RecommendationApproved",
            "RECOMMENDATION_APPROVED",
            "SUCCESS",
            "Recommendation approved.",
            current.auditSummary(),
            updated.auditSummary(),
            "reviewId=" + review.id(),
            command.correlationId(),
            command.sourceIp(),
            now);
    transactionManager.required(
        () -> {
          recommendationRepository.approve(updated, review);
          caseRepository.appendAuditEvent(auditEvent);
          outboxRepository.enqueue(MessagingEventFactory.auditIntegrated(auditEvent, now));
          return null;
        });
    return updated;
  }

  private Recommendation getRequiredRecommendation(UUID recommendationId) {
    return recommendationRepository
        .findById(recommendationId)
        .orElseThrow(() -> new RecommendationNotFoundException(recommendationId));
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
      UUID recommendationId,
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
        RECOMMENDATION_RESOURCE_TYPE,
        recommendationId.toString(),
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
