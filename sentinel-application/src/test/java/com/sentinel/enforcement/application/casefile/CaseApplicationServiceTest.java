package com.sentinel.enforcement.application.casefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.sentinel.enforcement.application.report.ReportRepository;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.Permission;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseAssignment;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseStatusHistoryEntry;
import com.sentinel.enforcement.domain.report.Report;
import com.sentinel.enforcement.domain.report.ReportStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaseApplicationServiceTest {

  @Test
  void createCaseUsesReportJurisdictionAndPersistsInitialHistoryAndAudit() {
    InMemoryCaseRepository caseRepository = new InMemoryCaseRepository();
    InMemoryReportRepository reportRepository = new InMemoryReportRepository();
    CapturingAuthorizationService authorizationService = new CapturingAuthorizationService();
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T10:15:30Z"), ZoneOffset.UTC);
    CaseApplicationService service =
        new CaseApplicationService(authorizationService, caseRepository, reportRepository, clock);
    ApplicationActor actor =
        new ApplicationActor("subject-1", "triage-jkt", Set.of("TRIAGE_OFFICER"), Set.of("JKT"));
    Report report =
        new Report(
            UUID.randomUUID(),
            "Improper gift disclosure",
            "Potential violation involving unreported gifts.",
            "JKT",
            "Analyst A",
            ReportStatus.SUBMITTED,
            Instant.parse("2026-07-14T09:00:00Z"),
            "intake-jkt",
            Instant.parse("2026-07-14T09:00:00Z"),
            "intake-jkt",
            0L);
    reportRepository.save(report);

    CaseRecord caseRecord =
        service.createCase(
            actor,
            new CreateCaseCommand(
                report.id(), "Gift disclosure case", "Triaged into case.", "corr-1", "127.0.0.1"));

    assertEquals("JKT-ENF-2026-00000001", caseRecord.caseNumber());
    assertEquals("JKT", caseRecord.jurisdictionCode());
    assertEquals(Permission.CREATE_CASE, authorizationService.permission());
    assertEquals("JKT", authorizationService.authorizationContext().jurisdictionCode());
    assertSame(caseRecord, caseRepository.savedCaseRecord);
    assertNotNull(caseRepository.savedHistoryEntry);
    assertNotNull(caseRepository.savedAuditEvent);
  }

  @Test
  void listCasesRestrictsInvestigatorToAssignedCases() {
    InMemoryCaseRepository caseRepository = new InMemoryCaseRepository();
    InMemoryReportRepository reportRepository = new InMemoryReportRepository();
    CapturingAuthorizationService authorizationService = new CapturingAuthorizationService();
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T10:15:30Z"), ZoneOffset.UTC);
    CaseApplicationService service =
        new CaseApplicationService(authorizationService, caseRepository, reportRepository, clock);
    ApplicationActor actor =
        new ApplicationActor(
            "subject-2", "investigator-jkt", Set.of("INVESTIGATOR"), Set.of("JKT"));

    service.listCases(actor, new ListCasesQuery(null, null, 20));

    assertEquals(Permission.LIST_CASES, authorizationService.permission());
    assertEquals(Set.of("JKT"), caseRepository.lastPageRequest.jurisdictionCodes());
    assertEquals("investigator-jkt", caseRepository.lastPageRequest.assigneeUserId());
    assertNull(authorizationService.authorizationContext().jurisdictionCode());
  }

  private static final class InMemoryCaseRepository implements CaseRepository {
    private String nextCaseNumber = "JKT-ENF-2026-00000001";
    private CaseRecord savedCaseRecord;
    private CaseStatusHistoryEntry savedHistoryEntry;
    private AuditEvent savedAuditEvent;
    private CasePageRequest lastPageRequest;

    @Override
    public String nextCaseNumber(String jurisdictionCode, int year) {
      return nextCaseNumber;
    }

    @Override
    public void save(
        CaseRecord caseRecord, CaseStatusHistoryEntry statusHistoryEntry, AuditEvent auditEvent) {
      this.savedCaseRecord = caseRecord;
      this.savedHistoryEntry = statusHistoryEntry;
      this.savedAuditEvent = auditEvent;
    }

    @Override
    public Optional<CaseRecord> findById(UUID caseId) {
      return Optional.empty();
    }

    @Override
    public List<CaseRecord> findPage(CasePageRequest pageRequest) {
      this.lastPageRequest = pageRequest;
      return List.of();
    }

    @Override
    public void assign(
        CaseRecord caseRecord, CaseAssignment caseAssignment, AuditEvent auditEvent) {}

    @Override
    public void transition(
        CaseRecord caseRecord, CaseStatusHistoryEntry statusHistoryEntry, AuditEvent auditEvent) {}

    @Override
    public List<AuditEvent> findAuditEvents(UUID caseId, int limit) {
      return List.of();
    }
  }

  private static final class InMemoryReportRepository implements ReportRepository {
    private final Map<UUID, Report> reports = new HashMap<>();

    @Override
    public void save(Report report) {
      reports.put(report.id(), report);
    }

    @Override
    public Optional<Report> findById(UUID reportId) {
      return Optional.ofNullable(reports.get(reportId));
    }
  }

  private static final class CapturingAuthorizationService implements AuthorizationService {
    private Permission permission;
    private AuthorizationContext authorizationContext;

    @Override
    public void requirePermission(
        ApplicationActor actor, Permission permission, AuthorizationContext authorizationContext) {
      this.permission = permission;
      this.authorizationContext = authorizationContext;
    }

    Permission permission() {
      return permission;
    }

    AuthorizationContext authorizationContext() {
      return authorizationContext;
    }
  }
}
