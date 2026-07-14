package com.sentinel.enforcement.application.casefile;

public enum AuditEventListSortBy {
  TIMESTAMP,
  EVENT_TYPE,
  ACTION,
  RESULT,
  ACTOR_ID;

  public boolean isTimestampBased() {
    return this == TIMESTAMP;
  }
}
