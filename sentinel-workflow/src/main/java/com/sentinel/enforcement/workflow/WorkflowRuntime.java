package com.sentinel.enforcement.workflow;

import com.sentinel.enforcement.application.workflow.CaseWorkflowPort;
import org.camunda.bpm.engine.ProcessEngine;

public final class WorkflowRuntime implements AutoCloseable {
  private final ProcessEngine processEngine;
  private final CaseWorkflowPort caseWorkflowPort;

  WorkflowRuntime(ProcessEngine processEngine, CaseWorkflowPort caseWorkflowPort) {
    this.processEngine = processEngine;
    this.caseWorkflowPort = caseWorkflowPort;
  }

  public CaseWorkflowPort caseWorkflowPort() {
    return caseWorkflowPort;
  }

  @Override
  public void close() {
    processEngine.close();
  }
}
