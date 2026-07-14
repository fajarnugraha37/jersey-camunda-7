package com.sentinel.enforcement.workflow;

import com.sentinel.enforcement.application.casefile.CaseRepository;
import com.sentinel.enforcement.application.workflow.CaseWorkflowPort;
import com.sentinel.enforcement.application.workflow.StartedWorkflowInstance;
import com.sentinel.enforcement.application.workflow.WorkflowTaskConflictException;
import com.sentinel.enforcement.application.workflow.WorkflowTaskNotFoundException;
import com.sentinel.enforcement.application.workflow.WorkflowTaskState;
import com.sentinel.enforcement.application.workflow.WorkflowTaskView;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;

final class CamundaCaseWorkflowAdapter implements CaseWorkflowPort {
  private final CamundaServices camundaServices;
  private final WorkflowInstanceJdbcStore workflowInstanceStore;
  private final CaseRepository caseRepository;
  private final Clock clock;

  CamundaCaseWorkflowAdapter(
      CamundaServices camundaServices,
      WorkflowInstanceJdbcStore workflowInstanceStore,
      CaseRepository caseRepository,
      Clock clock) {
    this.camundaServices = camundaServices;
    this.workflowInstanceStore = workflowInstanceStore;
    this.caseRepository = caseRepository;
    this.clock = clock;
  }

  @Override
  public StartedWorkflowInstance startCaseWorkflow(
      UUID caseId,
      String jurisdictionCode,
      String caseNumber,
      String caseTitle,
      Duration investigationEscalationDuration,
      String startedBy) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("caseId", caseId.toString());
    variables.put("jurisdictionCode", jurisdictionCode);
    variables.put("caseNumber", caseNumber);
    variables.put("caseTitle", caseTitle);
    variables.put("startedBy", startedBy);
    variables.put("investigationEscalationDuration", investigationEscalationDuration.toString());

    ProcessInstance processInstance =
        camundaServices
            .runtimeService()
            .startProcessInstanceByKey(
                WorkflowModule.PROCESS_DEFINITION_KEY, caseId.toString(), variables);
    ProcessDefinition processDefinition =
        camundaServices
            .repositoryService()
            .getProcessDefinition(processInstance.getProcessDefinitionId());
    StartedWorkflowInstance startedWorkflow =
        new StartedWorkflowInstance(
            caseId,
            processInstance.getProcessInstanceId(),
            processDefinition.getId(),
            processDefinition.getVersion(),
            processInstance.getBusinessKey());
    try {
      workflowInstanceStore.saveStarted(startedWorkflow, clock.instant());
      return startedWorkflow;
    } catch (RuntimeException exception) {
      camundaServices
          .runtimeService()
          .deleteProcessInstance(
              processInstance.getProcessInstanceId(),
              "Workflow correlation persistence failed during case creation.");
      throw exception;
    }
  }

  @Override
  public void cancelCaseWorkflow(UUID caseId, String reason) {
    Optional<WorkflowInstanceRecord> workflowInstance = workflowInstanceStore.findByCaseId(caseId);
    String processInstanceId =
        workflowInstance
            .map(WorkflowInstanceRecord::processInstanceId)
            .orElseGet(
                () -> {
                  ProcessInstance runningInstance =
                      camundaServices
                          .runtimeService()
                          .createProcessInstanceQuery()
                          .processInstanceBusinessKey(caseId.toString())
                          .singleResult();
                  return runningInstance == null ? null : runningInstance.getProcessInstanceId();
                });
    if (processInstanceId != null
        && camundaServices
                .runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .count()
            > 0) {
      camundaServices.runtimeService().deleteProcessInstance(processInstanceId, reason);
    }
    workflowInstanceStore.markCancelled(caseId, clock.instant());
  }

  @Override
  public List<WorkflowTaskView> listActiveTasks() {
    List<Task> tasks = camundaServices.taskService().createTaskQuery().active().list();
    Map<UUID, CaseRecord> casesById = loadCases(tasks);
    return tasks.stream()
        .map(task -> toWorkflowTaskView(task, casesById.get(resolveCaseId(task))))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  @Override
  public Optional<WorkflowTaskView> findActiveTask(String taskId) {
    Task task =
        camundaServices.taskService().createTaskQuery().taskId(taskId).active().singleResult();
    if (task == null) {
      return Optional.empty();
    }
    UUID caseId = resolveCaseId(task);
    CaseRecord caseRecord = caseRepository.findById(caseId).orElse(null);
    return toWorkflowTaskView(task, caseRecord);
  }

  @Override
  public boolean isTaskCompleted(String taskId) {
    return camundaServices
            .historyService()
            .createHistoricTaskInstanceQuery()
            .taskId(taskId)
            .finished()
            .count()
        > 0;
  }

  @Override
  public WorkflowTaskView claimTask(String taskId, String username) {
    var taskService = camundaServices.taskService();
    Task currentTask = taskService.createTaskQuery().taskId(taskId).active().singleResult();
    if (currentTask == null) {
      if (isTaskCompleted(taskId)) {
        throw new WorkflowTaskConflictException(
            "TASK_ALREADY_COMPLETED", "Workflow task " + taskId + " was already completed.");
      }
      throw new WorkflowTaskNotFoundException(taskId);
    }
    try {
      taskService.claim(taskId, username);
    } catch (ProcessEngineException exception) {
      throw new WorkflowTaskConflictException(
          "TASK_CLAIM_FAILED", "Workflow task " + taskId + " could not be claimed.");
    }
    return findActiveTask(taskId).orElseThrow(() -> new WorkflowTaskNotFoundException(taskId));
  }

  @Override
  public void completeTask(String taskId) {
    var taskService = camundaServices.taskService();
    Task currentTask = taskService.createTaskQuery().taskId(taskId).active().singleResult();
    if (currentTask == null) {
      if (isTaskCompleted(taskId)) {
        return;
      }
      throw new WorkflowTaskNotFoundException(taskId);
    }
    String processInstanceId = currentTask.getProcessInstanceId();
    try {
      taskService.complete(taskId);
    } catch (ProcessEngineException exception) {
      if (isTaskCompleted(taskId)) {
        return;
      }
      throw new WorkflowTaskConflictException(
          "TASK_COMPLETE_FAILED", "Workflow task " + taskId + " could not be completed.");
    }
    if (camundaServices
            .runtimeService()
            .createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .count()
        == 0) {
      workflowInstanceStore.markCompleted(processInstanceId, clock.instant());
    }
  }

  private Map<UUID, CaseRecord> loadCases(List<Task> tasks) {
    Set<UUID> caseIds = tasks.stream().map(this::resolveCaseId).collect(Collectors.toSet());
    return caseRepository.findByIds(caseIds).stream()
        .collect(Collectors.toMap(CaseRecord::id, Function.identity()));
  }

  private Optional<WorkflowTaskView> toWorkflowTaskView(Task task, CaseRecord caseRecord) {
    if (caseRecord == null) {
      return Optional.empty();
    }
    return Optional.of(
        new WorkflowTaskView(
            task.getId(),
            task.getName(),
            task.getTaskDefinitionKey(),
            task.getProcessInstanceId(),
            caseRecord.id(),
            caseRecord.caseNumber(),
            caseRecord.title(),
            caseRecord.summary(),
            caseRecord.status(),
            caseRecord.jurisdictionCode(),
            task.getAssignee(),
            task.getCreateTime().toInstant(),
            task.getAssignee() == null ? WorkflowTaskState.READY : WorkflowTaskState.CLAIMED));
  }

  private UUID resolveCaseId(Task task) {
    Object variableValue = camundaServices.taskService().getVariable(task.getId(), "caseId");
    if (!(variableValue instanceof String caseIdValue)) {
      throw new IllegalStateException("Workflow task " + task.getId() + " is missing caseId.");
    }
    return UUID.fromString(caseIdValue);
  }
}
