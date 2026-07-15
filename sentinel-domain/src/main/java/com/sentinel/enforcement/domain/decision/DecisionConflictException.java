package com.sentinel.enforcement.domain.decision;

public final class DecisionConflictException extends RuntimeException {
  private final String code;

  public DecisionConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
