package com.sentinel.enforcement.workflow;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;

public final class CamundaServices {
  private final ProcessEngineProvider processEngineProvider;

  CamundaServices(ProcessEngineProvider processEngineProvider) {
    this.processEngineProvider = processEngineProvider;
  }

  public RuntimeService runtimeService() {
    return processEngineProvider.get().getRuntimeService();
  }

  public HistoryService historyService() {
    return processEngineProvider.get().getHistoryService();
  }

  public TaskService taskService() {
    return processEngineProvider.get().getTaskService();
  }

  public RepositoryService repositoryService() {
    return processEngineProvider.get().getRepositoryService();
  }

  public ManagementService managementService() {
    return processEngineProvider.get().getManagementService();
  }
}
