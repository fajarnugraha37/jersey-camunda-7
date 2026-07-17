package com.sentinel.enforcement.persistence.workflow;

import java.time.OffsetDateTime;

public record WorkflowReconciliationIssueQueryData(
    String quickSearchPattern,
    String searchField,
    String searchPattern,
    String issueType,
    String caseStatus,
    String workflowCorrelationStatus,
    String sortBy,
    String sortDirection,
    OffsetDateTime cursorTimestampValue,
    String cursorTextValue,
    String cursorCaseId,
    int limitPlusOne) {}
