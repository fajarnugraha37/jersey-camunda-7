package com.sentinel.enforcement.persistence.workflow;

import com.sentinel.enforcement.application.workflow.StartedWorkflowInstance;
import com.sentinel.enforcement.application.workflow.WorkflowInstanceCorrelation;
import com.sentinel.enforcement.application.workflow.WorkflowInstanceStore;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public final class WorkflowInstanceMyBatisAdapter implements WorkflowInstanceStore {
  private final SqlSessionFactory sqlSessionFactory;

  public WorkflowInstanceMyBatisAdapter(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  @Override
  public void saveStarted(StartedWorkflowInstance startedWorkflowInstance, Instant now) {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      session
          .getMapper(WorkflowInstanceMyBatisMapper.class)
          .upsertStartedWorkflow(toData(startedWorkflowInstance, now));
      session.commit();
    }
  }

  @Override
  public Optional<WorkflowInstanceCorrelation> findByCaseId(UUID caseId) {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      return Optional.ofNullable(
              session.getMapper(WorkflowInstanceMyBatisMapper.class).findByCaseId(caseId))
          .map(this::toCorrelation);
    }
  }

  @Override
  public void markCompleted(String processInstanceId, Instant now) {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      session
          .getMapper(WorkflowInstanceMyBatisMapper.class)
          .markCompleted(processInstanceId, now.atOffset(ZoneOffset.UTC));
      session.commit();
    }
  }

  @Override
  public void markCancelled(UUID caseId, Instant now) {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      session
          .getMapper(WorkflowInstanceMyBatisMapper.class)
          .markCancelled(caseId, now.atOffset(ZoneOffset.UTC));
      session.commit();
    }
  }

  private WorkflowInstanceData toData(StartedWorkflowInstance startedWorkflowInstance, Instant now) {
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
