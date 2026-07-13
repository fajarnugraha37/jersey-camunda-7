package com.sentinel.enforcement.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sentinel.enforcement.api.generated.model.CreateReportRequest;
import com.sentinel.enforcement.api.generated.model.ErrorResponse;
import com.sentinel.enforcement.api.generated.model.HealthResponse;
import com.sentinel.enforcement.api.generated.model.ReportResponse;
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
import java.time.Duration;
import java.util.Map;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class ReportApiIT {
  private static final String REALM_NAME = "sentinel";
  private static final String CLIENT_ID = "sentinel-api";
  private static final String DEFAULT_PASSWORD = "sentinel";

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:18.3-alpine");
  private static final GenericContainer<?> KEYCLOAK =
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

  private static ApplicationRuntime applicationRuntime;
  private static Client client;

  @BeforeAll
  static void setUp() {
    POSTGRES.start();
    KEYCLOAK.start();
    applicationRuntime =
        ApplicationRuntime.start(
            AppConfiguration.fromEnvironment(
                Map.of(
                    "HTTP_PORT", "0",
                    "DB_URL", POSTGRES.getJdbcUrl(),
                    "DB_USERNAME", POSTGRES.getUsername(),
                    "DB_PASSWORD", POSTGRES.getPassword(),
                    "KEYCLOAK_ISSUER", keycloakIssuer(),
                    "KEYCLOAK_AUDIENCE", CLIENT_ID,
                    "KEYCLOAK_JWKS_URL", keycloakJwksUrl())));
    client = ClientBuilder.newBuilder().register(JacksonFeature.class).build();
  }

  @AfterAll
  static void tearDown() {
    if (client != null) {
      client.close();
    }
    if (applicationRuntime != null) {
      applicationRuntime.close();
    }
    KEYCLOAK.stop();
    POSTGRES.stop();
  }

  @Test
  void createAndGetReportRoundTripWorksForAuthorizedActors() {
    String intakeToken = accessToken("intake-jkt");
    String auditorToken = accessToken("auditor-jkt");
    ReportResponse created = createReport(intakeToken, "JKT");

    assertNotNull(created.getId());
    assertEquals("SUBMITTED", created.getStatus());

    ReportResponse fetched =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/reports/" + created.getId())
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + auditorToken)
            .get(ReportResponse.class);

    assertEquals(created.getId(), fetched.getId());
    assertEquals("Improper gift disclosure", fetched.getTitle());
  }

  @Test
  void missingBearerTokenReturnsUnauthorizedErrorEnvelope() {
    Response response =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/reports")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .post(
                Entity.entity(
                    new CreateReportRequest()
                        .title("")
                        .description("Potential violation involving unreported gifts.")
                        .jurisdictionCode("JKT")
                        .reporterName("Analyst A"),
                    MediaType.APPLICATION_JSON_TYPE));

    ErrorResponse error = response.readEntity(ErrorResponse.class);

    assertEquals(401, response.getStatus());
    assertEquals("UNAUTHENTICATED", error.getCode());
    assertNotNull(error.getCorrelationId());
  }

  @Test
  void wrongRoleReturnsForbidden() {
    Response response =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/reports")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("investigator-jkt"))
            .post(
                Entity.entity(
                    new CreateReportRequest()
                        .title("Improper gift disclosure")
                        .description("Potential violation involving unreported gifts.")
                        .jurisdictionCode("JKT")
                        .reporterName("Analyst A"),
                    MediaType.APPLICATION_JSON_TYPE));

    ErrorResponse error = response.readEntity(ErrorResponse.class);

    assertEquals(403, response.getStatus());
    assertEquals("FORBIDDEN", error.getCode());
  }

  @Test
  void wrongJurisdictionReturnsForbidden() {
    Response response =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/reports")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("intake-bdg"))
            .post(
                Entity.entity(
                    new CreateReportRequest()
                        .title("Improper gift disclosure")
                        .description("Potential violation involving unreported gifts.")
                        .jurisdictionCode("JKT")
                        .reporterName("Analyst A"),
                    MediaType.APPLICATION_JSON_TYPE));

    ErrorResponse error = response.readEntity(ErrorResponse.class);

    assertEquals(403, response.getStatus());
    assertEquals("FORBIDDEN", error.getCode());
  }

  @Test
  void invalidCreateRequestReturnsConsistentErrorEnvelope() {
    Response response =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/reports")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("intake-jkt"))
            .post(
                Entity.entity(
                    new CreateReportRequest()
                        .title("")
                        .description("Potential violation involving unreported gifts.")
                        .jurisdictionCode("JKT")
                        .reporterName("Analyst A"),
                    MediaType.APPLICATION_JSON_TYPE));

    ErrorResponse error = response.readEntity(ErrorResponse.class);

    assertEquals(400, response.getStatus());
    assertEquals("VALIDATION_ERROR", error.getCode());
    assertNotNull(error.getCorrelationId());
  }

  @Test
  void healthEndpointReportsApplicationAndDatabaseUp() {
    HealthResponse response =
        client
            .target(applicationRuntime.baseUri())
            .path("/health")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .get(HealthResponse.class);

    assertEquals("UP", response.getStatus());
    assertEquals("UP", response.getDatabase());
    assertNotNull(response.getTimestamp());
  }

  private static ReportResponse createReport(String accessToken, String jurisdictionCode) {
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

  private static String accessToken(String username) {
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

  private static String keycloakBaseUrl() {
    return "http://127.0.0.1:" + KEYCLOAK.getMappedPort(8080);
  }

  private static String keycloakIssuer() {
    return keycloakBaseUrl() + "/realms/" + REALM_NAME;
  }

  private static String keycloakJwksUrl() {
    return keycloakIssuer() + "/protocol/openid-connect/certs";
  }
}
