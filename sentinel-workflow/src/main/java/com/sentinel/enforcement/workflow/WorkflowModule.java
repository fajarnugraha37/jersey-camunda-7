package com.sentinel.enforcement.workflow;

import com.sentinel.enforcement.application.casefile.CaseRepository;
import com.sentinel.enforcement.application.workflow.WorkflowInstanceStore;
import java.time.Clock;
import java.util.Map;
import javax.sql.DataSource;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.el.JuelExpressionManager;

public final class WorkflowModule {
  public static final String PROCESS_DEFINITION_KEY = "regulatoryEnforcementCase";
  public static final String APPEAL_PROCESS_DEFINITION_KEY = "decisionAppealReview";
  private static final String BPMN_RESOURCE = "bpmn/regulatory-enforcement-case.bpmn";
  private static final String APPEAL_BPMN_RESOURCE = "bpmn/decision-appeal-review.bpmn";

  private WorkflowModule() {}

  public static WorkflowRuntime start(
      DataSource dataSource,
      CaseRepository caseRepository,
      WorkflowInstanceStore workflowInstanceStore,
      Clock clock,
      String engineName) {
    InvestigationEscalationDelegate escalationDelegate =
        new InvestigationEscalationDelegate(caseRepository, clock);

    StandaloneProcessEngineConfiguration configuration = new StandaloneProcessEngineConfiguration();
    configuration.setProcessEngineName(engineName);
    configuration.setDataSource(dataSource);
    configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);
    configuration.setHistory(ProcessEngineConfiguration.HISTORY_FULL);
    configuration.setJobExecutorActivate(true);
    configuration.setJdbcBatchProcessing(true);
    configuration.setExpressionManager(
        new JuelExpressionManager(Map.of("investigationEscalationDelegate", escalationDelegate)));

    ProcessEngineProvider processEngineProvider =
        new SingleProcessEngineProvider(configuration.buildProcessEngine());
    CamundaServices camundaServices = new CamundaServices(processEngineProvider);
    camundaServices
        .repositoryService()
        .createDeployment()
        .name("sentinel-regulatory-enforcement")
        .enableDuplicateFiltering(true)
        .addClasspathResource(BPMN_RESOURCE)
        .addClasspathResource(APPEAL_BPMN_RESOURCE)
        .deploy();

    CamundaCaseWorkflowAdapter workflowAdapter =
        new CamundaCaseWorkflowAdapter(
            camundaServices, workflowInstanceStore, caseRepository, clock);
    CamundaWorkflowAdministrationAdapter workflowAdministrationAdapter =
        new CamundaWorkflowAdministrationAdapter(camundaServices);
    return new WorkflowRuntime(
        processEngineProvider,
        workflowAdapter,
        workflowAdministrationAdapter,
        new WorkflowReadinessProbe(processEngineProvider, camundaServices, PROCESS_DEFINITION_KEY));
  }
}
