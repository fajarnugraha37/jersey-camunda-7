package com.sentinel.enforcement.workflow;

import com.sentinel.enforcement.application.casefile.CaseRepository;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

final class InvestigationEscalationDelegate implements JavaDelegate {
  private static final String RESOURCE_TYPE = "CASE";

  private final CaseRepository caseRepository;
  private final Clock clock;

  InvestigationEscalationDelegate(CaseRepository caseRepository, Clock clock) {
    this.caseRepository = caseRepository;
    this.clock = clock;
  }

  @Override
  public void execute(DelegateExecution execution) {
    UUID caseId = UUID.fromString(stringVariable(execution, "caseId"));
    CaseRecord caseRecord = caseRepository.findById(caseId).orElse(null);
    if (caseRecord == null) {
      return;
    }

    Instant timestamp = clock.instant();
    String processInstanceId = execution.getProcessInstanceId();
    String escalationDuration = stringVariable(execution, "investigationEscalationDuration");
    UUID eventId =
        UUID.nameUUIDFromBytes(
            (processInstanceId + ":investigationEscalation").getBytes(StandardCharsets.UTF_8));
    AuditEvent auditEvent =
        new AuditEvent(
            eventId,
            "WorkflowInvestigationEscalated",
            "SYSTEM",
            "camunda",
            "SYSTEM",
            "WORKFLOW_TIMER_ESCALATED",
            RESOURCE_TYPE,
            caseId.toString(),
            caseId,
            timestamp,
            processInstanceId,
            null,
            "SUCCESS",
            "Investigation task exceeded the configured escalation timer.",
            caseRecord.auditSummary(),
            caseRecord.auditSummary(),
            "taskDefinitionKey=investigationTask;duration="
                + escalationDuration
                + ";processInstanceId="
                + processInstanceId);
    caseRepository.appendAuditEvent(auditEvent);
  }

  private String stringVariable(DelegateExecution execution, String variableName) {
    Object variableValue = execution.getVariable(variableName);
    if (variableValue instanceof String stringValue && !stringValue.isBlank()) {
      return stringValue;
    }
    throw new IllegalStateException("Workflow variable " + variableName + " is missing.");
  }
}
