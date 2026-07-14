package com.sentinel.enforcement.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sentinel.enforcement.api.generated.model.AssignCaseRequest;
import com.sentinel.enforcement.api.generated.model.CaseResponse;
import com.sentinel.enforcement.api.generated.model.CaseStatusValue;
import com.sentinel.enforcement.api.generated.model.CreateCaseRequest;
import com.sentinel.enforcement.api.generated.model.ReportResponse;
import com.sentinel.enforcement.api.generated.model.WorkflowTaskListResponse;
import com.sentinel.enforcement.api.generated.model.WorkflowTaskResponse;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkflowTaskApiIT extends AbstractApiIT {

  @Test
  void workflowTasksCanDriveCaseFromCreationToDecision() {
    ReportResponse report =
        createTriagedReport(accessToken("intake-jkt"), accessToken("triage-jkt"), "JKT");
    CaseResponse createdCase =
        createCase(
            accessToken("triage-jkt"),
            report.getId(),
            "Workflow driven case",
            "Use task APIs from triage through decision.");

    WorkflowTaskResponse triageTask =
        singleTask(
            accessToken("triage-jkt"),
            Map.of("caseId", createdCase.getId().toString(), "limit", "10"));
    assertEquals("triageTask", triageTask.getTaskDefinitionKey());

    WorkflowTaskResponse claimedTriage =
        claimTask(accessToken("triage-jkt"), triageTask.getTaskId());
    assertEquals("triage-jkt", claimedTriage.getAssigneeUserId());
    completeTask(accessToken("triage-jkt"), triageTask.getTaskId());

    CaseResponse afterTriage = getCase(accessToken("triage-jkt"), createdCase.getId());
    assertEquals(CaseStatusValue.UNDER_INVESTIGATION, afterTriage.getStatus());

    CaseResponse assigned =
        assignCase(
            accessToken("triage-jkt"),
            createdCase.getId(),
            "JKT-WF-1",
            "investigator-jkt",
            afterTriage.getVersion(),
            "Assign investigator after triage.");

    WorkflowTaskResponse investigationTask =
        singleTask(
            accessToken("investigator-jkt"),
            Map.of("caseId", createdCase.getId().toString(), "limit", "10"));
    assertEquals("investigationTask", investigationTask.getTaskDefinitionKey());
    claimTask(accessToken("investigator-jkt"), investigationTask.getTaskId());
    completeTask(accessToken("investigator-jkt"), investigationTask.getTaskId());

    WorkflowTaskResponse reviewTask =
        singleTask(
            accessToken("reviewer-jkt"),
            Map.of("caseId", createdCase.getId().toString(), "limit", "10"));
    assertEquals("reviewTask", reviewTask.getTaskDefinitionKey());
    claimTask(accessToken("reviewer-jkt"), reviewTask.getTaskId());
    completeTask(accessToken("reviewer-jkt"), reviewTask.getTaskId());

    WorkflowTaskResponse decisionTask =
        singleTask(
            accessToken("decision-jkt"),
            Map.of("caseId", createdCase.getId().toString(), "limit", "10"));
    assertEquals("decisionTask", decisionTask.getTaskDefinitionKey());
    claimTask(accessToken("decision-jkt"), decisionTask.getTaskId());
    completeTask(accessToken("decision-jkt"), decisionTask.getTaskId());

    CaseResponse decidedCase = getCase(accessToken("decision-jkt"), createdCase.getId());
    assertEquals(CaseStatusValue.DECIDED, decidedCase.getStatus());
    assertEquals("COMPLETED", workflowStatus(createdCase.getId()));
    assertEquals(6L, decidedCase.getVersion());
    assertEquals(assigned.getId(), decidedCase.getId());
  }

  @Test
  void duplicateTaskCompletionIsSafeAndDoesNotAdvanceCaseTwice() {
    ReportResponse report =
        createTriagedReport(accessToken("intake-jkt"), accessToken("triage-jkt"), "JKT");
    CaseResponse createdCase =
        createCase(
            accessToken("triage-jkt"),
            report.getId(),
            "Duplicate completion case",
            "Ensure duplicate task completion is idempotent.");

    WorkflowTaskResponse triageTask =
        singleTask(
            accessToken("triage-jkt"),
            Map.of("caseId", createdCase.getId().toString(), "limit", "10"));
    claimTask(accessToken("triage-jkt"), triageTask.getTaskId());

    Response firstComplete = completeTask(accessToken("triage-jkt"), triageTask.getTaskId());
    Response secondComplete = completeTask(accessToken("triage-jkt"), triageTask.getTaskId());

    assertEquals(204, firstComplete.getStatus());
    assertEquals(204, secondComplete.getStatus());

    CaseResponse afterCompletion = getCase(accessToken("triage-jkt"), createdCase.getId());
    assertEquals(CaseStatusValue.UNDER_INVESTIGATION, afterCompletion.getStatus());
    assertEquals(2L, afterCompletion.getVersion());
  }

  @Test
  void taskListSupportsQuickSearchFieldSearchSortAndCursor() {
    String token = "wf-list-" + UUID.randomUUID().toString().substring(0, 8);
    ReportResponse reportOne =
        createTriagedReport(accessToken("intake-jkt"), accessToken("triage-jkt"), "JKT");
    ReportResponse reportTwo =
        createTriagedReport(accessToken("intake-jkt"), accessToken("triage-jkt"), "JKT");
    ReportResponse reportThree =
        createTriagedReport(accessToken("intake-jkt"), accessToken("triage-jkt"), "JKT");

    CaseResponse alpha =
        createCase(
            accessToken("triage-jkt"), reportOne.getId(), token + " Alpha", "Workflow list.");
    CaseResponse bravo =
        createCase(
            accessToken("triage-jkt"), reportTwo.getId(), token + " Bravo", "Workflow list.");
    CaseResponse zulu =
        createCase(
            accessToken("triage-jkt"), reportThree.getId(), "Zulu unrelated", token + " summary");

    WorkflowTaskListResponse quickSearchResponse =
        listTasks(
            accessToken("triage-jkt"),
            Map.of("q", token, "sortBy", "CASE_NUMBER", "sortDirection", "ASC", "limit", "10"));

    assertEquals(3, quickSearchResponse.getItems().size());
    assertEquals(alpha.getId(), quickSearchResponse.getItems().get(0).getCaseId());
    assertEquals(bravo.getId(), quickSearchResponse.getItems().get(1).getCaseId());
    assertEquals(zulu.getId(), quickSearchResponse.getItems().get(2).getCaseId());

    WorkflowTaskListResponse fieldSearchResponse =
        listTasks(
            accessToken("triage-jkt"),
            Map.of(
                "searchField", "CASE_TITLE",
                "searchValue", token,
                "sortBy", "CASE_NUMBER",
                "sortDirection", "ASC",
                "limit", "10"));

    assertEquals(2, fieldSearchResponse.getItems().size());
    assertEquals(alpha.getId(), fieldSearchResponse.getItems().get(0).getCaseId());
    assertEquals(bravo.getId(), fieldSearchResponse.getItems().get(1).getCaseId());

    WorkflowTaskListResponse firstPage =
        listTasks(
            accessToken("triage-jkt"),
            Map.of("q", token, "sortBy", "CASE_NUMBER", "sortDirection", "ASC", "limit", "2"));

    assertEquals(2, firstPage.getItems().size());
    assertNotNull(firstPage.getNextCursor());

    WorkflowTaskListResponse secondPage =
        listTasks(
            accessToken("triage-jkt"),
            Map.of(
                "q", token,
                "sortBy", "CASE_NUMBER",
                "sortDirection", "ASC",
                "limit", "2",
                "cursor", firstPage.getNextCursor()));

    assertEquals(1, secondPage.getItems().size());
    assertEquals(zulu.getId(), secondPage.getItems().get(0).getCaseId());
  }

  @Test
  void investigationTimerEscalationWritesAuditEvent() {
    ReportResponse report =
        createTriagedReport(accessToken("intake-jkt"), accessToken("triage-jkt"), "JKT");
    CaseResponse createdCase =
        createCase(
            accessToken("triage-jkt"),
            report.getId(),
            "Timer escalation case",
            "Wait for workflow timer escalation.");

    WorkflowTaskResponse triageTask =
        singleTask(
            accessToken("triage-jkt"),
            Map.of("caseId", createdCase.getId().toString(), "limit", "10"));
    claimTask(accessToken("triage-jkt"), triageTask.getTaskId());
    completeTask(accessToken("triage-jkt"), triageTask.getTaskId());

    waitForEscalationAudit(createdCase.getId());
    assertEquals(1L, countAuditEventsByType(createdCase.getId(), "WorkflowInvestigationEscalated"));
  }

  private static void waitForEscalationAudit(UUID caseId) {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
    while (Instant.now().isBefore(deadline)) {
      if (countAuditEventsByType(caseId, "WorkflowInvestigationEscalated") > 0) {
        return;
      }
      try {
        Thread.sleep(250);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while waiting for workflow timer escalation.", exception);
      }
    }
    throw new AssertionError("Expected workflow escalation audit event was not written in time.");
  }

  private static WorkflowTaskResponse singleTask(
      String accessToken, Map<String, String> queryParams) {
    WorkflowTaskListResponse response = listTasks(accessToken, queryParams);
    assertEquals(1, response.getItems().size());
    return response.getItems().get(0);
  }

  private static WorkflowTaskListResponse listTasks(
      String accessToken, Map<String, String> queryParams) {
    return listTasksRaw(accessToken, queryParams).readEntity(WorkflowTaskListResponse.class);
  }

  private static Response listTasksRaw(String accessToken, Map<String, String> queryParams) {
    WebTarget target = client.target(applicationRuntime.baseUri()).path("/api/v1/tasks");
    for (Map.Entry<String, String> entry : queryParams.entrySet()) {
      target = target.queryParam(entry.getKey(), entry.getValue());
    }
    return target
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .get();
  }

  private static WorkflowTaskResponse claimTask(String accessToken, String taskId) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/tasks/" + taskId + "/claim")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(Entity.entity("", MediaType.APPLICATION_JSON_TYPE), WorkflowTaskResponse.class);
  }

  private static Response completeTask(String accessToken, String taskId) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/tasks/" + taskId + "/complete")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(Entity.entity("", MediaType.APPLICATION_JSON_TYPE));
  }

  private static CaseResponse getCase(String accessToken, UUID caseId) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/cases/" + caseId)
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .get(CaseResponse.class);
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
}
