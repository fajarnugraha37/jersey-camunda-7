package com.sentinel.enforcement.application.workflow;

import com.sentinel.enforcement.domain.casefile.CaseStatus;
import java.time.Instant;
import java.util.UUID;

public record WorkflowReconciliationCandidate(
    UUID caseId,
    String caseNumber,
    String caseTitle,
    CaseStatus caseStatus,
    String jurisdictionCode,
    String assigneeUserId,
    Instant caseUpdatedAt,
    WorkflowInstanceCorrelation workflowInstanceCorrelation) {}
