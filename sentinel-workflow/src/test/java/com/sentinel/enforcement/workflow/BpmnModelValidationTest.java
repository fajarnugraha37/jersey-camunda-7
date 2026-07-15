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

      assertNotNull(modelInstance.getModelElementById("triageTask"));
      assertNotNull(modelInstance.getModelElementById("investigationTask"));
      assertNotNull(modelInstance.getModelElementById("investigationEscalationTimer"));
      assertNotNull(modelInstance.getModelElementById("reviewTask"));
      assertNotNull(modelInstance.getModelElementById("decisionTask"));
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

      assertNotNull(modelInstance.getModelElementById("appealReviewTask"));
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to validate appeal BPMN model.", exception);
    }
  }
}
