package com.sentinel.enforcement.application.appeal;

import com.sentinel.enforcement.domain.appeal.Appeal;
import com.sentinel.enforcement.domain.appeal.AppealDecision;
import java.util.Optional;
import java.util.UUID;

public interface AppealRepository {
  void save(Appeal appeal);

  Optional<Appeal> findById(UUID appealId);

  Optional<Appeal> findLatestByCaseId(UUID caseId);

  Optional<Appeal> findActiveByDecisionId(UUID decisionId);

  Optional<Appeal> findActiveByCaseId(UUID caseId);

  Optional<AppealDecision> findDecisionByAppealId(UUID appealId);

  void decide(Appeal appeal, AppealDecision appealDecision);

  boolean existsActiveByCaseId(UUID caseId);
}
