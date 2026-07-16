package com.sentinel.enforcement.workflow;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

final class MockWorkflowServiceDelegate implements JavaDelegate {

  @Override
  public void execute(DelegateExecution execution) {
    String activityId = execution.getCurrentActivityId();
    if ("createPublicationPackageTask".equals(activityId)) {
      execution.setVariable("publicationPackageCreated", true);
      return;
    }
    if ("evidenceSufficiencyRuleTask".equals(activityId)) {
      execution.setVariable("evidenceSufficient", true);
      return;
    }
    if ("determineSanctionPackageTask".equals(activityId)) {
      execution.setVariable("sanctionPackageDetermined", true);
      return;
    }
    if ("createObligationScheduleTask".equals(activityId)) {
      execution.setVariable("obligationScheduleCreated", true);
      return;
    }
    if ("simulateExternalEvidenceDeliveredTask".equals(activityId)) {
      execution.setVariable("externalEvidenceDelivered", true);
      return;
    }
    if ("simulateRegistryAcknowledgmentTask".equals(activityId)) {
      execution.setVariable("registryAcknowledgmentFailed", false);
      return;
    }
    if ("simulateNotificationResultTask".equals(activityId)) {
      execution.setVariable("notificationResultFailed", false);
      return;
    }
    if ("markManualNotificationRequiredTask".equals(activityId)) {
      execution.setVariable("manualNotificationRequired", true);
    }
  }
}
