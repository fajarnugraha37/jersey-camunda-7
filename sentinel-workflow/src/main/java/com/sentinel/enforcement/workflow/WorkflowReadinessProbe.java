package com.sentinel.enforcement.workflow;

final class WorkflowReadinessProbe {
  private final ProcessEngineProvider processEngineProvider;
  private final CamundaServices camundaServices;
  private final String[] processDefinitionKeys;

  WorkflowReadinessProbe(
      ProcessEngineProvider processEngineProvider,
      CamundaServices camundaServices,
      String... processDefinitionKeys) {
    this.processEngineProvider = processEngineProvider;
    this.camundaServices = camundaServices;
    this.processDefinitionKeys = processDefinitionKeys;
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
      for (String processDefinitionKey : processDefinitionKeys) {
        long deployedCount =
            camundaServices
                .repositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionKey(processDefinitionKey)
                .latestVersion()
                .count();
        if (deployedCount == 0) {
          return false;
        }
      }
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }
}
