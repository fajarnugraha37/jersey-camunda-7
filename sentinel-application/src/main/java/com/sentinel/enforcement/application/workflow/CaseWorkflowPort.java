package com.sentinel.enforcement.application.workflow;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseWorkflowPort {

  StartedWorkflowInstance startCaseWorkflow(
      UUID caseId,
      String jurisdictionCode,
      String caseNumber,
      String caseTitle,
      Duration investigationEscalationDuration,
      String startedBy);

  void cancelCaseWorkflow(UUID caseId, String reason);

  List<WorkflowTaskView> listActiveTasks();

  Optional<WorkflowTaskView> findActiveTask(String taskId);

  boolean isTaskCompleted(String taskId);

  WorkflowTaskView claimTask(String taskId, String username);

  void completeTask(String taskId);
}
