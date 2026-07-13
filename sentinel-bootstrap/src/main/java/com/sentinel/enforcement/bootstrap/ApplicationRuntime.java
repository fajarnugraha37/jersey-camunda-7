package com.sentinel.enforcement.bootstrap;

import com.sentinel.enforcement.api.error.AuthorizationDeniedExceptionMapper;
import com.sentinel.enforcement.api.error.BadRequestExceptionMapper;
import com.sentinel.enforcement.api.error.ConstraintViolationExceptionMapper;
import com.sentinel.enforcement.api.error.CorrelationIdFilter;
import com.sentinel.enforcement.api.error.GenericExceptionMapper;
import com.sentinel.enforcement.api.error.ReportNotFoundExceptionMapper;
import com.sentinel.enforcement.api.error.UnauthenticatedExceptionMapper;
import com.sentinel.enforcement.api.health.HealthResource;
import com.sentinel.enforcement.api.json.ObjectMapperContextResolver;
import com.sentinel.enforcement.api.report.ReportResource;
import com.sentinel.enforcement.api.security.BearerAuthenticationFilter;
import com.sentinel.enforcement.application.health.HealthStatusService;
import com.sentinel.enforcement.application.report.ReportApplicationService;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.TokenVerifier;
import com.sentinel.enforcement.persistence.PersistenceModule;
import com.sentinel.enforcement.persistence.report.ReportRepositoryMyBatisAdapter;
import com.sentinel.enforcement.security.KeycloakSecurityConfiguration;
import com.sentinel.enforcement.security.KeycloakTokenVerifier;
import com.sentinel.enforcement.security.RoleBasedAuthorizationService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.time.Clock;
import org.apache.ibatis.session.SqlSessionFactory;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ApplicationRuntime implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationRuntime.class);

  private final HikariDataSource dataSource;
  private final HttpServer server;
  private final URI baseUri;

  private ApplicationRuntime(HikariDataSource dataSource, HttpServer server, URI baseUri) {
    this.dataSource = dataSource;
    this.server = server;
    this.baseUri = baseUri;
  }

  public static ApplicationRuntime start(AppConfiguration configuration) {
    HikariDataSource dataSource = createDataSource(configuration);
    LiquibaseMigrator.migrate(dataSource);

    Clock clock = Clock.systemUTC();
    SqlSessionFactory sqlSessionFactory = PersistenceModule.createSqlSessionFactory(dataSource);
    AuthorizationService authorizationService = new RoleBasedAuthorizationService();
    TokenVerifier tokenVerifier =
        new KeycloakTokenVerifier(
            new KeycloakSecurityConfiguration(
                URI.create(configuration.keycloakIssuer()),
                configuration.keycloakAudience(),
                URI.create(configuration.keycloakJwksUrl())),
            clock);
    ReportApplicationService reportApplicationService =
        new ReportApplicationService(
            authorizationService, new ReportRepositoryMyBatisAdapter(sqlSessionFactory), clock);
    HealthStatusService healthStatusService = new DatabaseHealthService(dataSource, clock);

    ResourceConfig resourceConfig =
        new ResourceConfig()
            .register(
                new ApplicationBinder(
                    healthStatusService,
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
            .register(ReportNotFoundExceptionMapper.class)
            .register(GenericExceptionMapper.class)
            .register(HealthResource.class)
            .register(ReportResource.class);

    HttpServer server =
        GrizzlyHttpServerFactory.createHttpServer(
            URI.create("http://0.0.0.0:" + configuration.httpPort() + "/"), resourceConfig, false);
    try {
      server.start();
    } catch (Exception exception) {
      dataSource.close();
      throw new IllegalStateException("Failed to start HTTP server", exception);
    }

    int actualPort = server.getListeners().iterator().next().getPort();
    URI baseUri = URI.create("http://127.0.0.1:" + actualPort + "/");
    LOGGER.info("Sentinel app started at {}", baseUri);
    return new ApplicationRuntime(dataSource, server, baseUri);
  }

  public static void migrate(AppConfiguration configuration) {
    try (HikariDataSource dataSource = createDataSource(configuration)) {
      LiquibaseMigrator.migrate(dataSource);
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
    dataSource.close();
  }

  private static HikariDataSource createDataSource(AppConfiguration configuration) {
    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(configuration.dbUrl());
    hikariConfig.setUsername(configuration.dbUsername());
    hikariConfig.setPassword(configuration.dbPassword());
    hikariConfig.setMaximumPoolSize(4);
    hikariConfig.setConnectionTimeout(5000);
    hikariConfig.setValidationTimeout(2000);
    hikariConfig.setPoolName("sentinel-hikari");
    return new HikariDataSource(hikariConfig);
  }
}
