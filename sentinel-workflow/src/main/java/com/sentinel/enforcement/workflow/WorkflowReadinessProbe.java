package com.sentinel.enforcement.workflow;

final class WorkflowReadinessProbe {
  private final ProcessEngineProvider processEngineProvider;
  private final CamundaServices camundaServices;
  private final String processDefinitionKey;

  WorkflowReadinessProbe(
      ProcessEngineProvider processEngineProvider,
      CamundaServices camundaServices,
      String processDefinitionKey) {
    this.processEngineProvider = processEngineProvider;
    this.camundaServices = camundaServices;
    this.processDefinitionKey = processDefinitionKey;
  }

  boolean isReady() {
    try {
      if (processEngineProvider.get().getName() == null
          || processEngineProvider.get().getName().isBlank()) {
        return false;
      }
      if (camundaServices.managementService().getTableCount().isEmpty()) {
        return false;
      }
      return camundaServices
              .repositoryService()
              .createProcessDefinitionQuery()
              .processDefinitionKey(processDefinitionKey)
              .latestVersion()
              .count()
          > 0;
    } catch (RuntimeException exception) {
      return false;
    }
  }
}
