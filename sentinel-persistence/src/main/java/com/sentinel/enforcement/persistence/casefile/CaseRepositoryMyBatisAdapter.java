package com.sentinel.enforcement.persistence.casefile;

import com.sentinel.enforcement.application.casefile.AuditEventListSortBy;
import com.sentinel.enforcement.application.casefile.AuditEventPageRequest;
import com.sentinel.enforcement.application.casefile.CasePageRequest;
import com.sentinel.enforcement.application.casefile.CaseRepository;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseAssignment;
import com.sentinel.enforcement.domain.casefile.CaseConflictException;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import com.sentinel.enforcement.domain.casefile.CaseStatusHistoryEntry;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public final class CaseRepositoryMyBatisAdapter implements CaseRepository {
  private final SqlSessionFactory sqlSessionFactory;

  public CaseRepositoryMyBatisAdapter(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  @Override
  public String nextCaseNumber(String jurisdictionCode, int year) {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      CaseMyBatisMapper mapper = session.getMapper(CaseMyBatisMapper.class);
      String caseNumber = mapper.nextCaseNumber(jurisdictionCode, year);
      session.commit();
      return caseNumber;
    }
  }

  @Override
  public void save(
      CaseRecord caseRecord, CaseStatusHistoryEntry statusHistoryEntry, AuditEvent auditEvent) {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      CaseMyBatisMapper mapper = session.getMapper(CaseMyBatisMapper.class);
      mapper.insertCase(toCaseData(caseRecord));
      mapper.insertStatusHistory(toHistoryData(statusHistoryEntry));
      mapper.insertAuditEvent(toAuditData(auditEvent));
      session.commit();
    }
  }

  @Override
  public Optional<CaseRecord> findById(UUID caseId) {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      return Optional.ofNullable(session.getMapper(CaseMyBatisMapper.class).findCaseById(caseId))
          .map(this::toCaseDomain);
    }
  }

  @Override
  public List<CaseRecord> findPage(CasePageRequest pageRequest) {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      CasePageQueryData queryData =
          new CasePageQueryData(
              pageRequest.jurisdictionCodes(),
              pageRequest.restrictedAssigneeUserId(),
              pageRequest.requestedAssigneeUserId(),
              toContainsPattern(pageRequest.quickSearch()),
              pageRequest.searchField() == null ? null : pageRequest.searchField().name(),
              toContainsPattern(pageRequest.searchValue()),
              pageRequest.status() == null ? null : pageRequest.status().name(),
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
    }
  }

  @Override
  public void assign(CaseRecord caseRecord, CaseAssignment caseAssignment, AuditEvent auditEvent) {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      CaseMyBatisMapper mapper = session.getMapper(CaseMyBatisMapper.class);
      int updated = mapper.updateCase(toCaseData(caseRecord), caseRecord.version() - 1);
      if (updated == 0) {
        session.rollback();
        throw new CaseConflictException(
            "CONCURRENT_MODIFICATION",
            "Case " + caseRecord.caseNumber() + " was modified by another transaction.");
      }
      mapper.insertAssignment(toAssignmentData(caseAssignment));
      mapper.insertAuditEvent(toAuditData(auditEvent));
      session.commit();
    }
  }

  @Override
  public void transition(
      CaseRecord caseRecord, CaseStatusHistoryEntry statusHistoryEntry, AuditEvent auditEvent) {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      CaseMyBatisMapper mapper = session.getMapper(CaseMyBatisMapper.class);
      int updated = mapper.updateCase(toCaseData(caseRecord), caseRecord.version() - 1);
      if (updated == 0) {
        session.rollback();
        throw new CaseConflictException(
            "CONCURRENT_MODIFICATION",
            "Case " + caseRecord.caseNumber() + " was modified by another transaction.");
      }
      mapper.insertStatusHistory(toHistoryData(statusHistoryEntry));
      mapper.insertAuditEvent(toAuditData(auditEvent));
      session.commit();
    }
  }

  @Override
  public List<AuditEvent> findAuditEventsPage(AuditEventPageRequest pageRequest) {
    try (SqlSession session = sqlSessionFactory.openSession()) {
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
    }
  }

  private CaseRecordData toCaseData(CaseRecord caseRecord) {
    return new CaseRecordData(
        caseRecord.id(),
        caseRecord.caseNumber(),
        caseRecord.reportId(),
        caseRecord.title(),
        caseRecord.summary(),
        caseRecord.jurisdictionCode(),
        caseRecord.status().name(),
        caseRecord.assignedUnitId(),
        caseRecord.assigneeUserId(),
        caseRecord.createdAt().atOffset(ZoneOffset.UTC),
        caseRecord.createdBy(),
        caseRecord.updatedAt().atOffset(ZoneOffset.UTC),
        caseRecord.updatedBy(),
        caseRecord.version());
  }

  private CaseAssignmentData toAssignmentData(CaseAssignment caseAssignment) {
    return new CaseAssignmentData(
        caseAssignment.id(),
        caseAssignment.caseId(),
        caseAssignment.assignedUnitId(),
        caseAssignment.assigneeUserId(),
        caseAssignment.assignmentReason(),
        caseAssignment.assignedAt().atOffset(ZoneOffset.UTC),
        caseAssignment.assignedBy(),
        caseAssignment.createdAt().atOffset(ZoneOffset.UTC),
        caseAssignment.createdBy(),
        caseAssignment.updatedAt().atOffset(ZoneOffset.UTC),
        caseAssignment.updatedBy(),
        caseAssignment.version());
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

  private CaseRecord toCaseDomain(CaseRecordData caseRecordData) {
    return new CaseRecord(
        caseRecordData.id(),
        caseRecordData.caseNumber(),
        caseRecordData.reportId(),
        caseRecordData.title(),
        caseRecordData.summary(),
        caseRecordData.jurisdictionCode(),
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
