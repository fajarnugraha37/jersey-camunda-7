package com.sentinel.enforcement.application.workflow;

import java.time.Instant;
import java.util.UUID;

public record WorkflowHistoricProcessSnapshot(
    UUID caseId,
    String processInstanceId,
    String processDefinitionId,
    int processDefinitionVersion,
    String businessKey,
    Instant endedAt) {}
