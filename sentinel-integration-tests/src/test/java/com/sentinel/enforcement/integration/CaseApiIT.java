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
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
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

  @Test
  void listCasesSupportsQuickSearchFieldSearchDynamicSortAndCursor() {
    String token = "list-pattern-" + UUID.randomUUID().toString().substring(0, 8);
    ReportResponse reportAlpha = createReport(accessToken("intake-jkt"), "JKT");
    ReportResponse reportZulu = createReport(accessToken("intake-jkt"), "JKT");
    ReportResponse reportBravo = createReport(accessToken("intake-jkt"), "JKT");
    CaseResponse alpha =
        createCase(
            accessToken("triage-jkt"),
            reportAlpha.getId(),
            token + " Alpha",
            "Title field carries the search token.");
    CaseResponse zulu =
        createCase(
            accessToken("triage-jkt"),
            reportZulu.getId(),
            "Zulu unrelated",
            token + " summary match only.");
    CaseResponse bravo =
        createCase(
            accessToken("triage-jkt"),
            reportBravo.getId(),
            token + " Bravo",
            "Another title match for sort verification.");

    CaseListResponse quickSearchResponse =
        listCases(
            accessToken("triage-jkt"),
            Map.of("q", token, "sortBy", "TITLE", "sortDirection", "ASC", "limit", "10"));

    assertEquals(3, quickSearchResponse.getItems().size());
    assertEquals(alpha.getId(), quickSearchResponse.getItems().get(0).getId());
    assertEquals(bravo.getId(), quickSearchResponse.getItems().get(1).getId());
    assertEquals(zulu.getId(), quickSearchResponse.getItems().get(2).getId());

    CaseListResponse fieldSearchResponse =
        listCases(
            accessToken("triage-jkt"),
            Map.of(
                "searchField", "TITLE",
                "searchValue", token,
                "sortBy", "TITLE",
                "sortDirection", "ASC",
                "limit", "10"));

    assertEquals(2, fieldSearchResponse.getItems().size());
    assertEquals(alpha.getId(), fieldSearchResponse.getItems().get(0).getId());
    assertEquals(bravo.getId(), fieldSearchResponse.getItems().get(1).getId());

    CaseListResponse firstPage =
        listCases(
            accessToken("triage-jkt"),
            Map.of("q", token, "sortBy", "TITLE", "sortDirection", "ASC", "limit", "2"));

    assertEquals(2, firstPage.getItems().size());
    assertNotNull(firstPage.getNextCursor());

    CaseListResponse secondPage =
        listCases(
            accessToken("triage-jkt"),
            Map.of(
                "q", token,
                "sortBy", "TITLE",
                "sortDirection", "ASC",
                "limit", "2",
                "cursor", firstPage.getNextCursor()));

    assertEquals(1, secondPage.getItems().size());
    assertEquals(zulu.getId(), secondPage.getItems().get(0).getId());
  }

  @Test
  void auditEventsSupportQuickSearchFieldSearchDynamicSortAndCursor() {
    ReportResponse report = createReport(accessToken("intake-jkt"), "JKT");
    CaseResponse createdCase =
        createCase(
            accessToken("triage-jkt"),
            report.getId(),
            "Audit list verification",
            "Exercise dynamic audit listing.");
    CaseResponse assigned =
        assignCase(
            accessToken("triage-jkt"),
            createdCase.getId(),
            "JKT-UNIT-1",
            "investigator-jkt",
            createdCase.getVersion(),
            "Assign investigator for audit trail.");
    CaseResponse underTriage =
        transitionCase(
            accessToken("triage-jkt"),
            assigned.getId(),
            CaseStatusValue.UNDER_TRIAGE,
            assigned.getVersion(),
            "Move case into triage.");
    transitionCase(
        accessToken("triage-jkt"),
        underTriage.getId(),
        CaseStatusValue.UNDER_INVESTIGATION,
        underTriage.getVersion(),
        "Move case into investigation.");

    CaseAuditEventListResponse quickSearchResponse =
        listAuditEvents(
            accessToken("auditor-jkt"),
            createdCase.getId(),
            Map.of("q", "assigned", "sortBy", "EVENT_TYPE", "sortDirection", "ASC", "limit", "10"));

    assertEquals(1, quickSearchResponse.getItems().size());
    assertEquals("CaseAssigned", quickSearchResponse.getItems().get(0).getEventType());

    CaseAuditEventListResponse fieldSearchResponse =
        listAuditEvents(
            accessToken("auditor-jkt"),
            createdCase.getId(),
            Map.of(
                "searchField", "ACTION",
                "searchValue", "transitioned",
                "sortBy", "TIMESTAMP",
                "sortDirection", "DESC",
                "limit", "10"));

    assertEquals(2, fieldSearchResponse.getItems().size());
    assertTrue(
        fieldSearchResponse.getItems().stream()
            .allMatch(item -> "CASE_TRANSITIONED".equals(item.getAction())));

    CaseAuditEventListResponse firstPage =
        listAuditEvents(
            accessToken("auditor-jkt"),
            createdCase.getId(),
            Map.of("sortBy", "EVENT_TYPE", "sortDirection", "ASC", "limit", "2"));

    assertEquals(2, firstPage.getItems().size());
    assertNotNull(firstPage.getNextCursor());

    CaseAuditEventListResponse secondPage =
        listAuditEvents(
            accessToken("auditor-jkt"),
            createdCase.getId(),
            Map.of(
                "sortBy", "EVENT_TYPE",
                "sortDirection", "ASC",
                "limit", "2",
                "cursor", firstPage.getNextCursor()));

    assertTrue(secondPage.getItems().size() >= 1);
    assertTrue(
        secondPage.getItems().stream()
            .noneMatch(item -> item.getEventId().equals(firstPage.getItems().get(0).getEventId())));
  }

  @Test
  void mismatchedCursorScopeReturnsBadRequest() {
    String token = "cursor-scope-" + UUID.randomUUID().toString().substring(0, 8);
    ReportResponse report = createReport(accessToken("intake-jkt"), "JKT");
    createCase(
        accessToken("triage-jkt"), report.getId(), token + " Alpha", "Cursor mismatch validation.");
    createCase(
        accessToken("triage-jkt"), report.getId(), token + " Bravo", "Cursor mismatch validation.");

    CaseListResponse firstPage =
        listCases(
            accessToken("triage-jkt"),
            Map.of("q", token, "sortBy", "TITLE", "sortDirection", "ASC", "limit", "1"));

    Response invalidCursorResponse =
        listCasesRaw(
            accessToken("triage-jkt"),
            Map.of(
                "q", token,
                "sortBy", "CREATED_AT",
                "sortDirection", "DESC",
                "limit", "1",
                "cursor", firstPage.getNextCursor()));

    ErrorResponse error = invalidCursorResponse.readEntity(ErrorResponse.class);
    assertEquals(400, invalidCursorResponse.getStatus());
    assertEquals("MALFORMED_REQUEST", error.getCode());
  }

  @Test
  void incompleteFieldSearchReturnsBadRequestInsteadOfServerError() {
    Response caseResponse =
        listCasesRaw(accessToken("triage-jkt"), Map.of("searchField", "TITLE", "limit", "10"));

    ErrorResponse caseError = caseResponse.readEntity(ErrorResponse.class);
    assertEquals(400, caseResponse.getStatus());
    assertEquals("MALFORMED_REQUEST", caseError.getCode());

    ReportResponse report = createReport(accessToken("intake-jkt"), "JKT");
    CaseResponse createdCase =
        createCase(
            accessToken("triage-jkt"),
            report.getId(),
            "Audit error handling",
            "Negative query validation.");

    Response auditResponse =
        listAuditEventsRaw(
            accessToken("auditor-jkt"),
            createdCase.getId(),
            Map.of("searchField", "ACTION", "limit", "10"));

    ErrorResponse auditError = auditResponse.readEntity(ErrorResponse.class);
    assertEquals(400, auditResponse.getStatus());
    assertEquals("MALFORMED_REQUEST", auditError.getCode());
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

  private static CaseListResponse listCases(String accessToken, Map<String, String> queryParams) {
    return listCasesRaw(accessToken, queryParams).readEntity(CaseListResponse.class);
  }

  private static Response listCasesRaw(String accessToken, Map<String, String> queryParams) {
    WebTarget target = client.target(applicationRuntime.baseUri()).path("/api/v1/cases");
    for (Map.Entry<String, String> entry : queryParams.entrySet()) {
      target = target.queryParam(entry.getKey(), entry.getValue());
    }
    return target
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .get();
  }

  private static CaseAuditEventListResponse listAuditEvents(
      String accessToken, UUID caseId, Map<String, String> queryParams) {
    return listAuditEventsRaw(accessToken, caseId, queryParams)
        .readEntity(CaseAuditEventListResponse.class);
  }

  private static Response listAuditEventsRaw(
      String accessToken, UUID caseId, Map<String, String> queryParams) {
    WebTarget target =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/cases/" + caseId + "/audit-events");
    for (Map.Entry<String, String> entry : queryParams.entrySet()) {
      target = target.queryParam(entry.getKey(), entry.getValue());
    }
    return target
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .get();
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
