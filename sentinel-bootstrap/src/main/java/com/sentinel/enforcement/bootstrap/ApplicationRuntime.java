package com.sentinel.enforcement.bootstrap;

import com.sentinel.enforcement.api.casefile.CaseResource;
import com.sentinel.enforcement.api.decision.CaseDecisionResource;
import com.sentinel.enforcement.api.recommendation.CaseRecommendationResource;
import com.sentinel.enforcement.api.appeal.AppealResource;
import com.sentinel.enforcement.api.decision.DecisionResource;
import com.sentinel.enforcement.api.error.AuthorizationDeniedExceptionMapper;
import com.sentinel.enforcement.api.error.AppealConflictExceptionMapper;
import com.sentinel.enforcement.api.error.AppealNotFoundExceptionMapper;
import com.sentinel.enforcement.api.error.BadRequestExceptionMapper;
import com.sentinel.enforcement.api.error.CaseConflictExceptionMapper;
import com.sentinel.enforcement.api.error.CaseNotFoundExceptionMapper;
import com.sentinel.enforcement.api.error.ConstraintViolationExceptionMapper;
import com.sentinel.enforcement.api.error.CorrelationIdFilter;
import com.sentinel.enforcement.api.error.DecisionConflictExceptionMapper;
import com.sentinel.enforcement.api.error.DecisionNotFoundExceptionMapper;
import com.sentinel.enforcement.api.error.EvidenceConflictExceptionMapper;
import com.sentinel.enforcement.api.error.EvidenceNotFoundExceptionMapper;
import com.sentinel.enforcement.api.error.EvidenceObjectMissingExceptionMapper;
import com.sentinel.enforcement.api.error.EvidenceStorageUnavailableExceptionMapper;
import com.sentinel.enforcement.api.error.GenericExceptionMapper;
import com.sentinel.enforcement.api.error.NotFoundExceptionMapper;
import com.sentinel.enforcement.api.error.RecommendationConflictExceptionMapper;
import com.sentinel.enforcement.api.error.RecommendationNotFoundExceptionMapper;
import com.sentinel.enforcement.api.error.ReportConflictExceptionMapper;
import com.sentinel.enforcement.api.error.ReportNotFoundExceptionMapper;
import com.sentinel.enforcement.api.error.UnauthenticatedExceptionMapper;
import com.sentinel.enforcement.api.error.WorkflowReconciliationConflictExceptionMapper;
import com.sentinel.enforcement.api.error.WorkflowTaskConflictExceptionMapper;
import com.sentinel.enforcement.api.error.WorkflowTaskNotFoundExceptionMapper;
import com.sentinel.enforcement.api.evidence.CaseEvidenceResource;
import com.sentinel.enforcement.api.evidence.EvidenceResource;
import com.sentinel.enforcement.api.health.HealthResource;
import com.sentinel.enforcement.api.json.ObjectMapperContextResolver;
import com.sentinel.enforcement.api.recommendation.RecommendationResource;
import com.sentinel.enforcement.api.report.ReportResource;
import com.sentinel.enforcement.api.security.BearerAuthenticationFilter;
import com.sentinel.enforcement.api.workflow.TaskResource;
import com.sentinel.enforcement.api.workflow.WorkflowReconciliationResource;
import com.sentinel.enforcement.application.casefile.CaseApplicationService;
import com.sentinel.enforcement.application.casefile.PhaseSevenCaseProgressionGuard;
import com.sentinel.enforcement.application.appeal.AppealApplicationService;
import com.sentinel.enforcement.application.appeal.AppealRepository;
import com.sentinel.enforcement.application.decision.DecisionApplicationService;
import com.sentinel.enforcement.application.decision.DecisionRepository;
import com.sentinel.enforcement.application.evidence.EvidenceApplicationService;
import com.sentinel.enforcement.application.health.HealthStatusService;
import com.sentinel.enforcement.application.messaging.ApplicationTransactionManager;
import com.sentinel.enforcement.application.messaging.InboxRepository;
import com.sentinel.enforcement.application.messaging.NotificationRepository;
import com.sentinel.enforcement.application.messaging.OutboxRepository;
import com.sentinel.enforcement.application.recommendation.RecommendationApplicationService;
import com.sentinel.enforcement.application.recommendation.RecommendationRepository;
import com.sentinel.enforcement.application.report.ReportApplicationService;
import com.sentinel.enforcement.application.sanction.SanctionRepository;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.TokenVerifier;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationApplicationService;
import com.sentinel.enforcement.application.workflow.WorkflowTaskApplicationService;
import com.sentinel.enforcement.messaging.MessagingRuntime;
import com.sentinel.enforcement.messaging.MessagingRuntimeConfiguration;
import com.sentinel.enforcement.persistence.MyBatisTransactionManager;
import com.sentinel.enforcement.persistence.PersistenceModule;
import com.sentinel.enforcement.persistence.appeal.AppealRepositoryMyBatisAdapter;
import com.sentinel.enforcement.persistence.casefile.CaseRepositoryMyBatisAdapter;
import com.sentinel.enforcement.persistence.decision.DecisionRepositoryMyBatisAdapter;
import com.sentinel.enforcement.persistence.decision.SanctionRepositoryMyBatisAdapter;
import com.sentinel.enforcement.persistence.evidence.EvidenceRepositoryMyBatisAdapter;
import com.sentinel.enforcement.persistence.messaging.InboxRepositoryMyBatisAdapter;
import com.sentinel.enforcement.persistence.messaging.NotificationRepositoryMyBatisAdapter;
import com.sentinel.enforcement.persistence.messaging.OutboxRepositoryMyBatisAdapter;
import com.sentinel.enforcement.persistence.recommendation.RecommendationRepositoryMyBatisAdapter;
import com.sentinel.enforcement.persistence.report.ReportRepositoryMyBatisAdapter;
import com.sentinel.enforcement.persistence.workflow.WorkflowInstanceMyBatisAdapter;
import com.sentinel.enforcement.persistence.workflow.WorkflowReconciliationMyBatisAdapter;
import com.sentinel.enforcement.security.KeycloakSecurityConfiguration;
import com.sentinel.enforcement.security.KeycloakTokenVerifier;
import com.sentinel.enforcement.security.RoleBasedAuthorizationService;
import com.sentinel.enforcement.storage.MinioEvidenceStorageAdapter;
import com.sentinel.enforcement.workflow.WorkflowModule;
import com.sentinel.enforcement.workflow.WorkflowRuntime;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.time.Clock;
import org.apache.ibatis.session.SqlSessionFactory;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ApplicationRuntime implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationRuntime.class);

  private final HikariDataSource dataSource;
  private final WorkflowRuntime workflowRuntime;
  private final MessagingRuntime messagingRuntime;
  private final HttpServer server;
  private final URI baseUri;

  private ApplicationRuntime(
      HikariDataSource dataSource,
      WorkflowRuntime workflowRuntime,
      MessagingRuntime messagingRuntime,
      HttpServer server,
      URI baseUri) {
    this.dataSource = dataSource;
    this.workflowRuntime = workflowRuntime;
    this.messagingRuntime = messagingRuntime;
    this.server = server;
    this.baseUri = baseUri;
  }

  public static ApplicationRuntime start(AppConfiguration configuration) {
    HikariDataSource dataSource = createDataSource(configuration);
    WorkflowRuntime workflowRuntime = null;
    MessagingRuntime messagingRuntime = null;
    try {
      Clock clock = Clock.systemUTC();
      SqlSessionFactory sqlSessionFactory = PersistenceModule.createSqlSessionFactory(dataSource);
      AuthorizationService authorizationService = new RoleBasedAuthorizationService();
      ApplicationTransactionManager transactionManager =
          new MyBatisTransactionManager(sqlSessionFactory);
      CaseRepositoryMyBatisAdapter caseRepository =
          new CaseRepositoryMyBatisAdapter(sqlSessionFactory);
      EvidenceRepositoryMyBatisAdapter evidenceRepository =
          new EvidenceRepositoryMyBatisAdapter(sqlSessionFactory);
      ReportRepositoryMyBatisAdapter reportRepository =
          new ReportRepositoryMyBatisAdapter(sqlSessionFactory);
      RecommendationRepository recommendationRepository =
          new RecommendationRepositoryMyBatisAdapter(sqlSessionFactory);
      DecisionRepository decisionRepository = new DecisionRepositoryMyBatisAdapter(sqlSessionFactory);
      SanctionRepository sanctionRepository = new SanctionRepositoryMyBatisAdapter(sqlSessionFactory);
      AppealRepository appealRepository = new AppealRepositoryMyBatisAdapter(sqlSessionFactory);
      OutboxRepository outboxRepository = new OutboxRepositoryMyBatisAdapter(sqlSessionFactory);
      InboxRepository inboxRepository = new InboxRepositoryMyBatisAdapter(sqlSessionFactory);
      NotificationRepository notificationRepository =
          new NotificationRepositoryMyBatisAdapter(sqlSessionFactory);
      MinioEvidenceStorageAdapter evidenceStorage =
          new MinioEvidenceStorageAdapter(
              configuration.minioEndpoint(),
              configuration.minioAccessKey(),
              configuration.minioSecretKey());
      evidenceStorage.ensureBucketExists(configuration.minioEvidenceBucket());
      WorkflowInstanceMyBatisAdapter workflowInstanceStore =
          new WorkflowInstanceMyBatisAdapter(sqlSessionFactory);
      WorkflowReconciliationMyBatisAdapter workflowReconciliationQueryPort =
          new WorkflowReconciliationMyBatisAdapter(sqlSessionFactory);
      workflowRuntime =
          WorkflowModule.start(
              dataSource,
              caseRepository,
              workflowInstanceStore,
              clock,
              configuration.workflowEngineName());
      TokenVerifier tokenVerifier =
          new KeycloakTokenVerifier(
              new KeycloakSecurityConfiguration(
                  URI.create(configuration.keycloakIssuer()),
                  configuration.keycloakAudience(),
                  URI.create(configuration.keycloakJwksUrl())),
              clock);
      ReportApplicationService reportApplicationService =
          new ReportApplicationService(authorizationService, reportRepository, clock);
      EvidenceApplicationService evidenceApplicationService =
          new EvidenceApplicationService(
              authorizationService,
              transactionManager,
              caseRepository,
              evidenceRepository,
              outboxRepository,
              evidenceStorage,
              clock,
              configuration.minioEvidenceBucket(),
              configuration.evidenceUploadUrlTtl(),
              configuration.evidenceDownloadUrlTtl());
      CaseApplicationService caseApplicationService =
          new CaseApplicationService(
              authorizationService,
              transactionManager,
              caseRepository,
              reportRepository,
              outboxRepository,
              new PhaseSevenCaseProgressionGuard(
                  recommendationRepository,
                  decisionRepository,
                  appealRepository,
                  sanctionRepository),
              workflowRuntime.caseWorkflowPort(),
              configuration.workflowInvestigationEscalationDuration(),
              clock);
      RecommendationApplicationService recommendationApplicationService =
          new RecommendationApplicationService(
              authorizationService,
              transactionManager,
              caseRepository,
              recommendationRepository,
              clock);
      DecisionApplicationService decisionApplicationService =
          new DecisionApplicationService(
              authorizationService,
              transactionManager,
              caseRepository,
              recommendationRepository,
              decisionRepository,
              outboxRepository,
              clock);
      AppealApplicationService appealApplicationService =
          new AppealApplicationService(
              authorizationService,
              transactionManager,
              caseRepository,
              decisionRepository,
              appealRepository,
              sanctionRepository,
              outboxRepository,
              workflowRuntime.caseWorkflowPort(),
              clock);
      WorkflowTaskApplicationService workflowTaskApplicationService =
          new WorkflowTaskApplicationService(
              authorizationService,
              caseRepository,
              caseApplicationService,
              recommendationRepository,
              decisionRepository,
              appealApplicationService,
              workflowRuntime.caseWorkflowPort());
      WorkflowReconciliationApplicationService workflowReconciliationApplicationService =
          new WorkflowReconciliationApplicationService(
              authorizationService,
              workflowReconciliationQueryPort,
              workflowRuntime.workflowAdministrationPort(),
              workflowInstanceStore,
              caseRepository,
              clock);
      HealthStatusService healthStatusService =
          new WorkflowAwareHealthStatusService(
              new DatabaseHealthService(dataSource, clock), workflowRuntime, clock);
      messagingRuntime =
          MessagingRuntime.start(
              new MessagingRuntimeConfiguration(
                  configuration.kafkaBootstrapServers(),
                  configuration.appInstanceId(),
                  configuration.outboxPollInterval(),
                  configuration.outboxLeaseDuration(),
                  configuration.outboxBatchSize(),
                  configuration.notificationConsumerGroupId(),
                  configuration.notificationMaxRetries()),
              transactionManager,
              outboxRepository,
              inboxRepository,
              notificationRepository,
              clock);

      ResourceConfig resourceConfig =
          new ResourceConfig()
              .register(
                  new ApplicationBinder(
                      healthStatusService,
                      caseApplicationService,
                      evidenceApplicationService,
                      recommendationApplicationService,
                      decisionApplicationService,
                      appealApplicationService,
                      workflowTaskApplicationService,
                      workflowReconciliationApplicationService,
                      reportApplicationService,
                      authorizationService,
                      tokenVerifier))
              .register(JacksonFeature.class)
              .register(ObjectMapperContextResolver.class)
              .register(CorrelationIdFilter.class)
              .register(BearerAuthenticationFilter.class)
              .register(ConstraintViolationExceptionMapper.class)
              .register(BadRequestExceptionMapper.class)
              .register(UnauthenticatedExceptionMapper.class)
               .register(AuthorizationDeniedExceptionMapper.class)
               .register(AppealConflictExceptionMapper.class)
               .register(AppealNotFoundExceptionMapper.class)
               .register(CaseConflictExceptionMapper.class)
               .register(CaseNotFoundExceptionMapper.class)
               .register(DecisionConflictExceptionMapper.class)
               .register(DecisionNotFoundExceptionMapper.class)
               .register(EvidenceConflictExceptionMapper.class)
               .register(EvidenceNotFoundExceptionMapper.class)
              .register(EvidenceObjectMissingExceptionMapper.class)
              .register(EvidenceStorageUnavailableExceptionMapper.class)
               .register(NotFoundExceptionMapper.class)
               .register(RecommendationConflictExceptionMapper.class)
               .register(RecommendationNotFoundExceptionMapper.class)
               .register(ReportConflictExceptionMapper.class)
               .register(ReportNotFoundExceptionMapper.class)
              .register(WorkflowReconciliationConflictExceptionMapper.class)
              .register(WorkflowTaskConflictExceptionMapper.class)
              .register(WorkflowTaskNotFoundExceptionMapper.class)
              .register(GenericExceptionMapper.class)
               .register(HealthResource.class)
               .register(AppealResource.class)
               .register(CaseResource.class)
               .register(CaseDecisionResource.class)
               .register(CaseEvidenceResource.class)
               .register(CaseRecommendationResource.class)
               .register(DecisionResource.class)
               .register(EvidenceResource.class)
               .register(RecommendationResource.class)
               .register(ReportResource.class)
              .register(TaskResource.class)
              .register(WorkflowReconciliationResource.class)
              .property(ServerProperties.WADL_FEATURE_DISABLE, true);

      HttpServer server =
          GrizzlyHttpServerFactory.createHttpServer(
              URI.create("http://0.0.0.0:" + configuration.httpPort() + "/"),
              resourceConfig,
              false);
      try {
        server.start();
      } catch (Exception exception) {
        messagingRuntime.close();
        workflowRuntime.close();
        dataSource.close();
        throw new IllegalStateException("Failed to start HTTP server", exception);
      }

      int actualPort = server.getListeners().iterator().next().getPort();
      URI baseUri = URI.create("http://127.0.0.1:" + actualPort + "/");
      LOGGER.info("Sentinel app started at {}", baseUri);
      return new ApplicationRuntime(dataSource, workflowRuntime, messagingRuntime, server, baseUri);
    } catch (RuntimeException exception) {
      if (messagingRuntime != null) {
        messagingRuntime.close();
      }
      if (workflowRuntime != null) {
        workflowRuntime.close();
      }
      dataSource.close();
      throw exception;
    }
  }

  public static void migrate(AppConfiguration configuration) {
    try (HikariDataSource dataSource = createDataSource(configuration)) {
      LiquibaseMigrator.migrate(dataSource);
      CamundaSchemaMigrator.migrate(dataSource);
    }
  }

  public static void rollback(AppConfiguration configuration, int rollbackCount) {
    try (HikariDataSource dataSource = createDataSource(configuration)) {
      LiquibaseMigrator.rollbackCount(dataSource, rollbackCount);
    }
  }

  public URI baseUri() {
    return baseUri;
  }

  @Override
  public void close() {
    if (server != null) {
      server.shutdownNow();
    }
    if (messagingRuntime != null) {
      messagingRuntime.close();
    }
    if (workflowRuntime != null) {
      workflowRuntime.close();
    }
    dataSource.close();
  }

  private static HikariDataSource createDataSource(AppConfiguration configuration) {
    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(configuration.dbUrl());
    hikariConfig.setUsername(configuration.dbUsername());
    hikariConfig.setPassword(configuration.dbPassword());
    hikariConfig.setMaximumPoolSize(4);
    hikariConfig.setMinimumIdle(1);
    hikariConfig.setConnectionTimeout(5000);
    hikariConfig.setValidationTimeout(2000);
    hikariConfig.setInitializationFailTimeout(5000);
    hikariConfig.setLeakDetectionThreshold(30000);
    hikariConfig.setConnectionInitSql("SET statement_timeout = '10s'");
    hikariConfig.addDataSourceProperty("connectTimeout", "5");
    hikariConfig.addDataSourceProperty("socketTimeout", "30");
    hikariConfig.setPoolName("sentinel-hikari");
    return new HikariDataSource(hikariConfig);
  }
}
