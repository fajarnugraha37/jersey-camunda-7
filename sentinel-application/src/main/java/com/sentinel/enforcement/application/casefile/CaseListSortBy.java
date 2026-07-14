package com.sentinel.enforcement.application.casefile;

public enum CaseListSortBy {
  CREATED_AT,
  UPDATED_AT,
  CASE_NUMBER,
  TITLE,
  STATUS;

  public boolean isTimestampBased() {
    return this == CREATED_AT || this == UPDATED_AT;
  }
}
