package com.sentinel.enforcement.application.casefile;

import com.sentinel.enforcement.application.appeal.AppealRepository;
import com.sentinel.enforcement.application.decision.DecisionRepository;
import com.sentinel.enforcement.application.recommendation.RecommendationRepository;
import com.sentinel.enforcement.application.sanction.SanctionRepository;
import com.sentinel.enforcement.domain.casefile.CaseConflictException;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import java.util.UUID;

public final class PhaseSevenCaseProgressionGuard implements CaseProgressionGuard {
  private final RecommendationRepository recommendationRepository;
  private final DecisionRepository decisionRepository;
  private final AppealRepository appealRepository;
  private final SanctionRepository sanctionRepository;

  public PhaseSevenCaseProgressionGuard(
      RecommendationRepository recommendationRepository,
      DecisionRepository decisionRepository,
      AppealRepository appealRepository,
      SanctionRepository sanctionRepository) {
    this.recommendationRepository = recommendationRepository;
    this.decisionRepository = decisionRepository;
    this.appealRepository = appealRepository;
    this.sanctionRepository = sanctionRepository;
  }

  @Override
  public void requireTargetStatePrerequisites(UUID caseId, CaseStatus targetStatus) {
    switch (targetStatus) {
      case PENDING_DECISION -> {
        if (!recommendationRepository.existsApprovedForCase(caseId)) {
          throw new CaseConflictException(
              "CASE_TRANSITION_NOT_ALLOWED",
              "Case cannot move to PENDING_DECISION without an approved recommendation.");
        }
      }
      case DECIDED -> {
        if (!decisionRepository.existsPublishedForCase(caseId)) {
          throw new CaseConflictException(
              "CASE_TRANSITION_NOT_ALLOWED",
              "Case cannot move to DECIDED without a published decision.");
        }
      }
      case UNDER_APPEAL -> {
        if (!appealRepository.existsActiveByCaseId(caseId)) {
          throw new CaseConflictException(
              "CASE_TRANSITION_NOT_ALLOWED",
              "Case cannot move to UNDER_APPEAL without an active appeal.");
        }
      }
      case ENFORCEMENT_IN_PROGRESS -> {
        if (!decisionRepository.existsPublishedForCase(caseId)) {
          throw new CaseConflictException(
              "CASE_TRANSITION_NOT_ALLOWED",
              "Case cannot move to ENFORCEMENT_IN_PROGRESS without a published decision.");
        }
      }
      case CLOSED -> {
        if (sanctionRepository.countActiveObligationsForCase(caseId) > 0) {
          throw new CaseConflictException(
              "CASE_TRANSITION_NOT_ALLOWED",
              "Case cannot be closed while active sanction obligations remain.");
        }
      }
      default -> {}
    }
  }
}
