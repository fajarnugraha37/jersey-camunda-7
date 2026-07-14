package com.sentinel.enforcement.application.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowAdministrationPort {

  List<WorkflowProcessSnapshot> listActiveProcessInstances();

  Optional<WorkflowProcessSnapshot> findActiveProcessInstance(UUID caseId);

  Optional<WorkflowHistoricProcessSnapshot> findLatestFinishedProcessInstance(UUID caseId);

  void terminateActiveProcessInstance(UUID caseId, String reason);
}
