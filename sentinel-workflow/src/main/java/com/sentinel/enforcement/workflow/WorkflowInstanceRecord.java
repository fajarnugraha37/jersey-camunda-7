package com.sentinel.enforcement.workflow;

import java.util.UUID;

record WorkflowInstanceRecord(
    UUID caseId,
    String processInstanceId,
    String processDefinitionId,
    int processDefinitionVersion,
    String businessKey,
    String status) {}
