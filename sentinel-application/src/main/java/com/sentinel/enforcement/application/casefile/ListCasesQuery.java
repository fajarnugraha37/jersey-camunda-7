package com.sentinel.enforcement.application.casefile;

import java.time.Instant;
import java.util.UUID;

public record ListCasesQuery(Instant cursorCreatedAt, UUID cursorId, int limit) {

  public ListCasesQuery {
    if (limit < 1 || limit > 50) {
      throw new IllegalArgumentException("limit must be between 1 and 50");
    }
    if ((cursorCreatedAt == null) != (cursorId == null)) {
      throw new IllegalArgumentException("cursorCreatedAt and cursorId must both be present");
    }
  }
}
