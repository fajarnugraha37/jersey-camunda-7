package com.sentinel.enforcement.application.workflow;

public enum WorkflowReconciliationSortBy {
  CASE_UPDATED_AT,
  CASE_NUMBER,
  CASE_STATUS,
  ISSUE_TYPE,
  CORRELATION_STATUS;

  public boolean isTimestampBased() {
    return this == CASE_UPDATED_AT;
  }
}
