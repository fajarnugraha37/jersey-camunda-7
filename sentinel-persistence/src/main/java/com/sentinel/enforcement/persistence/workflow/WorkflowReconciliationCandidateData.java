package com.sentinel.enforcement.persistence.workflow;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkflowReconciliationCandidateData(
    UUID caseId,
    String caseNumber,
    String caseTitle,
    String caseStatus,
    String jurisdictionCode,
    String assigneeUserId,
    OffsetDateTime caseUpdatedAt,
    String correlationProcessInstanceId,
    String correlationProcessDefinitionId,
    Integer correlationProcessDefinitionVersion,
    String correlationBusinessKey,
    String correlationStatus) {}
