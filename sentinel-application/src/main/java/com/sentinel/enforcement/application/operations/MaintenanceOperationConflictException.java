package com.sentinel.enforcement.application.operations;

public final class MaintenanceOperationConflictException extends RuntimeException {
  private final String code;

  public MaintenanceOperationConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
