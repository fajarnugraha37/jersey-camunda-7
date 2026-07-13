package com.sentinel.enforcement.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sentinel.enforcement.api.error.ErrorResponse;
import com.sentinel.enforcement.api.health.HealthResponse;
import com.sentinel.enforcement.api.report.CreateReportRequest;
import com.sentinel.enforcement.api.report.ReportResponse;
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
                    new CreateReportRequest(
                        "Improper gift disclosure",
                        "Potential violation involving unreported gifts.",
                        "JKT",
                        "Analyst A"),
                    MediaType.APPLICATION_JSON_TYPE),
                ReportResponse.class);

    assertNotNull(created.id());
    assertEquals("SUBMITTED", created.status());

    ReportResponse fetched =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/reports/" + created.id())
            .request(MediaType.APPLICATION_JSON_TYPE)
            .get(ReportResponse.class);

    assertEquals(created.id(), fetched.id());
    assertEquals("Improper gift disclosure", fetched.title());
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
                    new CreateReportRequest(
                        "", "Potential violation involving unreported gifts.", "JKT", "Analyst A"),
                    MediaType.APPLICATION_JSON_TYPE));

    ErrorResponse error = response.readEntity(ErrorResponse.class);

    assertEquals(400, response.getStatus());
    assertEquals("VALIDATION_ERROR", error.code());
    assertNotNull(error.correlationId());
  }

  @Test
  void healthEndpointReportsApplicationAndDatabaseUp() {
    HealthResponse response =
        client
            .target(applicationRuntime.baseUri())
            .path("/health")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .get(HealthResponse.class);

    assertEquals("UP", response.status());
    assertEquals("UP", response.database());
    assertNotNull(response.timestamp());
  }
}
