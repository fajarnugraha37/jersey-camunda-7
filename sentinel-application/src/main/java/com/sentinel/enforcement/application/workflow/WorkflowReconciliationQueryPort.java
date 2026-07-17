package com.sentinel.enforcement.application.workflow;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowReconciliationQueryPort {

  WorkflowReconciliationPage findIssuePage(ListWorkflowReconciliationIssuesQuery query);

  Optional<WorkflowReconciliationCandidate> findCandidateByCaseId(UUID caseId);
}
