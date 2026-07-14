package com.sentinel.enforcement.application.workflow;

import java.util.List;

public record WorkflowReconciliationPage(
    List<WorkflowReconciliationView> items,
    String nextCursorValue,
    String nextCursorCaseId,
    boolean hasNextPage) {}
