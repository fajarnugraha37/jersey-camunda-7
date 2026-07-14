package com.sentinel.enforcement.application.workflow;

import java.util.UUID;

public record WorkflowReconciliationActionResult(
    UUID caseId,
    WorkflowReconciliationAction action,
    WorkflowReconciliationActionResultStatus result,
    WorkflowReconciliationIssueType issueType,
    String detail,
    String workflowCorrelationStatus,
    String processInstanceId) {}
