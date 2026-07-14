package com.sentinel.enforcement.application.casefile;

import java.util.UUID;

public record AuditEventPageRequest(
    UUID caseId,
    String cursorValue,
    UUID cursorId,
    String quickSearch,
    AuditEventListSearchField searchField,
    String searchValue,
    String actorId,
    String eventType,
    String action,
    String result,
    AuditEventListSortBy sortBy,
    SortDirection sortDirection,
    int limitPlusOne) {

  public AuditEventPageRequest {
    quickSearch = normalize(quickSearch);
    searchValue = normalize(searchValue);
    actorId = normalize(actorId);
    eventType = normalize(eventType);
    action = normalize(action);
    result = normalize(result);
    if (caseId == null) {
      throw new IllegalArgumentException("caseId must not be null");
    }
    if ((cursorValue == null) != (cursorId == null)) {
      throw new IllegalArgumentException("cursorValue and cursorId must both be present");
    }
    if ((searchField == null) != (searchValue == null)) {
      throw new IllegalArgumentException("searchField and searchValue must both be present");
    }
    if (sortBy == null) {
      throw new IllegalArgumentException("sortBy must not be null");
    }
    if (sortDirection == null) {
      throw new IllegalArgumentException("sortDirection must not be null");
    }
    if (limitPlusOne < 2) {
      throw new IllegalArgumentException("limitPlusOne must be at least 2");
    }
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
