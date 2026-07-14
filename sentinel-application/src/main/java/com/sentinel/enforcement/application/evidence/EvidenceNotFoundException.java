package com.sentinel.enforcement.application.evidence;

import java.util.UUID;

public final class EvidenceNotFoundException extends RuntimeException {
  public EvidenceNotFoundException(UUID evidenceId) {
    super("Evidence " + evidenceId + " was not found.");
  }
}
