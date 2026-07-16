package com.sentinel.enforcement.persistence.casefile;

import com.sentinel.enforcement.application.casefile.AuditEventListSortBy;
import com.sentinel.enforcement.application.casefile.AuditEventPageRequest;
import com.sentinel.enforcement.application.casefile.CasePageRequest;
import com.sentinel.enforcement.application.casefile.CaseRelationshipTraversalDirection;
import com.sentinel.enforcement.application.casefile.CaseRelationshipView;
import com.sentinel.enforcement.application.casefile.CaseRelationshipViewDirection;
import com.sentinel.enforcement.application.casefile.CaseRepository;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseAssignment;
import com.sentinel.enforcement.domain.casefile.CaseClassification;
import com.sentinel.enforcement.domain.casefile.CaseConflictException;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseRelationship;
import com.sentinel.enforcement.domain.casefile.CaseRelationshipType;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import com.sentinel.enforcement.domain.casefile.CaseStatusHistoryEntry;
import com.sentinel.enforcement.persistence.MyBatisRepositorySupport;
import com.sentinel.enforcement.persistence.PersistenceExceptionClassifier;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.ibatis.session.SqlSessionFactory;

public final class CaseRepositoryMyBatisAdapter extends MyBatisRepositorySupport
    implements CaseRepository {

  public CaseRepositoryMyBatisAdapter(SqlSessionFactory sqlSessionFactory) {
    super(sqlSessionFactory);
  }

  @Override
  public String nextCaseNumber(String jurisdictionCode, int year) {
    return executeWrite(
        session ->
            session.getMapper(CaseMyBatisMapper.class).nextCaseNumber(jurisdictionCode, year));
  }

  @Override
  public void save(
      CaseRecord caseRecord, CaseStatusHistoryEntry statusHistoryEntry, AuditEvent auditEvent) {
    executeWrite(
        session -> {
          CaseMyBatisMapper mapper = session.getMapper(CaseMyBatisMapper.class);
          mapper.insertCase(toCaseData(caseRecord));
          mapper.insertStatusHistory(toHistoryData(statusHistoryEntry));
          mapper.insertAuditEvent(toAuditData(auditEvent));
          return null;
        });
  }

  @Override
  public Optional<CaseRecord> findById(UUID caseId) {
    return executeRead(
        session ->
            Optional.ofNullable(session.getMapper(CaseMyBatisMapper.class).findCaseById(caseId))
                .map(this::toCaseDomain));
  }

  @Override
  public List<CaseRecord> findByIds(Set<UUID> caseIds) {
    if (caseIds.isEmpty()) {
      return List.of();
    }
    return executeRead(
        session ->
            session.getMapper(CaseMyBatisMapper.class).findCasesByIds(caseIds).stream()
                .map(this::toCaseDomain)
                .collect(Collectors.toList()));
  }

  @Override
  public List<CaseRecord> findPage(CasePageRequest pageRequest) {
    return executeRead(
        session -> {
          CasePageQueryData queryData =
              new CasePageQueryData(
                  pageRequest.jurisdictionCodes(),
                  pageRequest.restrictedAssigneeUserId(),
                  pageRequest.restrictedAssignedUnitIds(),
                  pageRequest.includeUnassignedWhenUnitRestricted(),
                  pageRequest.allowedClassifications().stream()
                      .map(CaseClassification::name)
                      .collect(Collectors.toSet()),
                  pageRequest.excludedCreatedByUserIds(),
                  pageRequest.requestedAssigneeUserId(),
                  toContainsPattern(pageRequest.quickSearch()),
                  pageRequest.searchField() == null ? null : pageRequest.searchField().name(),
                  toContainsPattern(pageRequest.searchValue()),
                  pageRequest.status() == null ? null : pageRequest.status().name(),
                  pageRequest.classification() == null ? null : pageRequest.classification().name(),
                  pageRequest.assignedUnitId(),
                  pageRequest.createdBy(),
                  pageRequest.reportId(),
                  pageRequest.sortBy().name(),
                  pageRequest.sortDirection().name(),
                  toCursorTimestamp(pageRequest.cursorValue(), pageRequest.sortBy()),
                  toCursorText(pageRequest.cursorValue(), pageRequest.sortBy()),
                  pageRequest.cursorId(),
                  pageRequest.limitPlusOne());
          return session.getMapper(CaseMyBatisMapper.class).findCasePage(queryData).stream()
              .map(this::toCaseDomain)
              .collect(Collectors.toList());
        });
  }

  @Override
  public void assign(CaseRecord caseRecord, CaseAssignment caseAssignment, AuditEvent auditEvent) {
    executeWrite(
        session -> {
          CaseMyBatisMapper mapper = session.getMapper(CaseMyBatisMapper.class);
          int updated =
              mapper.rotateAssignment(
                  new CaseAssignmentRotationData(
                      caseRecord.id(),
                      caseRecord.version() - 1,
                      caseRecord.assignedUnitId(),
                      caseRecord.assigneeUserId(),
                      caseRecord.updatedAt().atOffset(ZoneOffset.UTC),
                      caseRecord.updatedBy(),
                      caseAssignment.id(),
                      caseAssignment.assignmentReason(),
                      caseAssignment.assignedAt().atOffset(ZoneOffset.UTC),
                      caseAssignment.assignedBy(),
                      caseAssignment.createdAt().atOffset(ZoneOffset.UTC),
                      caseAssignment.createdBy(),
                      caseAssignment.version()));
          if (updated == 0) {
            throw new CaseConflictException(
                "CONCURRENT_MODIFICATION",
                "Case " + caseRecord.caseNumber() + " was modified by another transaction.");
          }
          mapper.insertAuditEvent(toAuditData(auditEvent));
          return null;
        });
  }

  @Override
  public void transition(
      CaseRecord caseRecord, CaseStatusHistoryEntry statusHistoryEntry, AuditEvent auditEvent) {
    executeWrite(
        session -> {
          CaseMyBatisMapper mapper = session.getMapper(CaseMyBatisMapper.class);
          int updated = mapper.updateCase(toCaseData(caseRecord), caseRecord.version() - 1);
          if (updated == 0) {
            throw new CaseConflictException(
                "CONCURRENT_MODIFICATION",
                "Case " + caseRecord.caseNumber() + " was modified by another transaction.");
          }
          mapper.insertStatusHistory(toHistoryData(statusHistoryEntry));
          mapper.insertAuditEvent(toAuditData(auditEvent));
          return null;
        });
  }

  @Override
  public void createRelationship(CaseRelationship relationship) {
    try {
      executeWrite(
          session -> {
            session
                .getMapper(CaseMyBatisMapper.class)
                .insertRelationship(toRelationshipData(relationship));
            return null;
          });
    } catch (RuntimeException exception) {
      if (PersistenceExceptionClassifier.isUniqueViolation(exception)) {
        throw new CaseConflictException(
            "CASE_RELATIONSHIP_ALREADY_EXISTS",
            "Relationship already exists between cases "
                + relationship.parentCaseId()
                + " and "
                + relationship.childCaseId()
                + ".");
      }
      throw exception;
    }
  }

  @Override
  public boolean wouldCreateRelationshipCycle(UUID parentCaseId, UUID childCaseId) {
    return executeRead(
        session ->
            session
                .getMapper(CaseMyBatisMapper.class)
                .wouldCreateRelationshipCycle(parentCaseId, childCaseId));
  }

  @Override
  public List<CaseRelationshipView> findRelationships(
      UUID caseId,
      CaseRelationshipTraversalDirection direction,
      int maxDepth,
      CaseRelationshipType relationshipType) {
    return executeRead(
        session -> {
          CaseMyBatisMapper mapper = session.getMapper(CaseMyBatisMapper.class);
          CaseRelationshipQueryData queryData =
              new CaseRelationshipQueryData(
                  caseId, maxDepth, relationshipType == null ? null : relationshipType.name());
          List<CaseRelationshipView> relationships = new ArrayList<>();
          if (direction == CaseRelationshipTraversalDirection.ANCESTORS
              || direction == CaseRelationshipTraversalDirection.BOTH) {
            relationships.addAll(
                mapper.findAncestorRelationships(queryData).stream()
                    .map(this::toRelationshipView)
                    .toList());
          }
          if (direction == CaseRelationshipTraversalDirection.DESCENDANTS
              || direction == CaseRelationshipTraversalDirection.BOTH) {
            relationships.addAll(
                mapper.findDescendantRelationships(queryData).stream()
                    .map(this::toRelationshipView)
                    .toList());
          }
          relationships.sort(
              Comparator.comparing((CaseRelationshipView view) -> view.direction().name())
                  .thenComparingInt(CaseRelationshipView::depth)
                  .thenComparing(CaseRelationshipView::relatedCaseNumber)
                  .thenComparing(CaseRelationshipView::relatedCaseId));
          return relationships;
        });
  }

  @Override
  public List<AuditEvent> findAuditEventsPage(AuditEventPageRequest pageRequest) {
    return executeRead(
        session -> {
          AuditEventPageQueryData queryData =
              new AuditEventPageQueryData(
                  pageRequest.caseId(),
                  toContainsPattern(pageRequest.quickSearch()),
                  pageRequest.searchField() == null ? null : pageRequest.searchField().name(),
                  toContainsPattern(pageRequest.searchValue()),
                  pageRequest.actorId(),
                  pageRequest.eventType(),
                  pageRequest.action(),
                  pageRequest.result(),
                  pageRequest.sortBy().name(),
                  pageRequest.sortDirection().name(),
                  toCursorTimestamp(pageRequest.cursorValue(), pageRequest.sortBy()),
                  toCursorText(pageRequest.cursorValue(), pageRequest.sortBy()),
                  pageRequest.cursorId(),
                  pageRequest.limitPlusOne());
          return session.getMapper(CaseMyBatisMapper.class).findAuditEventsPage(queryData).stream()
              .map(this::toAuditDomain)
              .collect(Collectors.toList());
        });
  }

  @Override
  public void appendAuditEvent(AuditEvent auditEvent) {
    executeWrite(
        session -> {
          session
              .getMapper(CaseMyBatisMapper.class)
              .insertAuditEventIfAbsent(toAuditData(auditEvent));
          return null;
        });
  }

  private CaseRecordData toCaseData(CaseRecord caseRecord) {
    return new CaseRecordData(
        caseRecord.id(),
        caseRecord.caseNumber(),
        caseRecord.reportId(),
        caseRecord.title(),
        caseRecord.summary(),
        caseRecord.jurisdictionCode(),
        caseRecord.classification().name(),
        caseRecord.status().name(),
        caseRecord.assignedUnitId(),
        caseRecord.assigneeUserId(),
        caseRecord.createdAt().atOffset(ZoneOffset.UTC),
        caseRecord.createdBy(),
        caseRecord.updatedAt().atOffset(ZoneOffset.UTC),
        caseRecord.updatedBy(),
        caseRecord.version());
  }

  private CaseStatusHistoryData toHistoryData(CaseStatusHistoryEntry historyEntry) {
    return new CaseStatusHistoryData(
        historyEntry.id(),
        historyEntry.caseId(),
        historyEntry.fromStatus() == null ? null : historyEntry.fromStatus().name(),
        historyEntry.toStatus().name(),
        historyEntry.transitionReason(),
        historyEntry.transitionedAt().atOffset(ZoneOffset.UTC),
        historyEntry.transitionedBy(),
        historyEntry.createdAt().atOffset(ZoneOffset.UTC),
        historyEntry.createdBy());
  }

  private AuditEventData toAuditData(AuditEvent auditEvent) {
    return new AuditEventData(
        auditEvent.eventId(),
        auditEvent.eventType(),
        auditEvent.actorType(),
        auditEvent.actorId(),
        auditEvent.actorRoles(),
        auditEvent.action(),
        auditEvent.resourceType(),
        auditEvent.resourceId(),
        auditEvent.caseId(),
        auditEvent.timestamp().atOffset(ZoneOffset.UTC),
        auditEvent.correlationId(),
        auditEvent.sourceIp(),
        auditEvent.result(),
        auditEvent.reason(),
        auditEvent.beforeSummary(),
        auditEvent.afterSummary(),
        auditEvent.metadata());
  }

  private CaseRelationshipData toRelationshipData(CaseRelationship relationship) {
    return new CaseRelationshipData(
        relationship.id(),
        relationship.parentCaseId(),
        relationship.childCaseId(),
        relationship.relationshipType().name(),
        relationship.relationshipReason(),
        relationship.createdAt().atOffset(ZoneOffset.UTC),
        relationship.createdBy(),
        relationship.updatedAt().atOffset(ZoneOffset.UTC),
        relationship.updatedBy(),
        relationship.version());
  }

  private CaseRecord toCaseDomain(CaseRecordData caseRecordData) {
    return new CaseRecord(
        caseRecordData.id(),
        caseRecordData.caseNumber(),
        caseRecordData.reportId(),
        caseRecordData.title(),
        caseRecordData.summary(),
        caseRecordData.jurisdictionCode(),
        CaseClassification.valueOf(caseRecordData.classification()),
        CaseStatus.valueOf(caseRecordData.status()),
        caseRecordData.assignedUnitId(),
        caseRecordData.assigneeUserId(),
        caseRecordData.createdAt().toInstant(),
        caseRecordData.createdBy(),
        caseRecordData.updatedAt().toInstant(),
        caseRecordData.updatedBy(),
        caseRecordData.version());
  }

  private AuditEvent toAuditDomain(AuditEventData auditEventData) {
    return new AuditEvent(
        auditEventData.eventId(),
        auditEventData.eventType(),
        auditEventData.actorType(),
        auditEventData.actorId(),
        auditEventData.actorRoles(),
        auditEventData.action(),
        auditEventData.resourceType(),
        auditEventData.resourceId(),
        auditEventData.caseId(),
        auditEventData.timestamp().toInstant(),
        auditEventData.correlationId(),
        auditEventData.sourceIp(),
        auditEventData.result(),
        auditEventData.reason(),
        auditEventData.beforeSummary(),
        auditEventData.afterSummary(),
        auditEventData.metadata());
  }

  private CaseRelationshipView toRelationshipView(CaseRelationshipLineageData relationshipData) {
    return new CaseRelationshipView(
        relationshipData.caseId(),
        relationshipData.relatedCaseId(),
        relationshipData.relatedCaseNumber(),
        relationshipData.relatedCaseTitle(),
        relationshipData.depth(),
        CaseRelationshipViewDirection.valueOf(relationshipData.direction()),
        CaseRelationshipType.valueOf(relationshipData.relationshipType()),
        relationshipData.relationshipReason(),
        parsePathCaseIds(relationshipData.pathCaseIdsCsv()));
  }

  private List<UUID> parsePathCaseIds(String pathCaseIdsCsv) {
    if (pathCaseIdsCsv == null || pathCaseIdsCsv.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(pathCaseIdsCsv.split(","))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .map(UUID::fromString)
        .toList();
  }

  private String toContainsPattern(String value) {
    return value == null ? null : "%" + value.toLowerCase(Locale.ROOT) + "%";
  }

  private OffsetDateTime toCursorTimestamp(
      String cursorValue, com.sentinel.enforcement.application.casefile.CaseListSortBy sortBy) {
    if (cursorValue == null || !sortBy.isTimestampBased()) {
      return null;
    }
    return Instant.parse(cursorValue).atOffset(ZoneOffset.UTC);
  }

  private String toCursorText(
      String cursorValue, com.sentinel.enforcement.application.casefile.CaseListSortBy sortBy) {
    if (cursorValue == null || sortBy.isTimestampBased()) {
      return null;
    }
    return cursorValue.toLowerCase(Locale.ROOT);
  }

  private OffsetDateTime toCursorTimestamp(String cursorValue, AuditEventListSortBy sortBy) {
    if (cursorValue == null || !sortBy.isTimestampBased()) {
      return null;
    }
    return Instant.parse(cursorValue).atOffset(ZoneOffset.UTC);
  }

  private String toCursorText(String cursorValue, AuditEventListSortBy sortBy) {
    if (cursorValue == null || sortBy.isTimestampBased()) {
      return null;
    }
    return cursorValue.toLowerCase(Locale.ROOT);
  }
}
