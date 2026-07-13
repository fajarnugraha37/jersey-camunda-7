package com.sentinel.enforcement.domain.casefile;

public enum CaseStatus {
  CREATED,
  UNDER_TRIAGE,
  UNDER_INVESTIGATION,
  PENDING_REVIEW,
  PENDING_DECISION,
  DECIDED,
  UNDER_APPEAL,
  ENFORCEMENT_IN_PROGRESS,
  CLOSED,
  CANCELLED;

  public boolean isTerminal() {
    return this == CLOSED || this == CANCELLED;
  }
}
