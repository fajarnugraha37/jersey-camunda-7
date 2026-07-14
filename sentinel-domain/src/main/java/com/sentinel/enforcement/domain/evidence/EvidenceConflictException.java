package com.sentinel.enforcement.domain.evidence;

public final class EvidenceConflictException extends RuntimeException {
  private final String code;

  public EvidenceConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
