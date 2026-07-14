package com.sentinel.enforcement.application.casefile;

import com.sentinel.enforcement.domain.casefile.AuditEvent;
import java.util.List;
import java.util.UUID;

public record AuditEventPage(
    List<AuditEvent> items, String nextCursorValue, UUID nextCursorId, boolean hasNextPage) {

  public AuditEventPage {
    items = List.copyOf(items);
    if (hasNextPage && (nextCursorValue == null || nextCursorId == null)) {
      throw new IllegalArgumentException(
          "next cursor fields must be present when hasNextPage is true");
    }
    if (!hasNextPage && (nextCursorValue != null || nextCursorId != null)) {
      throw new IllegalArgumentException(
          "next cursor fields must be absent when hasNextPage is false");
    }
  }
}
