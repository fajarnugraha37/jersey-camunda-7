package com.sentinel.enforcement.api.casefile;

import com.sentinel.enforcement.api.generated.model.AssignCaseRequest;
import com.sentinel.enforcement.api.generated.model.CaseAuditEventListResponse;
import com.sentinel.enforcement.api.generated.model.CaseAuditEventResponse;
import com.sentinel.enforcement.api.generated.model.CaseClassificationValue;
import com.sentinel.enforcement.api.generated.model.CaseListResponse;
import com.sentinel.enforcement.api.generated.model.CaseResponse;
import com.sentinel.enforcement.api.generated.model.CaseStatusValue;
import com.sentinel.enforcement.api.generated.model.CreateCaseRequest;
import com.sentinel.enforcement.api.generated.model.TransitionCaseRequest;
import com.sentinel.enforcement.application.casefile.AssignCaseCommand;
import com.sentinel.enforcement.application.casefile.AuditEventPage;
import com.sentinel.enforcement.application.casefile.CasePage;
import com.sentinel.enforcement.application.casefile.CreateCaseCommand;
import com.sentinel.enforcement.application.casefile.TransitionCaseCommand;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseClassification;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ApiCaseMapper {
  ApiCaseMapper INSTANCE = Mappers.getMapper(ApiCaseMapper.class);

  @Mapping(target = "correlationId", source = "correlationId")
  @Mapping(target = "sourceIp", source = "sourceIp")
  @Mapping(
      target = "classification",
      expression = "java(toDomainClassification(request.getClassification()))")
  CreateCaseCommand toCreateCaseCommand(
      CreateCaseRequest request, String correlationId, String sourceIp);

  @Mapping(target = "correlationId", source = "correlationId")
  @Mapping(target = "sourceIp", source = "sourceIp")
  AssignCaseCommand toAssignCaseCommand(
      AssignCaseRequest request, String correlationId, String sourceIp);

  @Mapping(target = "targetStatus", expression = "java(toDomainStatus(request.getTargetStatus()))")
  @Mapping(target = "correlationId", source = "correlationId")
  @Mapping(target = "sourceIp", source = "sourceIp")
  TransitionCaseCommand toTransitionCaseCommand(
      TransitionCaseRequest request, String correlationId, String sourceIp);

  @Mapping(target = "status", source = "status")
  CaseResponse toResponse(CaseRecord caseRecord);

  default CaseListResponse toListResponse(CasePage casePage, String nextCursor) {
    return new CaseListResponse()
        .items(casePage.items().stream().map(this::toResponse).toList())
        .nextCursor(nextCursor);
  }

  default CaseAuditEventListResponse toAuditListResponse(
      AuditEventPage auditEventPage, String nextCursor) {
    return new CaseAuditEventListResponse()
        .items(auditEventPage.items().stream().map(this::toAuditResponse).toList())
        .nextCursor(nextCursor);
  }

  default CaseAuditEventResponse toAuditResponse(AuditEvent auditEvent) {
    return new CaseAuditEventResponse()
        .eventId(auditEvent.eventId())
        .eventType(auditEvent.eventType())
        .actorType(auditEvent.actorType())
        .actorId(auditEvent.actorId())
        .actorRoles(splitRoles(auditEvent.actorRoles()))
        .action(auditEvent.action())
        .resourceType(auditEvent.resourceType())
        .resourceId(auditEvent.resourceId())
        .caseId(auditEvent.caseId())
        .timestamp(OffsetDateTime.ofInstant(auditEvent.timestamp(), ZoneOffset.UTC))
        .correlationId(auditEvent.correlationId())
        .sourceIp(auditEvent.sourceIp())
        .result(auditEvent.result())
        .reason(auditEvent.reason())
        .beforeSummary(auditEvent.beforeSummary())
        .afterSummary(auditEvent.afterSummary())
        .metadata(auditEvent.metadata());
  }

  default CaseStatus toDomainStatus(CaseStatusValue statusValue) {
    return CaseStatus.valueOf(statusValue.toString());
  }

  default CaseClassification toDomainClassification(CaseClassificationValue classificationValue) {
    return CaseClassification.valueOf(classificationValue.toString());
  }

  default CaseStatusValue toApiStatus(CaseStatus status) {
    return CaseStatusValue.fromValue(status.name());
  }

  default CaseClassificationValue toApiClassification(CaseClassification classification) {
    return CaseClassificationValue.fromValue(classification.name());
  }

  default OffsetDateTime toOffsetDateTime(java.time.Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  default List<String> splitRoles(String actorRoles) {
    return Stream.of(actorRoles.split(","))
        .map(String::trim)
        .filter(role -> !role.isEmpty())
        .toList();
  }
}
