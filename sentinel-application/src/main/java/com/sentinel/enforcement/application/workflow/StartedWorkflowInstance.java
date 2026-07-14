package com.sentinel.enforcement.application.workflow;

import java.util.Objects;
import java.util.UUID;

public record StartedWorkflowInstance(
    UUID caseId,
    String processInstanceId,
    String processDefinitionId,
    int processDefinitionVersion,
    String businessKey) {

  public StartedWorkflowInstance {
    Objects.requireNonNull(caseId, "caseId must not be null");
    processInstanceId = requireNonBlank(processInstanceId, "processInstanceId");
    processDefinitionId = requireNonBlank(processDefinitionId, "processDefinitionId");
    businessKey = requireNonBlank(businessKey, "businessKey");
    if (processDefinitionVersion < 1) {
      throw new IllegalArgumentException("processDefinitionVersion must be positive");
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
