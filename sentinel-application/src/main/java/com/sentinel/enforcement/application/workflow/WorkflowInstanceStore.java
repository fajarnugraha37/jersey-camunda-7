package com.sentinel.enforcement.application.workflow;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowInstanceStore {

  void upsert(WorkflowInstanceCorrelation workflowInstanceCorrelation, Instant now);

  void saveStarted(StartedWorkflowInstance startedWorkflowInstance, Instant now);

  Optional<WorkflowInstanceCorrelation> findByCaseId(UUID caseId);

  void markCompleted(String processInstanceId, Instant now);

  void markCancelled(UUID caseId, Instant now);
}
