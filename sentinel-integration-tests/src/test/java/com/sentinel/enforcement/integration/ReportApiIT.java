package com.sentinel.enforcement.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinel.enforcement.api.generated.model.CreateReportRequest;
import com.sentinel.enforcement.api.generated.model.ErrorResponse;
import com.sentinel.enforcement.api.generated.model.HealthResponse;
import com.sentinel.enforcement.api.generated.model.ReportResponse;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

class ReportApiIT extends AbstractApiIT {

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
  void triageReportUpdatesStatusAndVersion() {
    String intakeToken = accessToken("intake-jkt");
    String triageToken = accessToken("triage-jkt");
    ReportResponse created = createReport(intakeToken, "JKT");

    ReportResponse triaged =
        triageReport(
            triageToken, created.getId(), created.getVersion(), "Ready for case creation.");

    assertEquals("TRIAGED", triaged.getStatus());
    assertEquals(created.getVersion() + 1, triaged.getVersion());
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
    assertEquals("UP", response.getDependencies().get("database"));
    assertEquals("UP", response.getDependencies().get("kafka"));
    assertEquals("UP", response.getDependencies().get("redis"));
    assertEquals("UP", response.getDependencies().get("mailpit"));
    assertEquals("UP", response.getDependencies().get("workflow"));
    assertTrue(response.getDependencies().size() >= 5);
    assertNotNull(response.getTimestamp());
  }
}
