package com.sentinel.enforcement.persistence.workflow;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkflowReconciliationIssueData(
    UUID caseId,
    String caseNumber,
    String caseTitle,
    String caseStatus,
    String jurisdictionCode,
    String assigneeUserId,
    OffsetDateTime caseUpdatedAt,
    String issueType,
    String detail,
    String workflowCorrelationStatus,
    String correlationProcessInstanceId,
    String runtimeProcessInstanceId,
    String availableActionsCsv) {}
