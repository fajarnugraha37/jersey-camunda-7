package com.sentinel.enforcement.application.workflow;

import com.sentinel.enforcement.application.casefile.SortDirection;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record ListWorkflowReconciliationIssuesQuery(
    String cursorValue,
    String cursorCaseId,
    int limit,
    String quickSearch,
    WorkflowReconciliationSearchField searchField,
    String searchValue,
    WorkflowReconciliationIssueType issueType,
    CaseStatus caseStatus,
    String workflowCorrelationStatus,
    WorkflowReconciliationSortBy sortBy,
    SortDirection sortDirection) {

  public ListWorkflowReconciliationIssuesQuery {
    if (limit < 1 || limit > 50) {
      throw new IllegalArgumentException("limit must be between 1 and 50");
    }
    if ((searchField == null) != (searchValue == null || searchValue.isBlank())) {
      throw new IllegalArgumentException(
          "searchField and searchValue must either both be provided or both be omitted");
    }
    workflowCorrelationStatus = normalizeNullable(workflowCorrelationStatus);
    if (workflowCorrelationStatus != null
        && !SetHolder.ALLOWED_CORRELATION_STATUSES.contains(workflowCorrelationStatus)) {
      throw new IllegalArgumentException("workflowCorrelationStatus is invalid");
    }
    Objects.requireNonNull(sortBy, "sortBy must not be null");
    Objects.requireNonNull(sortDirection, "sortDirection must not be null");
    if (cursorValue != null && cursorValue.isBlank()) {
      throw new IllegalArgumentException("cursorValue must not be blank");
    }
    if (cursorCaseId != null) {
      UUID.fromString(cursorCaseId);
    }
  }

  public String cursorScope() {
    return String.join(
        "|",
        normalizeNullable(quickSearch),
        searchField == null ? "" : searchField.name(),
        normalizeNullable(searchValue),
        issueType == null ? "" : issueType.name(),
        caseStatus == null ? "" : caseStatus.name(),
        normalizeNullable(workflowCorrelationStatus));
  }

  private static String normalizeNullable(String value) {
    return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  private static final class SetHolder {
    private static final java.util.Set<String> ALLOWED_CORRELATION_STATUSES =
        java.util.Set.of("active", "completed", "cancelled");
  }
}
