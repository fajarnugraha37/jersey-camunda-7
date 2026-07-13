package com.sentinel.enforcement.application.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.Permission;
import com.sentinel.enforcement.domain.report.Report;
import com.sentinel.enforcement.domain.report.ReportStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportApplicationServiceTest {

  @Test
  void createReportPersistsSubmittedReportUsingAuthenticatedActorAndClock() {
    InMemoryReportRepository repository = new InMemoryReportRepository();
    CapturingAuthorizationService authorizationService = new CapturingAuthorizationService();
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T10:15:30Z"), ZoneOffset.UTC);
    ReportApplicationService service =
        new ReportApplicationService(authorizationService, repository, clock);
    ApplicationActor actor =
        new ApplicationActor(
            "subject-1", "intake-jkt", Set.of("CASE_INTAKE_OFFICER"), Set.of("JKT"));

    Report report =
        service.createReport(
            actor,
            new CreateReportCommand(
                "Suspicious procurement",
                "Potential procurement irregularity in Jakarta office.",
                "JKT",
                "Whistleblower A"));

    assertNotNull(report.id());
    assertEquals(ReportStatus.SUBMITTED, report.status());
    assertEquals(Instant.parse("2026-07-14T10:15:30Z"), report.createdAt());
    assertEquals("intake-jkt", report.createdBy());
    assertEquals(Permission.CREATE_REPORT, authorizationService.permission());
    assertEquals("JKT", authorizationService.authorizationContext().jurisdictionCode());
    assertSame(report, repository.savedReports().get(report.id()));
  }

  @Test
  void getReportAuthorizesAgainstPersistedReportJurisdiction() {
    InMemoryReportRepository repository = new InMemoryReportRepository();
    CapturingAuthorizationService authorizationService = new CapturingAuthorizationService();
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T10:15:30Z"), ZoneOffset.UTC);
    ReportApplicationService service =
        new ReportApplicationService(authorizationService, repository, clock);
    ApplicationActor actor =
        new ApplicationActor("subject-2", "auditor-jkt", Set.of("AUDITOR"), Set.of("JKT"));
    Report existing =
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
    repository.save(existing);

    Report fetched = service.getReport(actor, existing.id());

    assertSame(existing, fetched);
    assertEquals(Permission.READ_REPORT, authorizationService.permission());
    assertEquals(
        existing.id().toString(), authorizationService.authorizationContext().resourceId());
  }

  private static final class InMemoryReportRepository implements ReportRepository {
    private final Map<UUID, Report> savedReports = new HashMap<>();

    @Override
    public void save(Report report) {
      savedReports.put(report.id(), report);
    }

    @Override
    public Optional<Report> findById(UUID reportId) {
      return Optional.ofNullable(savedReports.get(reportId));
    }

    Map<UUID, Report> savedReports() {
      return savedReports;
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
