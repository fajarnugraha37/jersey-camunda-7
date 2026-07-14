package com.sentinel.enforcement.integration;

import com.sentinel.enforcement.api.generated.model.CreateReportRequest;
import com.sentinel.enforcement.api.generated.model.ReportResponse;
import com.sentinel.enforcement.api.json.ObjectMapperContextResolver;
import com.sentinel.enforcement.bootstrap.AppConfiguration;
import com.sentinel.enforcement.bootstrap.ApplicationRuntime;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

abstract class AbstractApiIT {
  protected static final String REALM_NAME = "sentinel";
  protected static final String CLIENT_ID = "sentinel-api";
  protected static final String DEFAULT_PASSWORD = "sentinel";

  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:18.3-alpine");
  protected static final GenericContainer<?> KEYCLOAK =
      new GenericContainer<>("quay.io/keycloak/keycloak:26.6")
          .withExposedPorts(8080, 9000)
          .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
          .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
          .withEnv("KC_HEALTH_ENABLED", "true")
          .withCommand("start-dev", "--http-port=8080", "--import-realm")
          .withClasspathResourceMapping(
              "keycloak/sentinel-realm.json",
              "/opt/keycloak/data/import/sentinel-realm.json",
              BindMode.READ_ONLY)
          .waitingFor(
              Wait.forHttp("/health/ready")
                  .forPort(9000)
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(3)));

  protected static ApplicationRuntime applicationRuntime;
  protected static Client client;
  protected static AppConfiguration testConfiguration;

  @BeforeAll
  static void setUp() {
    if (!POSTGRES.isRunning()) {
      POSTGRES.start();
    }
    if (!KEYCLOAK.isRunning()) {
      KEYCLOAK.start();
    }
    if (testConfiguration == null) {
      testConfiguration =
          AppConfiguration.fromEnvironment(
              Map.of(
                  "HTTP_PORT", "0",
                  "DB_URL", POSTGRES.getJdbcUrl(),
                  "DB_USERNAME", POSTGRES.getUsername(),
                  "DB_PASSWORD", POSTGRES.getPassword(),
                  "KEYCLOAK_ISSUER", keycloakIssuer(),
                  "KEYCLOAK_AUDIENCE", CLIENT_ID,
                  "KEYCLOAK_JWKS_URL", keycloakJwksUrl(),
                  "WORKFLOW_INVESTIGATION_ESCALATION_DURATION", "PT2S"));
    }
    if (applicationRuntime == null) {
      ApplicationRuntime.migrate(testConfiguration);
      applicationRuntime = ApplicationRuntime.start(testConfiguration);
    }
    if (client == null) {
      client =
          ClientBuilder.newBuilder()
              .register(JacksonFeature.class)
              .register(ObjectMapperContextResolver.class)
              .build();
    }
  }

  @AfterAll
  static void tearDown() {
    if (client != null) {
      client.close();
      client = null;
    }
    if (applicationRuntime != null) {
      applicationRuntime.close();
      applicationRuntime = null;
    }
    testConfiguration = null;
    KEYCLOAK.stop();
    POSTGRES.stop();
  }

  protected static ReportResponse createReport(String accessToken, String jurisdictionCode) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/reports")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(
            Entity.entity(
                new CreateReportRequest()
                    .title("Improper gift disclosure")
                    .description("Potential violation involving unreported gifts.")
                    .jurisdictionCode(jurisdictionCode)
                    .reporterName("Analyst A"),
                MediaType.APPLICATION_JSON_TYPE),
            ReportResponse.class);
  }

  protected static String accessToken(String username) {
    Response response =
        client
            .target(keycloakBaseUrl())
            .path("/realms/" + REALM_NAME + "/protocol/openid-connect/token")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .post(
                Entity.entity(
                    new Form()
                        .param("client_id", CLIENT_ID)
                        .param("grant_type", "password")
                        .param("username", username)
                        .param("password", DEFAULT_PASSWORD),
                    MediaType.APPLICATION_FORM_URLENCODED_TYPE));

    Map<String, Object> payload = response.readEntity(new GenericType<>() {});
    Object accessToken = payload.get("access_token");
    if (response.getStatus() != 200 || accessToken == null) {
      throw new IllegalStateException(
          "Failed to obtain access token for user "
              + username
              + ": status="
              + response.getStatus()
              + ", payload="
              + payload);
    }
    return accessToken.toString();
  }

  protected static long countByCaseId(String tableName, UUID caseId) {
    String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE case_id = ?";
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, caseId);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getLong(1);
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to count rows in " + tableName, exception);
    }
  }

  protected static long countAuditEventsByType(UUID caseId, String eventType) {
    String sql = "SELECT COUNT(*) FROM audit_event WHERE case_id = ? AND event_type = ?";
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, caseId);
      statement.setString(2, eventType);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getLong(1);
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to count audit events for case " + caseId, exception);
    }
  }

  protected static String workflowStatus(UUID caseId) {
    String sql = "SELECT status FROM workflow_instance WHERE case_id = ?";
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, caseId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return null;
        }
        return resultSet.getString(1);
      }
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Failed to load workflow status for case " + caseId, exception);
    }
  }

  protected static String keycloakBaseUrl() {
    return "http://127.0.0.1:" + KEYCLOAK.getMappedPort(8080);
  }

  private static String keycloakIssuer() {
    return keycloakBaseUrl() + "/realms/" + REALM_NAME;
  }

  private static String keycloakJwksUrl() {
    return keycloakIssuer() + "/protocol/openid-connect/certs";
  }
}
