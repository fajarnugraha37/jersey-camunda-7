package com.sentinel.enforcement.domain.appeal;

public final class AppealConflictException extends RuntimeException {
  private final String code;

  public AppealConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
