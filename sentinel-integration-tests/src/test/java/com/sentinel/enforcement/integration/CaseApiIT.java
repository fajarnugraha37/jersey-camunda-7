package com.sentinel.enforcement.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinel.enforcement.api.generated.model.AssignCaseRequest;
import com.sentinel.enforcement.api.generated.model.CaseAuditEventListResponse;
import com.sentinel.enforcement.api.generated.model.CaseListResponse;
import com.sentinel.enforcement.api.generated.model.CaseResponse;
import com.sentinel.enforcement.api.generated.model.CaseStatusValue;
import com.sentinel.enforcement.api.generated.model.CreateCaseRequest;
import com.sentinel.enforcement.api.generated.model.ErrorResponse;
import com.sentinel.enforcement.api.generated.model.ReportResponse;
import com.sentinel.enforcement.api.generated.model.TransitionCaseRequest;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaseApiIT extends AbstractApiIT {

  @Test
  void fullCaseLifecyclePersistsHistoryAndAuditTrail() {
    ReportResponse report = createReport(accessToken("intake-jkt"), "JKT");
    CaseResponse createdCase =
        createCase(
            accessToken("triage-jkt"),
            report.getId(),
            "Gift disclosure case",
            "Triaged into case.");

    assertNotNull(createdCase.getCaseNumber());
    assertTrue(createdCase.getCaseNumber().matches("JKT-ENF-2026-\\d{8}"));

    CaseResponse assigned =
        assignCase(
            accessToken("triage-jkt"),
            createdCase.getId(),
            "JKT-UNIT-1",
            "investigator-jkt",
            createdCase.getVersion(),
            "Assign investigator for intake review.");
    CaseResponse underTriage =
        transitionCase(
            accessToken("triage-jkt"),
            assigned.getId(),
            CaseStatusValue.UNDER_TRIAGE,
            assigned.getVersion(),
            "Triage opened for the case.");
    CaseResponse underInvestigation =
        transitionCase(
            accessToken("triage-jkt"),
            underTriage.getId(),
            CaseStatusValue.UNDER_INVESTIGATION,
            underTriage.getVersion(),
            "Escalating into investigation.");
    CaseResponse pendingReview =
        transitionCase(
            accessToken("investigator-jkt"),
            underInvestigation.getId(),
            CaseStatusValue.PENDING_REVIEW,
            underInvestigation.getVersion(),
            "Investigation complete and ready for review.");
    CaseResponse pendingDecision =
        transitionCase(
            accessToken("reviewer-jkt"),
            pendingReview.getId(),
            CaseStatusValue.PENDING_DECISION,
            pendingReview.getVersion(),
            "Review accepted and escalated to decision.");
    CaseResponse decided =
        transitionCase(
            accessToken("decision-jkt"),
            pendingDecision.getId(),
            CaseStatusValue.DECIDED,
            pendingDecision.getVersion(),
            "Decision approved.");
    CaseResponse enforcementInProgress =
        transitionCase(
            accessToken("decision-jkt"),
            decided.getId(),
            CaseStatusValue.ENFORCEMENT_IN_PROGRESS,
            decided.getVersion(),
            "Entering enforcement monitoring.");
    CaseResponse closed =
        transitionCase(
            accessToken("supervisor-jkt"),
            enforcementInProgress.getId(),
            CaseStatusValue.CLOSED,
            enforcementInProgress.getVersion(),
            "All obligations closed.");

    assertEquals(CaseStatusValue.CLOSED, closed.getStatus());
    assertEquals(8L, closed.getVersion());

    CaseAuditEventListResponse auditEvents =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/cases/" + closed.getId() + "/audit-events")
            .queryParam("limit", 20)
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("auditor-jkt"))
            .get(CaseAuditEventListResponse.class);

    assertEquals(9, auditEvents.getItems().size());
    assertEquals(8L, countByCaseId("case_status_history", closed.getId()));
    assertEquals(9L, countByCaseId("audit_event", closed.getId()));
    assertEquals(1L, countByCaseId("case_assignment", closed.getId()));
  }

  @Test
  void investigatorVisibilityIsRestrictedToDirectAssignments() {
    ReportResponse reportOne = createReport(accessToken("intake-jkt"), "JKT");
    ReportResponse reportTwo = createReport(accessToken("intake-jkt"), "JKT");
    CaseResponse caseOne =
        createCase(
            accessToken("triage-jkt"), reportOne.getId(), "Case one", "Assigned to investigator.");
    CaseResponse caseTwo =
        createCase(accessToken("triage-jkt"), reportTwo.getId(), "Case two", "Assigned elsewhere.");

    assignCase(
        accessToken("triage-jkt"),
        caseOne.getId(),
        "JKT-UNIT-1",
        "investigator-jkt",
        caseOne.getVersion(),
        "Direct investigator assignment.");
    assignCase(
        accessToken("triage-jkt"),
        caseTwo.getId(),
        "JKT-UNIT-1",
        "other-investigator",
        caseTwo.getVersion(),
        "Assigned to another investigator.");

    CaseListResponse listResponse =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/cases")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("investigator-jkt"))
            .get(CaseListResponse.class);

    assertEquals(
        0L,
        listResponse.getItems().stream()
            .filter(item -> item.getId().equals(caseTwo.getId()))
            .count());

    CaseResponse fetchedAssignedCase =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/cases/" + caseOne.getId())
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("investigator-jkt"))
            .get(CaseResponse.class);

    assertEquals(caseOne.getId(), fetchedAssignedCase.getId());

    Response forbiddenResponse =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/cases/" + caseTwo.getId())
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("investigator-jkt"))
            .get();

    ErrorResponse error = forbiddenResponse.readEntity(ErrorResponse.class);
    assertEquals(403, forbiddenResponse.getStatus());
    assertEquals("FORBIDDEN", error.getCode());
  }

  @Test
  void invalidTransitionAndStaleVersionReturnConflictEnvelope() {
    ReportResponse report = createReport(accessToken("intake-jkt"), "JKT");
    CaseResponse createdCase =
        createCase(accessToken("triage-jkt"), report.getId(), "Decision case", "Conflict checks.");

    Response invalidTransitionResponse =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/cases/" + createdCase.getId() + "/transitions")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("decision-jkt"))
            .post(
                Entity.entity(
                    new TransitionCaseRequest()
                        .targetStatus(CaseStatusValue.DECIDED)
                        .expectedVersion(createdCase.getVersion())
                        .reason("Attempting to skip required states."),
                    MediaType.APPLICATION_JSON_TYPE));

    ErrorResponse invalidTransition = invalidTransitionResponse.readEntity(ErrorResponse.class);
    assertEquals(409, invalidTransitionResponse.getStatus());
    assertEquals("CASE_TRANSITION_NOT_ALLOWED", invalidTransition.getCode());

    CaseResponse underTriage =
        transitionCase(
            accessToken("triage-jkt"),
            createdCase.getId(),
            CaseStatusValue.UNDER_TRIAGE,
            createdCase.getVersion(),
            "Proper triage start.");

    Response staleVersionResponse =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/cases/" + underTriage.getId() + "/transitions")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("triage-jkt"))
            .post(
                Entity.entity(
                    new TransitionCaseRequest()
                        .targetStatus(CaseStatusValue.UNDER_INVESTIGATION)
                        .expectedVersion(createdCase.getVersion())
                        .reason("Retry with stale version."),
                    MediaType.APPLICATION_JSON_TYPE));

    ErrorResponse staleVersion = staleVersionResponse.readEntity(ErrorResponse.class);
    assertEquals(409, staleVersionResponse.getStatus());
    assertEquals("CONCURRENT_MODIFICATION", staleVersion.getCode());
  }

  private static CaseResponse createCase(
      String accessToken, UUID reportId, String title, String summary) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/cases")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(
            Entity.entity(
                new CreateCaseRequest().reportId(reportId).title(title).summary(summary),
                MediaType.APPLICATION_JSON_TYPE),
            CaseResponse.class);
  }

  private static CaseResponse assignCase(
      String accessToken,
      UUID caseId,
      String assignedUnitId,
      String assigneeUserId,
      long expectedVersion,
      String reason) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/cases/" + caseId + "/assignments")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(
            Entity.entity(
                new AssignCaseRequest()
                    .assignedUnitId(assignedUnitId)
                    .assigneeUserId(assigneeUserId)
                    .expectedVersion(expectedVersion)
                    .reason(reason),
                MediaType.APPLICATION_JSON_TYPE),
            CaseResponse.class);
  }

  private static CaseResponse transitionCase(
      String accessToken,
      UUID caseId,
      CaseStatusValue targetStatus,
      long expectedVersion,
      String reason) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/cases/" + caseId + "/transitions")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(
            Entity.entity(
                new TransitionCaseRequest()
                    .targetStatus(targetStatus)
                    .expectedVersion(expectedVersion)
                    .reason(reason),
                MediaType.APPLICATION_JSON_TYPE),
            CaseResponse.class);
  }
}
