package com.sentinel.enforcement.bootstrap;

import com.sentinel.enforcement.application.appeal.AppealApplicationService;
import com.sentinel.enforcement.application.casefile.CaseApplicationService;
import com.sentinel.enforcement.application.decision.DecisionApplicationService;
import com.sentinel.enforcement.application.evidence.EvidenceApplicationService;
import com.sentinel.enforcement.application.health.HealthStatusService;
import com.sentinel.enforcement.application.operations.MaintenanceOperationApplicationService;
import com.sentinel.enforcement.application.recommendation.RecommendationApplicationService;
import com.sentinel.enforcement.application.report.ReportApplicationService;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.TokenVerifier;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationApplicationService;
import com.sentinel.enforcement.application.workflow.WorkflowTaskApplicationService;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

public final class ApplicationBinder extends AbstractBinder {
  private final HealthStatusService healthStatusService;
  private final CaseApplicationService caseApplicationService;
  private final EvidenceApplicationService evidenceApplicationService;
  private final RecommendationApplicationService recommendationApplicationService;
  private final DecisionApplicationService decisionApplicationService;
  private final AppealApplicationService appealApplicationService;
  private final WorkflowTaskApplicationService workflowTaskApplicationService;
  private final WorkflowReconciliationApplicationService workflowReconciliationApplicationService;
  private final MaintenanceOperationApplicationService maintenanceOperationApplicationService;
  private final ReportApplicationService reportApplicationService;
  private final AuthorizationService authorizationService;
  private final TokenVerifier tokenVerifier;

  public ApplicationBinder(
      HealthStatusService healthStatusService,
      CaseApplicationService caseApplicationService,
      EvidenceApplicationService evidenceApplicationService,
      RecommendationApplicationService recommendationApplicationService,
      DecisionApplicationService decisionApplicationService,
      AppealApplicationService appealApplicationService,
      WorkflowTaskApplicationService workflowTaskApplicationService,
      WorkflowReconciliationApplicationService workflowReconciliationApplicationService,
      MaintenanceOperationApplicationService maintenanceOperationApplicationService,
      ReportApplicationService reportApplicationService,
      AuthorizationService authorizationService,
      TokenVerifier tokenVerifier) {
    this.healthStatusService = healthStatusService;
    this.caseApplicationService = caseApplicationService;
    this.evidenceApplicationService = evidenceApplicationService;
    this.recommendationApplicationService = recommendationApplicationService;
    this.decisionApplicationService = decisionApplicationService;
    this.appealApplicationService = appealApplicationService;
    this.workflowTaskApplicationService = workflowTaskApplicationService;
    this.workflowReconciliationApplicationService = workflowReconciliationApplicationService;
    this.maintenanceOperationApplicationService = maintenanceOperationApplicationService;
    this.reportApplicationService = reportApplicationService;
    this.authorizationService = authorizationService;
    this.tokenVerifier = tokenVerifier;
  }

  @Override
  protected void configure() {
    bind(healthStatusService).to(HealthStatusService.class);
    bind(caseApplicationService).to(CaseApplicationService.class);
    bind(evidenceApplicationService).to(EvidenceApplicationService.class);
    bind(recommendationApplicationService).to(RecommendationApplicationService.class);
    bind(decisionApplicationService).to(DecisionApplicationService.class);
    bind(appealApplicationService).to(AppealApplicationService.class);
    bind(workflowTaskApplicationService).to(WorkflowTaskApplicationService.class);
    bind(workflowReconciliationApplicationService)
        .to(WorkflowReconciliationApplicationService.class);
    bind(maintenanceOperationApplicationService).to(MaintenanceOperationApplicationService.class);
    bind(reportApplicationService).to(ReportApplicationService.class);
    bind(authorizationService).to(AuthorizationService.class);
    bind(tokenVerifier).to(TokenVerifier.class);
  }
}
