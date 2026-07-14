package com.sentinel.enforcement.application.workflow;

public record WorkflowReconciliationActionCommand(
    WorkflowReconciliationAction action, String reason, String correlationId, String sourceIp) {

  public WorkflowReconciliationActionCommand {
    if (action == null) {
      throw new IllegalArgumentException("action must not be null");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
    if (correlationId == null || correlationId.isBlank()) {
      throw new IllegalArgumentException("correlationId must not be blank");
    }
  }
}
