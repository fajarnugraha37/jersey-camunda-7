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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class ReportApiIT {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:18.3-alpine");

  private static ApplicationRuntime applicationRuntime;
  private static Client client;

  @BeforeAll
  static void setUp() {
    POSTGRES.start();
    applicationRuntime =
        ApplicationRuntime.start(
            AppConfiguration.fromEnvironment(
                Map.of(
                    "HTTP_PORT", "0",
                    "DB_URL", POSTGRES.getJdbcUrl(),
                    "DB_USERNAME", POSTGRES.getUsername(),
                    "DB_PASSWORD", POSTGRES.getPassword())));
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
    POSTGRES.stop();
  }

  @Test
  void createAndGetReportRoundTripWorksAgainstMigratedDatabase() {
    ReportResponse created =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/reports")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .post(
                Entity.entity(
                    new CreateReportRequest()
                        .title("Improper gift disclosure")
                        .description("Potential violation involving unreported gifts.")
                        .jurisdictionCode("JKT")
                        .reporterName("Analyst A"),
                    MediaType.APPLICATION_JSON_TYPE),
                ReportResponse.class);

    assertNotNull(created.getId());
    assertEquals("SUBMITTED", created.getStatus());

    ReportResponse fetched =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/reports/" + created.getId())
            .request(MediaType.APPLICATION_JSON_TYPE)
            .get(ReportResponse.class);

    assertEquals(created.getId(), fetched.getId());
    assertEquals("Improper gift disclosure", fetched.getTitle());
  }

  @Test
  void invalidCreateRequestReturnsConsistentErrorEnvelope() {
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
}
