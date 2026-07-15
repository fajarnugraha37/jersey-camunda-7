package com.sentinel.enforcement.application.decision;

import java.util.UUID;

public final class DecisionNotFoundException extends RuntimeException {
  public DecisionNotFoundException(UUID decisionId) {
    super("Decision " + decisionId + " was not found.");
  }
}
