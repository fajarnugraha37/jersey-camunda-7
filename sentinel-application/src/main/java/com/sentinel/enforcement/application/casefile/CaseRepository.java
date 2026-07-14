package com.sentinel.enforcement.application.casefile;

import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseAssignment;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseStatusHistoryEntry;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CaseRepository {

  String nextCaseNumber(String jurisdictionCode, int year);

  void save(
      CaseRecord caseRecord, CaseStatusHistoryEntry statusHistoryEntry, AuditEvent auditEvent);

  Optional<CaseRecord> findById(UUID caseId);

  List<CaseRecord> findByIds(Set<UUID> caseIds);

  List<CaseRecord> findPage(CasePageRequest pageRequest);

  void assign(CaseRecord caseRecord, CaseAssignment caseAssignment, AuditEvent auditEvent);

  void transition(
      CaseRecord caseRecord, CaseStatusHistoryEntry statusHistoryEntry, AuditEvent auditEvent);

  List<AuditEvent> findAuditEventsPage(AuditEventPageRequest pageRequest);

  void appendAuditEvent(AuditEvent auditEvent);
}
