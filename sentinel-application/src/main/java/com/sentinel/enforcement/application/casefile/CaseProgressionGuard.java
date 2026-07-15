package com.sentinel.enforcement.application.casefile;

import com.sentinel.enforcement.domain.casefile.CaseStatus;
import java.util.UUID;

@FunctionalInterface
public interface CaseProgressionGuard {
  CaseProgressionGuard NO_OP = (caseId, targetStatus) -> {};

  void requireTargetStatePrerequisites(UUID caseId, CaseStatus targetStatus);
}
