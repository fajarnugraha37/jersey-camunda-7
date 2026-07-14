package com.sentinel.enforcement.application.workflow;

import com.sentinel.enforcement.domain.casefile.CaseStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowReconciliationView(
    UUID caseId,
    String caseNumber,
    String caseTitle,
    CaseStatus caseStatus,
    String jurisdictionCode,
    String assigneeUserId,
    Instant caseUpdatedAt,
    WorkflowReconciliationIssueType issueType,
    String detail,
    String workflowCorrelationStatus,
    String correlationProcessInstanceId,
    String runtimeProcessInstanceId,
    List<WorkflowReconciliationAction> availableActions) {}
