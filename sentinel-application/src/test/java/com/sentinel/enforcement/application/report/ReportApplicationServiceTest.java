package com.sentinel.enforcement.application.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.sentinel.enforcement.domain.report.Report;
import com.sentinel.enforcement.domain.report.ReportStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportApplicationServiceTest {

  @Test
  void createReportPersistsSubmittedReportUsingCommandActorAndClock() {
    InMemoryReportRepository repository = new InMemoryReportRepository();
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T10:15:30Z"), ZoneOffset.UTC);
    ReportApplicationService service = new ReportApplicationService(repository, clock);

    Report report =
        service.createReport(
            new CreateReportCommand(
                "Suspicious procurement",
                "Potential procurement irregularity in Jakarta office.",
                "JKT",
                "Whistleblower A",
                "system-local"));

    assertNotNull(report.id());
    assertEquals(ReportStatus.SUBMITTED, report.status());
    assertEquals(Instant.parse("2026-07-14T10:15:30Z"), report.createdAt());
    assertEquals("system-local", report.createdBy());
    assertSame(report, repository.savedReports().get(report.id()));
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
}
