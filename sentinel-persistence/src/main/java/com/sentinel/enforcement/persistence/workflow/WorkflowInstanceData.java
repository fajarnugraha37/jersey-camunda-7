package com.sentinel.enforcement.persistence.workflow;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkflowInstanceData(
    UUID caseId,
    String processInstanceId,
    String processDefinitionId,
    int processDefinitionVersion,
    String businessKey,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
