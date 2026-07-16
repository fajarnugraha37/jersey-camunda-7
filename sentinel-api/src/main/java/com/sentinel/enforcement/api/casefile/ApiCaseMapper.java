package com.sentinel.enforcement.api.casefile;

import com.sentinel.enforcement.api.generated.model.AssignCaseRequest;
import com.sentinel.enforcement.api.generated.model.CaseAuditEventListResponse;
import com.sentinel.enforcement.api.generated.model.CaseAuditEventResponse;
import com.sentinel.enforcement.api.generated.model.CaseClassificationValue;
import com.sentinel.enforcement.api.generated.model.CaseListResponse;
import com.sentinel.enforcement.api.generated.model.CaseRelationshipListResponse;
import com.sentinel.enforcement.api.generated.model.CaseRelationshipResponse;
import com.sentinel.enforcement.api.generated.model.CaseResponse;
import com.sentinel.enforcement.api.generated.model.CaseStatusValue;
import com.sentinel.enforcement.api.generated.model.CreateCaseRelationshipRequest;
import com.sentinel.enforcement.api.generated.model.CreateCaseRequest;
import com.sentinel.enforcement.api.generated.model.TransitionCaseRequest;
import com.sentinel.enforcement.application.casefile.AssignCaseCommand;
import com.sentinel.enforcement.application.casefile.AuditEventPage;
import com.sentinel.enforcement.application.casefile.CasePage;
import com.sentinel.enforcement.application.casefile.CaseRelationshipReferenceDirection;
import com.sentinel.enforcement.application.casefile.CaseRelationshipTraversalDirection;
import com.sentinel.enforcement.application.casefile.CaseRelationshipView;
import com.sentinel.enforcement.application.casefile.CaseRelationshipViewDirection;
import com.sentinel.enforcement.application.casefile.CreateCaseCommand;
import com.sentinel.enforcement.application.casefile.CreateCaseRelationshipCommand;
import com.sentinel.enforcement.application.casefile.ListCaseRelationshipsQuery;
import com.sentinel.enforcement.application.casefile.TransitionCaseCommand;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseClassification;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseRelationshipType;
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

  @Mapping(
      target = "relationshipType",
      expression = "java(toDomainRelationshipType(request.getRelationshipType()))")
  @Mapping(
      target = "direction",
      expression = "java(toDomainRelationshipDirection(request.getDirection()))")
  @Mapping(target = "correlationId", source = "correlationId")
  @Mapping(target = "sourceIp", source = "sourceIp")
  CreateCaseRelationshipCommand toCreateCaseRelationshipCommand(
      CreateCaseRelationshipRequest request, String correlationId, String sourceIp);

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

  default ListCaseRelationshipsQuery toListRelationshipsQuery(
      String directionValue, Integer maxDepth, String relationshipTypeValue) {
    CaseRelationshipTraversalDirection direction =
        directionValue == null
            ? CaseRelationshipTraversalDirection.BOTH
            : toDomainRelationshipTraversalDirection(
                com.sentinel.enforcement.api.generated.model.CaseRelationshipTraversalDirectionValue
                    .fromValue(directionValue));
    CaseRelationshipType relationshipType =
        relationshipTypeValue == null
            ? null
            : toDomainRelationshipType(
                com.sentinel.enforcement.api.generated.model.CaseRelationshipTypeValue.fromValue(
                    relationshipTypeValue));
    return new ListCaseRelationshipsQuery(
        direction, maxDepth == null ? 10 : maxDepth, relationshipType);
  }

  default CaseRelationshipListResponse toRelationshipListResponse(
      List<CaseRelationshipView> relationships) {
    return new CaseRelationshipListResponse()
        .items(relationships.stream().map(this::toRelationshipResponse).toList());
  }

  default CaseRelationshipResponse toRelationshipResponse(CaseRelationshipView relationship) {
    return new CaseRelationshipResponse()
        .caseId(relationship.caseId())
        .relatedCaseId(relationship.relatedCaseId())
        .relatedCaseNumber(relationship.relatedCaseNumber())
        .relatedCaseTitle(relationship.relatedCaseTitle())
        .depth(relationship.depth())
        .direction(toApiRelationshipViewDirection(relationship.direction()))
        .relationshipType(toApiRelationshipType(relationship.relationshipType()))
        .relationshipReason(relationship.relationshipReason())
        .pathCaseIds(relationship.pathCaseIds());
  }

  default CaseStatus toDomainStatus(CaseStatusValue statusValue) {
    return CaseStatus.valueOf(statusValue.toString());
  }

  default CaseClassification toDomainClassification(CaseClassificationValue classificationValue) {
    return CaseClassification.valueOf(classificationValue.toString());
  }

  default CaseRelationshipType toDomainRelationshipType(
      com.sentinel.enforcement.api.generated.model.CaseRelationshipTypeValue
          relationshipTypeValue) {
    return CaseRelationshipType.valueOf(relationshipTypeValue.toString());
  }

  default CaseRelationshipReferenceDirection toDomainRelationshipDirection(
      com.sentinel.enforcement.api.generated.model.CreateCaseRelationshipDirectionValue
          directionValue) {
    return CaseRelationshipReferenceDirection.valueOf(directionValue.toString());
  }

  default CaseRelationshipTraversalDirection toDomainRelationshipTraversalDirection(
      com.sentinel.enforcement.api.generated.model.CaseRelationshipTraversalDirectionValue
          directionValue) {
    return CaseRelationshipTraversalDirection.valueOf(directionValue.toString());
  }

  default CaseStatusValue toApiStatus(CaseStatus status) {
    return CaseStatusValue.fromValue(status.name());
  }

  default CaseClassificationValue toApiClassification(CaseClassification classification) {
    return CaseClassificationValue.fromValue(classification.name());
  }

  default com.sentinel.enforcement.api.generated.model.CaseRelationshipTypeValue
      toApiRelationshipType(CaseRelationshipType relationshipType) {
    return com.sentinel.enforcement.api.generated.model.CaseRelationshipTypeValue.fromValue(
        relationshipType.name());
  }

  default com.sentinel.enforcement.api.generated.model.CaseRelationshipDirectionValue
      toApiRelationshipViewDirection(CaseRelationshipViewDirection direction) {
    return com.sentinel.enforcement.api.generated.model.CaseRelationshipDirectionValue.fromValue(
        direction.name());
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
