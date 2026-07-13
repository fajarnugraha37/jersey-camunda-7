package com.sentinel.enforcement.application.report;

import java.util.UUID;

public final class ReportNotFoundException extends RuntimeException {
  public ReportNotFoundException(UUID reportId) {
    super("Report not found: " + reportId);
  }
}
