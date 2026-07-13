package com.sentinel.enforcement.bootstrap;

import com.sentinel.enforcement.application.health.HealthStatusService;
import com.sentinel.enforcement.application.report.ReportApplicationService;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.TokenVerifier;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

public final class ApplicationBinder extends AbstractBinder {
  private final HealthStatusService healthStatusService;
  private final ReportApplicationService reportApplicationService;
  private final AuthorizationService authorizationService;
  private final TokenVerifier tokenVerifier;

  public ApplicationBinder(
      HealthStatusService healthStatusService,
      ReportApplicationService reportApplicationService,
      AuthorizationService authorizationService,
      TokenVerifier tokenVerifier) {
    this.healthStatusService = healthStatusService;
    this.reportApplicationService = reportApplicationService;
    this.authorizationService = authorizationService;
    this.tokenVerifier = tokenVerifier;
  }

  @Override
  protected void configure() {
    bind(healthStatusService).to(HealthStatusService.class);
    bind(reportApplicationService).to(ReportApplicationService.class);
    bind(authorizationService).to(AuthorizationService.class);
    bind(tokenVerifier).to(TokenVerifier.class);
  }
}
