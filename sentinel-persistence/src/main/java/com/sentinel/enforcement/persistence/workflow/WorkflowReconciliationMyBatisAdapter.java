package com.sentinel.enforcement.persistence.workflow;

import com.sentinel.enforcement.application.workflow.WorkflowInstanceCorrelation;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationAction;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationCandidate;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationIssueType;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationPage;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationQueryPort;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationView;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import java.time.OffsetDateTime;
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
  public WorkflowReconciliationPage findIssuePage(
      com.sentinel.enforcement.application.workflow.ListWorkflowReconciliationIssuesQuery query) {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      List<WorkflowReconciliationView> loaded =
          session
              .getMapper(WorkflowReconciliationMyBatisMapper.class)
              .findIssuePage(toIssueQueryData(query))
              .stream()
              .map(this::toIssueView)
              .toList();
      boolean hasNextPage = loaded.size() > query.limit();
      List<WorkflowReconciliationView> trimmed =
          hasNextPage ? loaded.subList(0, query.limit()) : loaded;
      String nextCursorValue = null;
      String nextCursorCaseId = null;
      if (hasNextPage && !trimmed.isEmpty()) {
        WorkflowReconciliationView cursorItem = trimmed.get(trimmed.size() - 1);
        nextCursorValue = extractCursorValue(cursorItem, query.sortBy());
        nextCursorCaseId = cursorItem.caseId().toString();
      }
      return new WorkflowReconciliationPage(
          trimmed, nextCursorValue, nextCursorCaseId, hasNextPage);
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

  private WorkflowReconciliationView toIssueView(WorkflowReconciliationIssueData issueData) {
    return new WorkflowReconciliationView(
        issueData.caseId(),
        issueData.caseNumber(),
        issueData.caseTitle(),
        CaseStatus.valueOf(issueData.caseStatus()),
        issueData.jurisdictionCode(),
        issueData.assigneeUserId(),
        issueData.caseUpdatedAt().toInstant(),
        WorkflowReconciliationIssueType.valueOf(issueData.issueType()),
        issueData.detail(),
        issueData.workflowCorrelationStatus(),
        issueData.correlationProcessInstanceId(),
        issueData.runtimeProcessInstanceId(),
        parseActions(issueData.availableActionsCsv()));
  }

  private List<WorkflowReconciliationAction> parseActions(String availableActionsCsv) {
    if (availableActionsCsv == null || availableActionsCsv.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(availableActionsCsv.split(","))
        .filter(value -> !value.isBlank())
        .map(String::trim)
        .map(WorkflowReconciliationAction::valueOf)
        .toList();
  }

  private WorkflowReconciliationIssueQueryData toIssueQueryData(
      com.sentinel.enforcement.application.workflow.ListWorkflowReconciliationIssuesQuery query) {
    return new WorkflowReconciliationIssueQueryData(
        toContainsPattern(query.quickSearch()),
        query.searchField() == null ? null : query.searchField().name(),
        toContainsPattern(query.searchValue()),
        query.issueType() == null ? null : query.issueType().name(),
        query.caseStatus() == null ? null : query.caseStatus().name(),
        query.workflowCorrelationStatus(),
        query.sortBy().name(),
        query.sortDirection().name(),
        toCursorTimestamp(query.cursorValue(), query.sortBy()),
        toCursorText(query.cursorValue(), query.sortBy()),
        query.cursorCaseId(),
        query.limit() + 1);
  }

  private OffsetDateTime toCursorTimestamp(
      String cursorValue,
      com.sentinel.enforcement.application.workflow.WorkflowReconciliationSortBy sortBy) {
    if (cursorValue == null || !sortBy.isTimestampBased()) {
      return null;
    }
    return OffsetDateTime.parse(cursorValue);
  }

  private String toCursorText(
      String cursorValue,
      com.sentinel.enforcement.application.workflow.WorkflowReconciliationSortBy sortBy) {
    return cursorValue == null || sortBy.isTimestampBased() ? null : cursorValue;
  }

  private String toContainsPattern(String value) {
    return value == null || value.isBlank()
        ? null
        : "%" + value.trim().toLowerCase(java.util.Locale.ROOT) + "%";
  }

  private String extractCursorValue(
      WorkflowReconciliationView issue,
      com.sentinel.enforcement.application.workflow.WorkflowReconciliationSortBy sortBy) {
    return switch (sortBy) {
      case CASE_UPDATED_AT -> issue.caseUpdatedAt().toString();
      case CASE_NUMBER -> normalize(issue.caseNumber());
      case CASE_STATUS -> normalize(issue.caseStatus().name());
      case ISSUE_TYPE -> normalize(issue.issueType().name());
      case CORRELATION_STATUS -> normalize(issue.workflowCorrelationStatus());
    };
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
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
