package com.sentinel.enforcement.bootstrap;

import com.sentinel.enforcement.application.health.HealthStatusService;
import com.sentinel.enforcement.application.report.ReportApplicationService;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

public final class ApplicationBinder extends AbstractBinder {
  private final HealthStatusService healthStatusService;
  private final ReportApplicationService reportApplicationService;

  public ApplicationBinder(
      HealthStatusService healthStatusService, ReportApplicationService reportApplicationService) {
    this.healthStatusService = healthStatusService;
    this.reportApplicationService = reportApplicationService;
  }

  @Override
  protected void configure() {
    bind(healthStatusService).to(HealthStatusService.class);
    bind(reportApplicationService).to(ReportApplicationService.class);
  }
}
