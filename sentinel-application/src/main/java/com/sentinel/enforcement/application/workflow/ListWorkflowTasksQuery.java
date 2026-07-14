package com.sentinel.enforcement.application.workflow;

import com.sentinel.enforcement.application.casefile.SortDirection;
import java.util.Objects;
import java.util.UUID;

public record ListWorkflowTasksQuery(
    String cursorValue,
    String cursorTaskId,
    int limit,
    String quickSearch,
    WorkflowTaskSearchField searchField,
    String searchValue,
    UUID caseId,
    String assigneeUserId,
    WorkflowTaskState state,
    WorkflowTaskSortBy sortBy,
    SortDirection sortDirection) {

  public ListWorkflowTasksQuery {
    cursorValue = normalize(cursorValue);
    cursorTaskId = normalize(cursorTaskId);
    quickSearch = normalize(quickSearch);
    searchValue = normalize(searchValue);
    assigneeUserId = normalize(assigneeUserId);
    Objects.requireNonNull(sortBy, "sortBy must not be null");
    Objects.requireNonNull(sortDirection, "sortDirection must not be null");
    if (limit < 1 || limit > 50) {
      throw new IllegalArgumentException("limit must be between 1 and 50");
    }
    if ((cursorValue == null) != (cursorTaskId == null)) {
      throw new IllegalArgumentException("cursorValue and cursorTaskId must both be present");
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
        + ";caseId="
        + uuidOrEmpty(caseId)
        + ";assigneeUserId="
        + valueOrEmpty(assigneeUserId)
        + ";state="
        + enumOrEmpty(state);
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
