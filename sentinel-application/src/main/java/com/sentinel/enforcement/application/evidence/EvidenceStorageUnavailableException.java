package com.sentinel.enforcement.application.evidence;

public final class EvidenceStorageUnavailableException extends RuntimeException {
  public EvidenceStorageUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
