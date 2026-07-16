package com.sentinel.enforcement.workflow;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

final class PreTriageRoutingDelegate implements JavaDelegate {

  @Override
  public void execute(DelegateExecution execution) {
    defaultBoolean(execution, "intakeRejected", false);
    defaultBoolean(execution, "requiresExternalEvidence", false);
    defaultBoolean(execution, "requiresLegalAdvisory", false);
    defaultBoolean(execution, "requiresFinancialAnalysis", false);
    defaultBoolean(execution, "requiresLegalAdvisoryTrack", false);
    defaultBoolean(execution, "requiresFinancialAnalysisTrack", false);
    defaultBoolean(execution, "autoExternalEvidenceDelivery", true);
    defaultBoolean(execution, "evidenceSufficient", false);
    defaultBoolean(execution, "additionalEvidenceRequired", false);
    defaultBoolean(execution, "requiresSupervisorReview", false);
    defaultBoolean(execution, "reviewRequiresRevision", false);
    defaultBoolean(execution, "sanctionPublicationRequired", false);
    defaultBoolean(execution, "autoRegistryAcknowledgment", true);
    defaultBoolean(execution, "registryAcknowledgmentFailed", false);
    defaultBoolean(execution, "autoNotificationResult", true);
    defaultBoolean(execution, "notificationResultFailed", false);
    defaultBoolean(execution, "abortPublicationFinalization", false);
    defaultBoolean(execution, "enforcementMonitoringRequired", false);
    defaultBoolean(execution, "allObligationsComplete", false);
    defaultBoolean(execution, "obligationBreachDetected", false);
    defaultBoolean(execution, "overrideCancel", false);
    defaultBoolean(execution, "overrideSuspend", false);
  }

  private static void defaultBoolean(
      DelegateExecution execution, String variableName, boolean defaultValue) {
    if (!execution.hasVariable(variableName)) {
      execution.setVariable(variableName, defaultValue);
    }
  }
}
