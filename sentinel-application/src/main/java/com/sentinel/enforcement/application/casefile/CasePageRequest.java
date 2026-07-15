package com.sentinel.enforcement.application.casefile;

import com.sentinel.enforcement.domain.casefile.CaseClassification;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import java.util.Set;
import java.util.UUID;

public record CasePageRequest(
    Set<String> jurisdictionCodes,
    String restrictedAssigneeUserId,
    Set<String> restrictedAssignedUnitIds,
    boolean includeUnassignedWhenUnitRestricted,
    Set<CaseClassification> allowedClassifications,
    Set<String> excludedCreatedByUserIds,
    String requestedAssigneeUserId,
    String cursorValue,
    UUID cursorId,
    String quickSearch,
    CaseListSearchField searchField,
    String searchValue,
    CaseStatus status,
    CaseClassification classification,
    String assignedUnitId,
    String createdBy,
    UUID reportId,
    CaseListSortBy sortBy,
    SortDirection sortDirection,
    int limitPlusOne) {

  public CasePageRequest {
    jurisdictionCodes = Set.copyOf(jurisdictionCodes);
    restrictedAssignedUnitIds = Set.copyOf(restrictedAssignedUnitIds);
    allowedClassifications = Set.copyOf(allowedClassifications);
    excludedCreatedByUserIds = Set.copyOf(excludedCreatedByUserIds);
    cursorValue = normalize(cursorValue);
    quickSearch = normalize(quickSearch);
    searchValue = normalize(searchValue);
    restrictedAssigneeUserId = normalize(restrictedAssigneeUserId);
    requestedAssigneeUserId = normalize(requestedAssigneeUserId);
    assignedUnitId = normalize(assignedUnitId);
    createdBy = normalize(createdBy);
    if (jurisdictionCodes.isEmpty()) {
      throw new IllegalArgumentException("jurisdictionCodes must not be empty");
    }
    if (allowedClassifications.isEmpty()) {
      throw new IllegalArgumentException("allowedClassifications must not be empty");
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
