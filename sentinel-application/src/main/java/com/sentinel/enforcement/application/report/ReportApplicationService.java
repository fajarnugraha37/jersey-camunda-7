package com.sentinel.enforcement.application.report;

import com.sentinel.enforcement.domain.report.Report;
import com.sentinel.enforcement.domain.report.ReportStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public final class ReportApplicationService {
  private final ReportRepository reportRepository;
  private final Clock clock;

  public ReportApplicationService(ReportRepository reportRepository, Clock clock) {
    this.reportRepository = reportRepository;
    this.clock = clock;
  }

  public Report createReport(CreateReportCommand command) {
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
            command.actorId(),
            now,
            command.actorId(),
            0L);
    reportRepository.save(report);
    return report;
  }

  public Report getReport(UUID reportId) {
    return reportRepository
        .findById(reportId)
        .orElseThrow(() -> new ReportNotFoundException(reportId));
  }
}
