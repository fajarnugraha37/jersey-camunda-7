package com.sentinel.enforcement.domain.report;

public final class ReportConflictException extends RuntimeException {
  private final String code;

  public ReportConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
