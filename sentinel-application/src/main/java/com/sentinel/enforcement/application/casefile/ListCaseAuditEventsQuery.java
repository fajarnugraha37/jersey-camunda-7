package com.sentinel.enforcement.application.casefile;

import java.util.Objects;
import java.util.UUID;

public record ListCaseAuditEventsQuery(
    String cursorValue,
    UUID cursorId,
    int limit,
    String quickSearch,
    AuditEventListSearchField searchField,
    String searchValue,
    String actorId,
    String eventType,
    String action,
    String result,
    AuditEventListSortBy sortBy,
    SortDirection sortDirection) {

  public ListCaseAuditEventsQuery {
    cursorValue = normalize(cursorValue);
    quickSearch = normalize(quickSearch);
    searchValue = normalize(searchValue);
    actorId = normalize(actorId);
    eventType = normalize(eventType);
    action = normalize(action);
    result = normalize(result);
    Objects.requireNonNull(sortBy, "sortBy must not be null");
    Objects.requireNonNull(sortDirection, "sortDirection must not be null");
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("limit must be between 1 and 100");
    }
    if ((cursorValue == null) != (cursorId == null)) {
      throw new IllegalArgumentException("cursorValue and cursorId must both be present");
    }
    if ((searchField == null) != (searchValue == null)) {
      throw new IllegalArgumentException("searchField and searchValue must both be present");
    }
  }

  public String cursorScope() {
    return "q="
        + valueOrEmpty(quickSearch)
        + ";sf="
        + enumOrEmpty(searchField)
        + ";sv="
        + valueOrEmpty(searchValue)
        + ";actorId="
        + valueOrEmpty(actorId)
        + ";eventType="
        + valueOrEmpty(eventType)
        + ";action="
        + valueOrEmpty(action)
        + ";result="
        + valueOrEmpty(result);
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String enumOrEmpty(Enum<?> value) {
    return value == null ? "" : value.name();
  }
}
