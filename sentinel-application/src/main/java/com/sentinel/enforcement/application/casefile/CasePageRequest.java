package com.sentinel.enforcement.application.casefile;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CasePageRequest(
    Set<String> jurisdictionCodes,
    String assigneeUserId,
    Instant cursorCreatedAt,
    UUID cursorId,
    int limitPlusOne) {

  public CasePageRequest {
    jurisdictionCodes = Set.copyOf(jurisdictionCodes);
    if (jurisdictionCodes.isEmpty()) {
      throw new IllegalArgumentException("jurisdictionCodes must not be empty");
    }
    if (limitPlusOne < 2) {
      throw new IllegalArgumentException("limitPlusOne must be at least 2");
    }
  }
}
