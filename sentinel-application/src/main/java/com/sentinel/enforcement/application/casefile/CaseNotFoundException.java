package com.sentinel.enforcement.application.casefile;

import java.util.UUID;

public final class CaseNotFoundException extends RuntimeException {
  public CaseNotFoundException(UUID caseId) {
    super("Case " + caseId + " was not found.");
  }
}
