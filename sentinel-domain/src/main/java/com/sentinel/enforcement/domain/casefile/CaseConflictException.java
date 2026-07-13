package com.sentinel.enforcement.domain.casefile;

public final class CaseConflictException extends RuntimeException {
  private final String code;

  public CaseConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
