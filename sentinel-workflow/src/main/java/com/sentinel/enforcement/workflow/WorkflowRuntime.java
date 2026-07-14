package com.sentinel.enforcement.workflow;

import com.sentinel.enforcement.application.workflow.CaseWorkflowPort;

public final class WorkflowRuntime implements AutoCloseable {
  private final ProcessEngineProvider processEngineProvider;
  private final CaseWorkflowPort caseWorkflowPort;
  private final WorkflowReadinessProbe workflowReadinessProbe;

  WorkflowRuntime(
      ProcessEngineProvider processEngineProvider,
      CaseWorkflowPort caseWorkflowPort,
      WorkflowReadinessProbe workflowReadinessProbe) {
    this.processEngineProvider = processEngineProvider;
    this.caseWorkflowPort = caseWorkflowPort;
    this.workflowReadinessProbe = workflowReadinessProbe;
  }

  public CaseWorkflowPort caseWorkflowPort() {
    return caseWorkflowPort;
  }

  public boolean isReady() {
    return workflowReadinessProbe.isReady();
  }

  @Override
  public void close() {
    processEngineProvider.close();
  }
}
