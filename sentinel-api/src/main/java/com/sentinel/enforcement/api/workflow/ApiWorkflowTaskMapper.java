package com.sentinel.enforcement.api.workflow;

import com.sentinel.enforcement.api.generated.model.CaseStatusValue;
import com.sentinel.enforcement.api.generated.model.WorkflowTaskListResponse;
import com.sentinel.enforcement.api.generated.model.WorkflowTaskResponse;
import com.sentinel.enforcement.api.generated.model.WorkflowTaskStateValue;
import com.sentinel.enforcement.application.workflow.WorkflowTaskPage;
import com.sentinel.enforcement.application.workflow.WorkflowTaskView;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ApiWorkflowTaskMapper {
  ApiWorkflowTaskMapper INSTANCE = Mappers.getMapper(ApiWorkflowTaskMapper.class);

  @Mapping(target = "caseStatus", expression = "java(toApiCaseStatus(taskView.caseStatus()))")
  @Mapping(target = "state", expression = "java(toApiState(taskView.state()))")
  WorkflowTaskResponse toResponse(WorkflowTaskView taskView);

  default WorkflowTaskListResponse toListResponse(WorkflowTaskPage page, String nextCursor) {
    return new WorkflowTaskListResponse()
        .items(page.items().stream().map(this::toResponse).toList())
        .nextCursor(nextCursor);
  }

  default OffsetDateTime toOffsetDateTime(java.time.Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  default CaseStatusValue toApiCaseStatus(CaseStatus status) {
    return CaseStatusValue.fromValue(status.name());
  }

  default WorkflowTaskStateValue toApiState(
      com.sentinel.enforcement.application.workflow.WorkflowTaskState state) {
    return WorkflowTaskStateValue.fromValue(state.name());
  }
}
