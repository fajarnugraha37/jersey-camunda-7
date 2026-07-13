package com.sentinel.enforcement.application.casefile;

import com.sentinel.enforcement.domain.casefile.CaseRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CasePage(
    List<CaseRecord> items, Instant nextCursorCreatedAt, UUID nextCursorId, boolean hasNextPage) {

  public CasePage {
    items = List.copyOf(items);
    if (hasNextPage && (nextCursorCreatedAt == null || nextCursorId == null)) {
      throw new IllegalArgumentException(
          "next cursor fields must be present when hasNextPage is true");
    }
    if (!hasNextPage && (nextCursorCreatedAt != null || nextCursorId != null)) {
      throw new IllegalArgumentException(
          "next cursor fields must be absent when hasNextPage is false");
    }
  }
}
