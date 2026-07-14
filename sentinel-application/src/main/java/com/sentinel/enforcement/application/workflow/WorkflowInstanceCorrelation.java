package com.sentinel.enforcement.application.workflow;

import java.util.UUID;

public record WorkflowInstanceCorrelation(
    UUID caseId,
    String processInstanceId,
    String processDefinitionId,
    int processDefinitionVersion,
    String businessKey,
    String status) {}
