package com.sentinel.enforcement.application.report;

import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.Permission;
import com.sentinel.enforcement.domain.report.Report;
import com.sentinel.enforcement.domain.report.ReportStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public final class ReportApplicationService {
  private static final String REPORT_RESOURCE_TYPE = "REPORT";

  private final AuthorizationService authorizationService;
  private final ReportRepository reportRepository;
  private final Clock clock;

  public ReportApplicationService(
      AuthorizationService authorizationService, ReportRepository reportRepository, Clock clock) {
    this.authorizationService = authorizationService;
    this.reportRepository = reportRepository;
    this.clock = clock;
  }

  public Report createReport(ApplicationActor actor, CreateReportCommand command) {
    authorizationService.requirePermission(
        actor,
        Permission.CREATE_REPORT,
        new AuthorizationContext(command.jurisdictionCode(), REPORT_RESOURCE_TYPE, null, null));

    Instant now = clock.instant();
    Report report =
        new Report(
            UUID.randomUUID(),
            command.title(),
            command.description(),
            command.jurisdictionCode(),
            command.reporterName(),
            ReportStatus.SUBMITTED,
            now,
            actor.username(),
            now,
            actor.username(),
            0L);
    reportRepository.save(report);
    return report;
  }

  public Report getReport(ApplicationActor actor, UUID reportId) {
    Report report =
        reportRepository
            .findById(reportId)
            .orElseThrow(() -> new ReportNotFoundException(reportId));
    authorizationService.requirePermission(
        actor,
        Permission.READ_REPORT,
        new AuthorizationContext(
            report.jurisdictionCode(), REPORT_RESOURCE_TYPE, report.id().toString(), null));
    return report;
  }
}
