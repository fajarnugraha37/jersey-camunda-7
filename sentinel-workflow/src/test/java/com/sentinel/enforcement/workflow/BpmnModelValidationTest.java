package com.sentinel.enforcement.workflow;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Test;

class BpmnModelValidationTest {

  @Test
  void regulatoryEnforcementProcessContainsTheExpectedWorkflowStages() {
    try (InputStream inputStream =
        getClass().getClassLoader().getResourceAsStream("bpmn/regulatory-enforcement-case.bpmn")) {
      assertNotNull(inputStream);
      BpmnModelInstance modelInstance = Bpmn.readModelFromStream(inputStream);

      assertNotNull(modelInstance.getModelElementById("caseCreatedStartEvent"));
      assertNotNull(modelInstance.getModelElementById("preTriageValidationTask"));
      assertNotNull(modelInstance.getModelElementById("intakeValidGateway"));
      assertNotNull(modelInstance.getModelElementById("triageTask"));
      assertNotNull(modelInstance.getModelElementById("selectInvestigationTracksGateway"));
      assertNotNull(modelInstance.getModelElementById("investigationAndEvidenceSubProcess"));
      assertNotNull(modelInstance.getModelElementById("investigationTask"));
      assertNotNull(modelInstance.getModelElementById("requestExternalEvidenceTask"));
      assertNotNull(modelInstance.getModelElementById("externalEvidenceDeliveryModeGateway"));
      assertNotNull(modelInstance.getModelElementById("awaitExternalEvidenceTask"));
      assertNotNull(modelInstance.getModelElementById("simulateExternalEvidenceDeliveredTask"));
      assertNotNull(modelInstance.getModelElementById("legalAdvisoryTask"));
      assertNotNull(modelInstance.getModelElementById("financialAnalysisScriptTask"));
      assertNotNull(modelInstance.getModelElementById("evidenceSufficiencyRuleTask"));
      assertNotNull(modelInstance.getModelElementById("sufficientEvidenceConditionEvent"));
      assertNotNull(modelInstance.getModelElementById("investigationEscalationTimer"));
      assertNotNull(modelInstance.getModelElementById("recommendationReviewSubProcess"));
      assertNotNull(modelInstance.getModelElementById("reviewTask"));
      assertNotNull(modelInstance.getModelElementById("supervisorReviewTask"));
      assertNotNull(modelInstance.getModelElementById("decisionPublicationSubProcess"));
      assertNotNull(modelInstance.getModelElementById("decisionTask"));
      assertNotNull(modelInstance.getModelElementById("publishSanctionTransaction"));
      assertNotNull(modelInstance.getModelElementById("sendSanctionToRegistryTask"));
      assertNotNull(modelInstance.getModelElementById("registryAcknowledgmentModeGateway"));
      assertNotNull(modelInstance.getModelElementById("simulateRegistryAcknowledgmentTask"));
      assertNotNull(modelInstance.getModelElementById("awaitRegistryAcknowledgmentTask"));
      assertNotNull(modelInstance.getModelElementById("sendNotificationCommandTask"));
      assertNotNull(modelInstance.getModelElementById("notificationResultModeGateway"));
      assertNotNull(modelInstance.getModelElementById("simulateNotificationResultTask"));
      assertNotNull(modelInstance.getModelElementById("awaitNotificationResultTask"));
      assertNotNull(modelInstance.getModelElementById("appealWindowGateway"));
      assertNotNull(modelInstance.getModelElementById("appealFiledEvent"));
      assertNotNull(modelInstance.getModelElementById("awaitAppealResolutionEvent"));
      assertNotNull(modelInstance.getModelElementById("globalHoldCatchEvent"));
      assertNotNull(modelInstance.getModelElementById("enforcementMonitoringSubProcess"));
      assertNotNull(modelInstance.getModelElementById("supervisorOverrideEventSubProcess"));
      assertNotNull(modelInstance.getModelElementById("Participant_ExternalEvidenceProvider"));
      assertNotNull(modelInstance.getModelElementById("DataStoreReference_CaseDatabase"));
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to validate BPMN model.", exception);
    }
  }

  @Test
  void appealReviewProcessContainsTheExpectedWorkflowStages() {
    try (InputStream inputStream =
        getClass().getClassLoader().getResourceAsStream("bpmn/decision-appeal-review.bpmn")) {
      assertNotNull(inputStream);
      BpmnModelInstance modelInstance = Bpmn.readModelFromStream(inputStream);

      assertNotNull(modelInstance.getModelElementById("appealStartEvent"));
      assertNotNull(modelInstance.getModelElementById("appealReviewTask"));
      assertNotNull(modelInstance.getModelElementById("appealReviewReminderTimer"));
      assertNotNull(modelInstance.getModelElementById("appealDecisionOutcomeGateway"));
      assertNotNull(modelInstance.getModelElementById("appealGlobalHoldThrowEvent"));
      assertNotNull(modelInstance.getModelElementById("appealSupervisorOverrideEventSubProcess"));
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to validate appeal BPMN model.", exception);
    }
  }
}
