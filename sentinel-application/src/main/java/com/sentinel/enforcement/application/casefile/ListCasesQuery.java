package com.sentinel.enforcement.application.casefile;

import com.sentinel.enforcement.domain.casefile.CaseClassification;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import java.util.Objects;
import java.util.UUID;

public record ListCasesQuery(
    String cursorValue,
    UUID cursorId,
    int limit,
    String quickSearch,
    CaseListSearchField searchField,
    String searchValue,
    CaseStatus status,
    CaseClassification classification,
    String assignedUnitId,
    String assigneeUserId,
    String createdBy,
    UUID reportId,
    CaseListSortBy sortBy,
    SortDirection sortDirection) {

  public ListCasesQuery {
    cursorValue = normalize(cursorValue);
    quickSearch = normalize(quickSearch);
    searchValue = normalize(searchValue);
    assignedUnitId = normalize(assignedUnitId);
    assigneeUserId = normalize(assigneeUserId);
    createdBy = normalize(createdBy);
    Objects.requireNonNull(sortBy, "sortBy must not be null");
    Objects.requireNonNull(sortDirection, "sortDirection must not be null");
    if (limit < 1 || limit > 50) {
      throw new IllegalArgumentException("limit must be between 1 and 50");
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
        + ";status="
        + enumOrEmpty(status)
        + ";classification="
        + enumOrEmpty(classification)
        + ";assignedUnitId="
        + valueOrEmpty(assignedUnitId)
        + ";assigneeUserId="
        + valueOrEmpty(assigneeUserId)
        + ";createdBy="
        + valueOrEmpty(createdBy)
        + ";reportId="
        + uuidOrEmpty(reportId);
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

  private static String uuidOrEmpty(UUID value) {
    return value == null ? "" : value.toString();
  }
}
