package com.sentinel.enforcement.api.workflow;

import com.sentinel.enforcement.api.generated.model.ReconcileWorkflowRequest;
import com.sentinel.enforcement.api.generated.model.WorkflowCorrelationStatusValue;
import com.sentinel.enforcement.api.generated.model.WorkflowReconciliationActionResponse;
import com.sentinel.enforcement.api.generated.model.WorkflowReconciliationActionResultValue;
import com.sentinel.enforcement.api.generated.model.WorkflowReconciliationIssueListResponse;
import com.sentinel.enforcement.api.generated.model.WorkflowReconciliationIssueResponse;
import com.sentinel.enforcement.api.generated.model.WorkflowReconciliationIssueTypeValue;
import com.sentinel.enforcement.api.generated.model.WorkflowReconciliationOperationValue;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationAction;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationActionCommand;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationActionResult;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationPage;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationView;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ApiWorkflowReconciliationMapper {
  ApiWorkflowReconciliationMapper INSTANCE =
      Mappers.getMapper(ApiWorkflowReconciliationMapper.class);

  default WorkflowReconciliationIssueListResponse toListResponse(
      WorkflowReconciliationPage page, String nextCursor) {
    return new WorkflowReconciliationIssueListResponse()
        .items(page.items().stream().map(this::toIssueResponse).toList())
        .nextCursor(nextCursor);
  }

  default WorkflowReconciliationIssueResponse toIssueResponse(WorkflowReconciliationView view) {
    return new WorkflowReconciliationIssueResponse()
        .caseId(view.caseId())
        .caseNumber(view.caseNumber())
        .caseTitle(view.caseTitle())
        .caseStatus(
            com.sentinel.enforcement.api.generated.model.CaseStatusValue.fromValue(
                view.caseStatus().name()))
        .jurisdictionCode(view.jurisdictionCode())
        .assigneeUserId(view.assigneeUserId())
        .caseUpdatedAt(OffsetDateTime.ofInstant(view.caseUpdatedAt(), ZoneOffset.UTC))
        .issueType(WorkflowReconciliationIssueTypeValue.fromValue(view.issueType().name()))
        .detail(view.detail())
        .workflowCorrelationStatus(
            toWorkflowCorrelationStatusValue(view.workflowCorrelationStatus()))
        .correlationProcessInstanceId(view.correlationProcessInstanceId())
        .runtimeProcessInstanceId(view.runtimeProcessInstanceId())
        .availableActions(
            view.availableActions().stream()
                .map(action -> WorkflowReconciliationOperationValue.fromValue(action.name()))
                .toList());
  }

  default WorkflowReconciliationActionCommand toActionCommand(
      ReconcileWorkflowRequest request, String correlationId, String sourceIp) {
    return new WorkflowReconciliationActionCommand(
        WorkflowReconciliationAction.valueOf(request.getAction().toString()),
        request.getReason(),
        correlationId,
        sourceIp);
  }

  default WorkflowReconciliationActionResponse toActionResponse(
      WorkflowReconciliationActionResult result) {
    return new WorkflowReconciliationActionResponse()
        .caseId(result.caseId())
        .action(WorkflowReconciliationOperationValue.fromValue(result.action().name()))
        .result(WorkflowReconciliationActionResultValue.fromValue(result.result().name()))
        .issueType(WorkflowReconciliationIssueTypeValue.fromValue(result.issueType().name()))
        .detail(result.detail())
        .workflowCorrelationStatus(
            toWorkflowCorrelationStatusValue(result.workflowCorrelationStatus()))
        .processInstanceId(result.processInstanceId());
  }

  private static WorkflowCorrelationStatusValue toWorkflowCorrelationStatusValue(String value) {
    return value == null ? null : WorkflowCorrelationStatusValue.fromValue(value);
  }
}
