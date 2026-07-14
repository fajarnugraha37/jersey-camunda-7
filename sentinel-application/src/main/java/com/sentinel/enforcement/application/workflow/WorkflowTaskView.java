package com.sentinel.enforcement.application.workflow;

import com.sentinel.enforcement.domain.casefile.CaseStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkflowTaskView(
    String taskId,
    String name,
    String taskDefinitionKey,
    String processInstanceId,
    UUID caseId,
    String caseNumber,
    String caseTitle,
    String caseSummary,
    CaseStatus caseStatus,
    String jurisdictionCode,
    String assigneeUserId,
    Instant createdAt,
    WorkflowTaskState state) {

  public WorkflowTaskView {
    taskId = requireNonBlank(taskId, "taskId");
    name = requireNonBlank(name, "name");
    taskDefinitionKey = requireNonBlank(taskDefinitionKey, "taskDefinitionKey");
    processInstanceId = requireNonBlank(processInstanceId, "processInstanceId");
    Objects.requireNonNull(caseId, "caseId must not be null");
    caseNumber = requireNonBlank(caseNumber, "caseNumber");
    caseTitle = requireNonBlank(caseTitle, "caseTitle");
    caseSummary = requireNonBlank(caseSummary, "caseSummary");
    Objects.requireNonNull(caseStatus, "caseStatus must not be null");
    jurisdictionCode = requireNonBlank(jurisdictionCode, "jurisdictionCode");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(state, "state must not be null");
    if (assigneeUserId != null && assigneeUserId.isBlank()) {
      throw new IllegalArgumentException("assigneeUserId must not be blank when provided");
    }
  }

  private static String requireNonBlank(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}
