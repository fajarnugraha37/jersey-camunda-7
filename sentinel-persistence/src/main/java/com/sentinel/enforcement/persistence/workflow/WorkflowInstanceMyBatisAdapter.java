package com.sentinel.enforcement.persistence.workflow;

import com.sentinel.enforcement.application.workflow.StartedWorkflowInstance;
import com.sentinel.enforcement.application.workflow.WorkflowInstanceCorrelation;
import com.sentinel.enforcement.application.workflow.WorkflowInstanceStore;
import com.sentinel.enforcement.persistence.MyBatisRepositorySupport;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;

public final class WorkflowInstanceMyBatisAdapter extends MyBatisRepositorySupport
    implements WorkflowInstanceStore {

  public WorkflowInstanceMyBatisAdapter(SqlSessionFactory sqlSessionFactory) {
    super(sqlSessionFactory);
  }

  @Override
  public void upsert(WorkflowInstanceCorrelation workflowInstanceCorrelation, Instant now) {
    executeWrite(
        session -> {
          session
              .getMapper(WorkflowInstanceMyBatisMapper.class)
              .upsertWorkflowInstance(toData(workflowInstanceCorrelation, now));
          return null;
        });
  }

  @Override
  public void saveStarted(StartedWorkflowInstance startedWorkflowInstance, Instant now) {
    executeWrite(
        session -> {
          session
              .getMapper(WorkflowInstanceMyBatisMapper.class)
              .upsertStartedWorkflow(toData(startedWorkflowInstance, now));
          return null;
        });
  }

  @Override
  public Optional<WorkflowInstanceCorrelation> findByCaseId(UUID caseId) {
    return executeRead(
        session ->
            Optional.ofNullable(
                    session.getMapper(WorkflowInstanceMyBatisMapper.class).findByCaseId(caseId))
                .map(this::toCorrelation));
  }

  @Override
  public void markCompleted(String processInstanceId, Instant now) {
    executeWrite(
        session -> {
          session
              .getMapper(WorkflowInstanceMyBatisMapper.class)
              .markCompleted(processInstanceId, now.atOffset(ZoneOffset.UTC));
          return null;
        });
  }

  @Override
  public void markCancelled(UUID caseId, Instant now) {
    executeWrite(
        session -> {
          session
              .getMapper(WorkflowInstanceMyBatisMapper.class)
              .markCancelled(caseId, now.atOffset(ZoneOffset.UTC));
          return null;
        });
  }

  private WorkflowInstanceData toData(
      StartedWorkflowInstance startedWorkflowInstance, Instant now) {
    return new WorkflowInstanceData(
        startedWorkflowInstance.caseId(),
        startedWorkflowInstance.processInstanceId(),
        startedWorkflowInstance.processDefinitionId(),
        startedWorkflowInstance.processDefinitionVersion(),
        startedWorkflowInstance.businessKey(),
        "ACTIVE",
        now.atOffset(ZoneOffset.UTC),
        now.atOffset(ZoneOffset.UTC));
  }

  private WorkflowInstanceData toData(
      WorkflowInstanceCorrelation workflowInstanceCorrelation, Instant now) {
    return new WorkflowInstanceData(
        workflowInstanceCorrelation.caseId(),
        workflowInstanceCorrelation.processInstanceId(),
        workflowInstanceCorrelation.processDefinitionId(),
        workflowInstanceCorrelation.processDefinitionVersion(),
        workflowInstanceCorrelation.businessKey(),
        workflowInstanceCorrelation.status(),
        now.atOffset(ZoneOffset.UTC),
        now.atOffset(ZoneOffset.UTC));
  }

  private WorkflowInstanceCorrelation toCorrelation(WorkflowInstanceData workflowInstanceData) {
    return new WorkflowInstanceCorrelation(
        workflowInstanceData.caseId(),
        workflowInstanceData.processInstanceId(),
        workflowInstanceData.processDefinitionId(),
        workflowInstanceData.processDefinitionVersion(),
        workflowInstanceData.businessKey(),
        workflowInstanceData.status());
  }
}
