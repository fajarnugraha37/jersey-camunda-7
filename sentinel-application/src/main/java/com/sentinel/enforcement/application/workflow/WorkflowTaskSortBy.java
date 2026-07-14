package com.sentinel.enforcement.application.workflow;

public enum WorkflowTaskSortBy {
  CREATED_AT,
  TASK_NAME,
  CASE_NUMBER,
  CASE_STATUS;

  public boolean isTimestampBased() {
    return this == CREATED_AT;
  }
}
