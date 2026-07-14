package com.sentinel.enforcement.application.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowReconciliationQueryPort {

  List<WorkflowReconciliationCandidate> findCandidates();

  Optional<WorkflowReconciliationCandidate> findCandidateByCaseId(UUID caseId);
}
