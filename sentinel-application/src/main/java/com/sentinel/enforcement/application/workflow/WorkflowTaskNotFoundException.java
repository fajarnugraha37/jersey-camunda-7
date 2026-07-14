package com.sentinel.enforcement.application.workflow;

public final class WorkflowTaskNotFoundException extends RuntimeException {
  public WorkflowTaskNotFoundException(String taskId) {
    super("Workflow task " + taskId + " was not found.");
  }
}
