package com.sentinel.enforcement.application.sanction;

import com.sentinel.enforcement.domain.sanction.Sanction;
import com.sentinel.enforcement.domain.sanction.SanctionObligation;
import java.util.Optional;
import java.util.UUID;

public interface SanctionRepository {
  Optional<Sanction> findByDecisionId(UUID decisionId);

  Optional<Sanction> findByCaseId(UUID caseId);

  long countActiveObligationsForCase(UUID caseId);

  void cancelSanctionAndObligation(
      Sanction sanction, SanctionObligation sanctionObligation, String updatedBy);

  Optional<SanctionObligation> findActiveObligationBySanctionId(UUID sanctionId);
}
