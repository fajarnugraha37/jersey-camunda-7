package com.sentinel.enforcement.application.evidence;

public final class EvidenceObjectMissingException extends RuntimeException {
  public EvidenceObjectMissingException(String bucket, String objectKey) {
    super("Evidence object " + objectKey + " is missing from bucket " + bucket + ".");
  }
}
