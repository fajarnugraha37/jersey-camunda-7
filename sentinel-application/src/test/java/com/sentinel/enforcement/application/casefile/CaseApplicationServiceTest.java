package com.sentinel.enforcement.application.casefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinel.enforcement.application.report.ReportRepository;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.Permission;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseAssignment;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
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

    service.listCases(
        actor,
        new ListCasesQuery(
            null,
            null,
            20,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            CaseListSortBy.CREATED_AT,
            SortDirection.DESC));

    assertEquals(Permission.LIST_CASES, authorizationService.permission());
    assertEquals(Set.of("JKT"), caseRepository.lastPageRequest.jurisdictionCodes());
    assertEquals("investigator-jkt", caseRepository.lastPageRequest.restrictedAssigneeUserId());
    assertNull(caseRepository.lastPageRequest.requestedAssigneeUserId());
    assertNull(authorizationService.authorizationContext().jurisdictionCode());
  }

  @Test
  void listCasesPassesDynamicSearchFilterAndSortParameters() {
    InMemoryCaseRepository caseRepository = new InMemoryCaseRepository();
    InMemoryReportRepository reportRepository = new InMemoryReportRepository();
    CapturingAuthorizationService authorizationService = new CapturingAuthorizationService();
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T10:15:30Z"), ZoneOffset.UTC);
    CaseApplicationService service =
        new CaseApplicationService(authorizationService, caseRepository, reportRepository, clock);
    ApplicationActor actor =
        new ApplicationActor("subject-3", "triage-jkt", Set.of("TRIAGE_OFFICER"), Set.of("JKT"));

    service.listCases(
        actor,
        new ListCasesQuery(
            "2026-07-14T10:15:30Z",
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            15,
            "gift",
            CaseListSearchField.TITLE,
            "disclosure",
            CaseStatus.UNDER_TRIAGE,
            "JKT-UNIT-1",
            "investigator-jkt",
            "triage-jkt",
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            CaseListSortBy.TITLE,
            SortDirection.ASC));

    assertEquals("gift", caseRepository.lastPageRequest.quickSearch());
    assertEquals(CaseListSearchField.TITLE, caseRepository.lastPageRequest.searchField());
    assertEquals("disclosure", caseRepository.lastPageRequest.searchValue());
    assertEquals(CaseStatus.UNDER_TRIAGE, caseRepository.lastPageRequest.status());
    assertEquals("JKT-UNIT-1", caseRepository.lastPageRequest.assignedUnitId());
    assertEquals("investigator-jkt", caseRepository.lastPageRequest.requestedAssigneeUserId());
    assertEquals("triage-jkt", caseRepository.lastPageRequest.createdBy());
    assertEquals(CaseListSortBy.TITLE, caseRepository.lastPageRequest.sortBy());
    assertEquals(SortDirection.ASC, caseRepository.lastPageRequest.sortDirection());
    assertEquals(16, caseRepository.lastPageRequest.limitPlusOne());
  }

  @Test
  void getCaseAuditEventsBuildsCursorAwareAuditPage() {
    InMemoryCaseRepository caseRepository = new InMemoryCaseRepository();
    InMemoryReportRepository reportRepository = new InMemoryReportRepository();
    CapturingAuthorizationService authorizationService = new CapturingAuthorizationService();
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T10:15:30Z"), ZoneOffset.UTC);
    CaseApplicationService service =
        new CaseApplicationService(authorizationService, caseRepository, reportRepository, clock);
    ApplicationActor actor =
        new ApplicationActor("subject-4", "auditor-jkt", Set.of("AUDITOR"), Set.of("JKT"));
    UUID caseId = UUID.randomUUID();
    CaseRecord caseRecord =
        new CaseRecord(
            caseId,
            "JKT-ENF-2026-00000001",
            UUID.randomUUID(),
            "Gift disclosure case",
            "Triaged into case.",
            "JKT",
            CaseStatus.CREATED,
            "JKT-UNIT-1",
            "investigator-jkt",
            Instant.parse("2026-07-14T09:00:00Z"),
            "triage-jkt",
            Instant.parse("2026-07-14T09:00:00Z"),
            "triage-jkt",
            0L);
    caseRepository.caseById.put(caseId, caseRecord);
    caseRepository.auditEventsToReturn =
        List.of(
            auditEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "CaseViewed",
                "auditor-jkt",
                Instant.parse("2026-07-14T10:03:00Z")),
            auditEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "CaseViewed",
                "auditor-jkt",
                Instant.parse("2026-07-14T10:02:00Z")),
            auditEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "CaseViewed",
                "auditor-jkt",
                Instant.parse("2026-07-14T10:01:00Z")));

    AuditEventPage page =
        service.getCaseAuditEvents(
            actor,
            caseId,
            new ListCaseAuditEventsQuery(
                null,
                null,
                2,
                "viewed",
                AuditEventListSearchField.ACTION,
                "case",
                "auditor-jkt",
                "CaseViewed",
                "CASE_VIEWED",
                "SUCCESS",
                AuditEventListSortBy.TIMESTAMP,
                SortDirection.DESC));

    assertEquals(Permission.READ_CASE_AUDIT, authorizationService.permission());
    assertEquals(caseId, caseRepository.lastAuditPageRequest.caseId());
    assertEquals("viewed", caseRepository.lastAuditPageRequest.quickSearch());
    assertEquals(
        AuditEventListSearchField.ACTION, caseRepository.lastAuditPageRequest.searchField());
    assertEquals("case", caseRepository.lastAuditPageRequest.searchValue());
    assertEquals("auditor-jkt", caseRepository.lastAuditPageRequest.actorId());
    assertEquals(AuditEventListSortBy.TIMESTAMP, caseRepository.lastAuditPageRequest.sortBy());
    assertTrue(page.hasNextPage());
    assertEquals(2, page.items().size());
    assertEquals("2026-07-14T10:02:00Z", page.nextCursorValue());
    assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000002"), page.nextCursorId());
  }

  private static AuditEvent auditEvent(
      UUID eventId, String eventType, String actorId, Instant timestamp) {
    return new AuditEvent(
        eventId,
        eventType,
        "USER",
        actorId,
        "AUDITOR",
        "CASE_VIEWED",
        "CASE",
        UUID.randomUUID().toString(),
        UUID.randomUUID(),
        timestamp,
        "corr-1",
        "127.0.0.1",
        "SUCCESS",
        null,
        null,
        null,
        "");
  }

  private static final class InMemoryCaseRepository implements CaseRepository {
    private String nextCaseNumber = "JKT-ENF-2026-00000001";
    private CaseRecord savedCaseRecord;
    private CaseStatusHistoryEntry savedHistoryEntry;
    private AuditEvent savedAuditEvent;
    private CasePageRequest lastPageRequest;
    private final Map<UUID, CaseRecord> caseById = new HashMap<>();
    private AuditEventPageRequest lastAuditPageRequest;
    private List<AuditEvent> auditEventsToReturn = List.of();

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
      return Optional.ofNullable(caseById.get(caseId));
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
    public List<AuditEvent> findAuditEventsPage(AuditEventPageRequest pageRequest) {
      this.lastAuditPageRequest = pageRequest;
      return auditEventsToReturn;
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
