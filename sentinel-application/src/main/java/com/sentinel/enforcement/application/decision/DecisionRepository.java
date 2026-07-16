package com.sentinel.enforcement.application.decision;

import com.sentinel.enforcement.domain.decision.Decision;
import com.sentinel.enforcement.domain.decision.DecisionVersion;
import com.sentinel.enforcement.domain.sanction.Sanction;
import com.sentinel.enforcement.domain.sanction.SanctionObligation;
import java.util.Optional;
import java.util.UUID;

public interface DecisionRepository {
  void save(Decision decision);

  Optional<Decision> findById(UUID decisionId);

  Optional<Decision> findByIdForUpdate(UUID decisionId);

  Optional<Decision> findByCaseId(UUID caseId);

  void approve(Decision decision);

  void publish(
      Decision decision,
      DecisionVersion decisionVersion,
      Sanction sanction,
      SanctionObligation sanctionObligation);

  boolean existsPublishedForCase(UUID caseId);
}
