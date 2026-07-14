package com.sentinel.enforcement.workflow;

import com.sentinel.enforcement.application.workflow.CaseWorkflowPort;
import com.sentinel.enforcement.application.workflow.WorkflowAdministrationPort;

public final class WorkflowRuntime implements AutoCloseable {
  private final ProcessEngineProvider processEngineProvider;
  private final CaseWorkflowPort caseWorkflowPort;
  private final WorkflowAdministrationPort workflowAdministrationPort;
  private final WorkflowReadinessProbe workflowReadinessProbe;

  WorkflowRuntime(
      ProcessEngineProvider processEngineProvider,
      CaseWorkflowPort caseWorkflowPort,
      WorkflowAdministrationPort workflowAdministrationPort,
      WorkflowReadinessProbe workflowReadinessProbe) {
    this.processEngineProvider = processEngineProvider;
    this.caseWorkflowPort = caseWorkflowPort;
    this.workflowAdministrationPort = workflowAdministrationPort;
    this.workflowReadinessProbe = workflowReadinessProbe;
  }

  public CaseWorkflowPort caseWorkflowPort() {
    return caseWorkflowPort;
  }

  public WorkflowAdministrationPort workflowAdministrationPort() {
    return workflowAdministrationPort;
  }

  public boolean isReady() {
    return workflowReadinessProbe.isReady();
  }

  @Override
  public void close() {
    processEngineProvider.close();
  }
}
