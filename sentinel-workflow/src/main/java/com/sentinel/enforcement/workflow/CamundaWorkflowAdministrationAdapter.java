package com.sentinel.enforcement.workflow;

import com.sentinel.enforcement.application.workflow.WorkflowAdministrationPort;
import com.sentinel.enforcement.application.workflow.WorkflowHistoricProcessSnapshot;
import com.sentinel.enforcement.application.workflow.WorkflowProcessSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ProcessInstance;

final class CamundaWorkflowAdministrationAdapter implements WorkflowAdministrationPort {
  private final CamundaServices camundaServices;

  CamundaWorkflowAdministrationAdapter(CamundaServices camundaServices) {
    this.camundaServices = camundaServices;
  }

  @Override
  public List<WorkflowProcessSnapshot> listActiveProcessInstances() {
    return camundaServices.runtimeService().createProcessInstanceQuery().active().list().stream()
        .map(this::toRuntimeSnapshot)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  @Override
  public Optional<WorkflowProcessSnapshot> findActiveProcessInstance(UUID caseId) {
    ProcessInstance processInstance =
        camundaServices
            .runtimeService()
            .createProcessInstanceQuery()
            .processInstanceBusinessKey(caseId.toString())
            .active()
            .singleResult();
    if (processInstance == null) {
      return Optional.empty();
    }
    return toRuntimeSnapshot(processInstance);
  }

  @Override
  public Optional<WorkflowHistoricProcessSnapshot> findLatestFinishedProcessInstance(UUID caseId) {
    HistoricProcessInstance historicProcessInstance =
        camundaServices
            .historyService()
            .createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(caseId.toString())
            .finished()
            .orderByProcessInstanceEndTime()
            .desc()
            .listPage(0, 1)
            .stream()
            .findFirst()
            .orElse(null);
    if (historicProcessInstance == null) {
      return Optional.empty();
    }
    return Optional.of(toHistoricSnapshot(caseId, historicProcessInstance));
  }

  @Override
  public void terminateActiveProcessInstance(UUID caseId, String reason) {
    findActiveProcessInstance(caseId)
        .ifPresent(
            snapshot ->
                camundaServices
                    .runtimeService()
                    .deleteProcessInstance(snapshot.processInstanceId(), reason));
  }

  private Optional<WorkflowProcessSnapshot> toRuntimeSnapshot(ProcessInstance processInstance) {
    try {
      UUID caseId = UUID.fromString(processInstance.getBusinessKey());
      ProcessDefinition processDefinition =
          camundaServices
              .repositoryService()
              .getProcessDefinition(processInstance.getProcessDefinitionId());
      return Optional.of(
          new WorkflowProcessSnapshot(
              caseId,
              processInstance.getProcessInstanceId(),
              processDefinition.getId(),
              processDefinition.getVersion(),
              processInstance.getBusinessKey()));
    } catch (RuntimeException exception) {
      return Optional.empty();
    }
  }

  private WorkflowHistoricProcessSnapshot toHistoricSnapshot(
      UUID caseId, HistoricProcessInstance historicProcessInstance) {
    ProcessDefinition processDefinition =
        camundaServices
            .repositoryService()
            .getProcessDefinition(historicProcessInstance.getProcessDefinitionId());
    return new WorkflowHistoricProcessSnapshot(
        caseId,
        historicProcessInstance.getId(),
        processDefinition.getId(),
        processDefinition.getVersion(),
        historicProcessInstance.getBusinessKey(),
        historicProcessInstance.getEndTime().toInstant());
  }
}
