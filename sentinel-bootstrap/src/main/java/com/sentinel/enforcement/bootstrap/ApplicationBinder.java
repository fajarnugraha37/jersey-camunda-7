package com.sentinel.enforcement.bootstrap;

import com.sentinel.enforcement.application.casefile.CaseApplicationService;
import com.sentinel.enforcement.application.health.HealthStatusService;
import com.sentinel.enforcement.application.report.ReportApplicationService;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.TokenVerifier;
import com.sentinel.enforcement.application.workflow.WorkflowTaskApplicationService;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

public final class ApplicationBinder extends AbstractBinder {
  private final HealthStatusService healthStatusService;
  private final CaseApplicationService caseApplicationService;
  private final WorkflowTaskApplicationService workflowTaskApplicationService;
  private final ReportApplicationService reportApplicationService;
  private final AuthorizationService authorizationService;
  private final TokenVerifier tokenVerifier;

  public ApplicationBinder(
      HealthStatusService healthStatusService,
      CaseApplicationService caseApplicationService,
      WorkflowTaskApplicationService workflowTaskApplicationService,
      ReportApplicationService reportApplicationService,
      AuthorizationService authorizationService,
      TokenVerifier tokenVerifier) {
    this.healthStatusService = healthStatusService;
    this.caseApplicationService = caseApplicationService;
    this.workflowTaskApplicationService = workflowTaskApplicationService;
    this.reportApplicationService = reportApplicationService;
    this.authorizationService = authorizationService;
    this.tokenVerifier = tokenVerifier;
  }

  @Override
  protected void configure() {
    bind(healthStatusService).to(HealthStatusService.class);
    bind(caseApplicationService).to(CaseApplicationService.class);
    bind(workflowTaskApplicationService).to(WorkflowTaskApplicationService.class);
    bind(reportApplicationService).to(ReportApplicationService.class);
    bind(authorizationService).to(AuthorizationService.class);
    bind(tokenVerifier).to(TokenVerifier.class);
  }
}
