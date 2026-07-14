package com.sentinel.enforcement.application.workflow;

public final class WorkflowReconciliationConflictException extends RuntimeException {
  private final String code;

  public WorkflowReconciliationConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
