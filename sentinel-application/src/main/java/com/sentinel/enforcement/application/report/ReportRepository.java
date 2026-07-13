package com.sentinel.enforcement.application.report;

import com.sentinel.enforcement.domain.report.Report;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository {

  void save(Report report);

  Optional<Report> findById(UUID reportId);
}
