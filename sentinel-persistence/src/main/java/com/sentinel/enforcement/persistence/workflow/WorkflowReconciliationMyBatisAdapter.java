package com.sentinel.enforcement.persistence.workflow;

import com.sentinel.enforcement.application.workflow.WorkflowInstanceCorrelation;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationCandidate;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationQueryPort;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public final class WorkflowReconciliationMyBatisAdapter implements WorkflowReconciliationQueryPort {
  private final SqlSessionFactory sqlSessionFactory;

  public WorkflowReconciliationMyBatisAdapter(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  @Override
  public List<WorkflowReconciliationCandidate> findCandidates() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      return session.getMapper(WorkflowReconciliationMyBatisMapper.class).findCandidates().stream()
          .map(this::toCandidate)
          .toList();
    }
  }

  @Override
  public Optional<WorkflowReconciliationCandidate> findCandidateByCaseId(UUID caseId) {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      return Optional.ofNullable(
              session
                  .getMapper(WorkflowReconciliationMyBatisMapper.class)
                  .findCandidateByCaseId(caseId))
          .map(this::toCandidate);
    }
  }

  private WorkflowReconciliationCandidate toCandidate(
      WorkflowReconciliationCandidateData workflowReconciliationCandidateData) {
    return new WorkflowReconciliationCandidate(
        workflowReconciliationCandidateData.caseId(),
        workflowReconciliationCandidateData.caseNumber(),
        workflowReconciliationCandidateData.caseTitle(),
        CaseStatus.valueOf(workflowReconciliationCandidateData.caseStatus()),
        workflowReconciliationCandidateData.jurisdictionCode(),
        workflowReconciliationCandidateData.assigneeUserId(),
        workflowReconciliationCandidateData.caseUpdatedAt().toInstant(),
        toCorrelation(workflowReconciliationCandidateData));
  }

  private WorkflowInstanceCorrelation toCorrelation(
      WorkflowReconciliationCandidateData workflowReconciliationCandidateData) {
    if (workflowReconciliationCandidateData.correlationProcessInstanceId() == null) {
      return null;
    }
    return new WorkflowInstanceCorrelation(
        workflowReconciliationCandidateData.caseId(),
        workflowReconciliationCandidateData.correlationProcessInstanceId(),
        workflowReconciliationCandidateData.correlationProcessDefinitionId(),
        workflowReconciliationCandidateData.correlationProcessDefinitionVersion(),
        workflowReconciliationCandidateData.correlationBusinessKey(),
        workflowReconciliationCandidateData.correlationStatus());
  }
}
