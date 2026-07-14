package com.sentinel.enforcement.workflow;

import com.sentinel.enforcement.application.casefile.CaseRepository;
import java.time.Clock;
import java.util.Map;
import javax.sql.DataSource;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.el.JuelExpressionManager;

public final class WorkflowModule {
  private static final String BPMN_RESOURCE = "bpmn/regulatory-enforcement-case.bpmn";

  private WorkflowModule() {}

  public static WorkflowRuntime start(
      DataSource dataSource, CaseRepository caseRepository, Clock clock, String engineName) {
    InvestigationEscalationDelegate escalationDelegate =
        new InvestigationEscalationDelegate(caseRepository, clock);

    StandaloneProcessEngineConfiguration configuration = new StandaloneProcessEngineConfiguration();
    configuration.setProcessEngineName(engineName);
    configuration.setDataSource(dataSource);
    configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
    configuration.setHistory(ProcessEngineConfiguration.HISTORY_FULL);
    configuration.setJobExecutorActivate(true);
    configuration.setJdbcBatchProcessing(true);
    configuration.setExpressionManager(
        new JuelExpressionManager(Map.of("investigationEscalationDelegate", escalationDelegate)));

    ProcessEngine processEngine = configuration.buildProcessEngine();
    processEngine
        .getRepositoryService()
        .createDeployment()
        .name("sentinel-regulatory-enforcement")
        .enableDuplicateFiltering(true)
        .addClasspathResource(BPMN_RESOURCE)
        .deploy();

    WorkflowInstanceJdbcStore workflowInstanceStore = new WorkflowInstanceJdbcStore(dataSource);
    CamundaCaseWorkflowAdapter workflowAdapter =
        new CamundaCaseWorkflowAdapter(processEngine, workflowInstanceStore, caseRepository, clock);
    return new WorkflowRuntime(processEngine, workflowAdapter);
  }
}
