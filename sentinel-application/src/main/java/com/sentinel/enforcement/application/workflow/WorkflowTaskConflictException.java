package com.sentinel.enforcement.application.workflow;

public final class WorkflowTaskConflictException extends RuntimeException {
  private final String code;

  public WorkflowTaskConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
