package com.sentinel.enforcement.bootstrap;

import com.sentinel.enforcement.api.casefile.CaseResource;
import com.sentinel.enforcement.api.error.AuthorizationDeniedExceptionMapper;
import com.sentinel.enforcement.api.error.BadRequestExceptionMapper;
import com.sentinel.enforcement.api.error.CaseConflictExceptionMapper;
import com.sentinel.enforcement.api.error.CaseNotFoundExceptionMapper;
import com.sentinel.enforcement.api.error.ConstraintViolationExceptionMapper;
import com.sentinel.enforcement.api.error.CorrelationIdFilter;
import com.sentinel.enforcement.api.error.EvidenceConflictExceptionMapper;
import com.sentinel.enforcement.api.error.EvidenceNotFoundExceptionMapper;
import com.sentinel.enforcement.api.error.EvidenceObjectMissingExceptionMapper;
import com.sentinel.enforcement.api.error.EvidenceStorageUnavailableExceptionMapper;
import com.sentinel.enforcement.api.error.GenericExceptionMapper;
import com.sentinel.enforcement.api.error.ReportConflictExceptionMapper;
import com.sentinel.enforcement.api.error.ReportNotFoundExceptionMapper;
import com.sentinel.enforcement.api.error.UnauthenticatedExceptionMapper;
import com.sentinel.enforcement.api.error.WorkflowReconciliationConflictExceptionMapper;
import com.sentinel.enforcement.api.error.WorkflowTaskConflictExceptionMapper;
import com.sentinel.enforcement.api.error.WorkflowTaskNotFoundExceptionMapper;
import com.sentinel.enforcement.api.evidence.EvidenceResource;
import com.sentinel.enforcement.api.health.HealthResource;
import com.sentinel.enforcement.api.json.ObjectMapperContextResolver;
import com.sentinel.enforcement.api.report.ReportResource;
import com.sentinel.enforcement.api.security.BearerAuthenticationFilter;
import com.sentinel.enforcement.api.workflow.TaskResource;
import com.sentinel.enforcement.api.workflow.WorkflowReconciliationResource;
import com.sentinel.enforcement.application.casefile.CaseApplicationService;
import com.sentinel.enforcement.application.evidence.EvidenceApplicationService;
import com.sentinel.enforcement.application.health.HealthStatusService;
import com.sentinel.enforcement.application.report.ReportApplicationService;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.TokenVerifier;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationApplicationService;
import com.sentinel.enforcement.application.workflow.WorkflowTaskApplicationService;
import com.sentinel.enforcement.persistence.PersistenceModule;
import com.sentinel.enforcement.persistence.casefile.CaseRepositoryMyBatisAdapter;
import com.sentinel.enforcement.persistence.evidence.EvidenceRepositoryMyBatisAdapter;
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
  private final HttpServer server;
  private final URI baseUri;

  private ApplicationRuntime(
      HikariDataSource dataSource,
      WorkflowRuntime workflowRuntime,
      HttpServer server,
      URI baseUri) {
    this.dataSource = dataSource;
    this.workflowRuntime = workflowRuntime;
    this.server = server;
    this.baseUri = baseUri;
  }

  public static ApplicationRuntime start(AppConfiguration configuration) {
    HikariDataSource dataSource = createDataSource(configuration);
    WorkflowRuntime workflowRuntime = null;
    try {
      Clock clock = Clock.systemUTC();
      SqlSessionFactory sqlSessionFactory = PersistenceModule.createSqlSessionFactory(dataSource);
      AuthorizationService authorizationService = new RoleBasedAuthorizationService();
      CaseRepositoryMyBatisAdapter caseRepository =
          new CaseRepositoryMyBatisAdapter(sqlSessionFactory);
      EvidenceRepositoryMyBatisAdapter evidenceRepository =
          new EvidenceRepositoryMyBatisAdapter(sqlSessionFactory);
      ReportRepositoryMyBatisAdapter reportRepository =
          new ReportRepositoryMyBatisAdapter(sqlSessionFactory);
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
              caseRepository,
              evidenceRepository,
              evidenceStorage,
              clock,
              configuration.minioEvidenceBucket(),
              configuration.evidenceUploadUrlTtl(),
              configuration.evidenceDownloadUrlTtl());
      CaseApplicationService caseApplicationService =
          new CaseApplicationService(
              authorizationService,
              caseRepository,
              reportRepository,
              workflowRuntime.caseWorkflowPort(),
              configuration.workflowInvestigationEscalationDuration(),
              clock);
      WorkflowTaskApplicationService workflowTaskApplicationService =
          new WorkflowTaskApplicationService(
              authorizationService,
              caseRepository,
              caseApplicationService,
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

      ResourceConfig resourceConfig =
          new ResourceConfig()
              .register(
                  new ApplicationBinder(
                      healthStatusService,
                      caseApplicationService,
                      evidenceApplicationService,
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
              .register(CaseConflictExceptionMapper.class)
              .register(CaseNotFoundExceptionMapper.class)
              .register(EvidenceConflictExceptionMapper.class)
              .register(EvidenceNotFoundExceptionMapper.class)
              .register(EvidenceObjectMissingExceptionMapper.class)
              .register(EvidenceStorageUnavailableExceptionMapper.class)
              .register(ReportConflictExceptionMapper.class)
              .register(ReportNotFoundExceptionMapper.class)
              .register(WorkflowReconciliationConflictExceptionMapper.class)
              .register(WorkflowTaskConflictExceptionMapper.class)
              .register(WorkflowTaskNotFoundExceptionMapper.class)
              .register(GenericExceptionMapper.class)
              .register(HealthResource.class)
              .register(CaseResource.class)
              .register(EvidenceResource.class)
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
        workflowRuntime.close();
        dataSource.close();
        throw new IllegalStateException("Failed to start HTTP server", exception);
      }

      int actualPort = server.getListeners().iterator().next().getPort();
      URI baseUri = URI.create("http://127.0.0.1:" + actualPort + "/");
      LOGGER.info("Sentinel app started at {}", baseUri);
      return new ApplicationRuntime(dataSource, workflowRuntime, server, baseUri);
    } catch (RuntimeException exception) {
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

  public URI baseUri() {
    return baseUri;
  }

  @Override
  public void close() {
    if (server != null) {
      server.shutdownNow();
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
