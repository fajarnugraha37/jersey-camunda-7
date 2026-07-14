package com.sentinel.enforcement.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinel.enforcement.api.generated.model.AssignCaseRequest;
import com.sentinel.enforcement.api.generated.model.CaseResponse;
import com.sentinel.enforcement.api.generated.model.CreateCaseRequest;
import com.sentinel.enforcement.api.generated.model.ReconcileWorkflowRequest;
import com.sentinel.enforcement.api.generated.model.ReportResponse;
import com.sentinel.enforcement.api.generated.model.WorkflowCorrelationStatusValue;
import com.sentinel.enforcement.api.generated.model.WorkflowReconciliationActionResponse;
import com.sentinel.enforcement.api.generated.model.WorkflowReconciliationActionResultValue;
import com.sentinel.enforcement.api.generated.model.WorkflowReconciliationIssueListResponse;
import com.sentinel.enforcement.api.generated.model.WorkflowReconciliationIssueResponse;
import com.sentinel.enforcement.api.generated.model.WorkflowReconciliationIssueTypeValue;
import com.sentinel.enforcement.api.generated.model.WorkflowReconciliationOperationValue;
import com.sentinel.enforcement.api.generated.model.WorkflowTaskListResponse;
import com.sentinel.enforcement.api.generated.model.WorkflowTaskResponse;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkflowReconciliationApiIT extends AbstractApiIT {

  @Test
  void supervisorCanListAndAutoRepairMissingActiveWorkflowCorrelation() {
    String token = "wf-reconcile-active-" + UUID.randomUUID().toString().substring(0, 8);
    ReportResponse report = createReport(accessToken("intake-jkt"), "JKT");
    CaseResponse createdCase =
        createCase(
            accessToken("triage-jkt"),
            report.getId(),
            token + " active mismatch",
            "Active mismatch.");

    assertEquals(
        1, executeUpdate("DELETE FROM workflow_instance WHERE case_id = ?", createdCase.getId()));

    WorkflowReconciliationIssueListResponse issues =
        listIssues(accessToken("supervisor-jkt"), Map.of("q", token, "limit", "10"));

    assertEquals(1, issues.getItems().size());
    WorkflowReconciliationIssueResponse issue = issues.getItems().get(0);
    assertEquals(createdCase.getId(), issue.getCaseId());
    assertEquals(
        WorkflowReconciliationIssueTypeValue.ACTIVE_RUNTIME_MISSING_CORRELATION,
        issue.getIssueType());
    assertNull(issue.getWorkflowCorrelationStatus());
    assertNotNull(issue.getRuntimeProcessInstanceId());
    assertTrue(
        issue.getAvailableActions().contains(WorkflowReconciliationOperationValue.AUTO_REPAIR));

    WorkflowReconciliationActionResponse actionResponse =
        reconcileCase(
            accessToken("supervisor-jkt"),
            createdCase.getId(),
            WorkflowReconciliationOperationValue.AUTO_REPAIR,
            "Restore missing workflow correlation row.");

    assertEquals(createdCase.getId(), actionResponse.getCaseId());
    assertEquals(WorkflowReconciliationOperationValue.AUTO_REPAIR, actionResponse.getAction());
    assertEquals(WorkflowReconciliationActionResultValue.REPAIRED, actionResponse.getResult());
    assertEquals(
        WorkflowCorrelationStatusValue.ACTIVE, actionResponse.getWorkflowCorrelationStatus());
    assertEquals(issue.getRuntimeProcessInstanceId(), actionResponse.getProcessInstanceId());
    assertEquals("ACTIVE", workflowStatus(createdCase.getId()));
    assertEquals(
        1L, countAuditEventsByType(createdCase.getId(), "WorkflowReconciliationPerformed"));
    assertTrue(
        listIssues(accessToken("supervisor-jkt"), Map.of("q", token, "limit", "10"))
            .getItems()
            .isEmpty());
  }

  @Test
  void supervisorCanAutoRepairTerminalCaseFromWorkflowHistory() {
    String token = "wf-reconcile-history-" + UUID.randomUUID().toString().substring(0, 8);
    ReportResponse report = createReport(accessToken("intake-jkt"), "JKT");
    CaseResponse createdCase =
        createCase(
            accessToken("triage-jkt"),
            report.getId(),
            token + " history mismatch",
            "History mismatch.");

    advanceCaseToDecision(createdCase.getId());
    assertEquals("COMPLETED", workflowStatus(createdCase.getId()));
    assertEquals(
        1, executeUpdate("DELETE FROM workflow_instance WHERE case_id = ?", createdCase.getId()));

    WorkflowReconciliationIssueListResponse issues =
        listIssues(accessToken("supervisor-jkt"), Map.of("q", token, "limit", "10"));

    assertEquals(1, issues.getItems().size());
    WorkflowReconciliationIssueResponse issue = issues.getItems().get(0);
    assertEquals(
        WorkflowReconciliationIssueTypeValue.TERMINAL_CASE_MISSING_CORRELATION,
        issue.getIssueType());
    assertTrue(
        issue.getAvailableActions().contains(WorkflowReconciliationOperationValue.AUTO_REPAIR));
    assertNull(issue.getRuntimeProcessInstanceId());

    WorkflowReconciliationActionResponse actionResponse =
        reconcileCase(
            accessToken("supervisor-jkt"),
            createdCase.getId(),
            WorkflowReconciliationOperationValue.AUTO_REPAIR,
            "Restore terminal workflow correlation from history.");

    assertEquals(WorkflowReconciliationActionResultValue.REPAIRED, actionResponse.getResult());
    assertEquals(
        WorkflowCorrelationStatusValue.COMPLETED, actionResponse.getWorkflowCorrelationStatus());
    assertNotNull(actionResponse.getProcessInstanceId());
    assertEquals("COMPLETED", workflowStatus(createdCase.getId()));
    assertEquals(
        1L, countAuditEventsByType(createdCase.getId(), "WorkflowReconciliationPerformed"));
    assertTrue(
        listIssues(accessToken("supervisor-jkt"), Map.of("q", token, "limit", "10"))
            .getItems()
            .isEmpty());
  }

  @Test
  void supervisorCanTerminateRuntimeWhenCaseWasForcedIntoTerminalState() {
    String token = "wf-reconcile-terminate-" + UUID.randomUUID().toString().substring(0, 8);
    ReportResponse report = createReport(accessToken("intake-jkt"), "JKT");
    CaseResponse createdCase =
        createCase(
            accessToken("triage-jkt"),
            report.getId(),
            token + " terminate mismatch",
            "Terminate runtime mismatch.");

    WorkflowTaskListResponse beforeTermination =
        listTasks(
            accessToken("triage-jkt"),
            Map.of("caseId", createdCase.getId().toString(), "limit", "10"));
    assertEquals(1, beforeTermination.getItems().size());

    assertEquals(
        1,
        executeUpdate(
            """
            UPDATE case_record
            SET status = 'DECIDED',
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP,
                updated_by = 'integration-test'
            WHERE id = ?
            """,
            createdCase.getId()));

    WorkflowReconciliationIssueListResponse issues =
        listIssues(accessToken("supervisor-jkt"), Map.of("q", token, "limit", "10"));

    assertEquals(1, issues.getItems().size());
    WorkflowReconciliationIssueResponse issue = issues.getItems().get(0);
    assertEquals(
        WorkflowReconciliationIssueTypeValue.TERMINAL_CASE_RUNTIME_ACTIVE, issue.getIssueType());
    assertEquals(WorkflowCorrelationStatusValue.ACTIVE, issue.getWorkflowCorrelationStatus());
    assertTrue(
        issue
            .getAvailableActions()
            .contains(WorkflowReconciliationOperationValue.TERMINATE_RUNTIME));

    WorkflowReconciliationActionResponse actionResponse =
        reconcileCase(
            accessToken("supervisor-jkt"),
            createdCase.getId(),
            WorkflowReconciliationOperationValue.TERMINATE_RUNTIME,
            "Terminate runtime for terminal case mismatch.");

    assertEquals(WorkflowReconciliationActionResultValue.REPAIRED, actionResponse.getResult());
    assertEquals(
        WorkflowCorrelationStatusValue.COMPLETED, actionResponse.getWorkflowCorrelationStatus());
    assertEquals("COMPLETED", workflowStatus(createdCase.getId()));
    assertTrue(
        listTasks(
                accessToken("triage-jkt"),
                Map.of("caseId", createdCase.getId().toString(), "limit", "10"))
            .getItems()
            .isEmpty());
    assertEquals(
        1L, countAuditEventsByType(createdCase.getId(), "WorkflowReconciliationPerformed"));
    assertTrue(
        listIssues(accessToken("supervisor-jkt"), Map.of("q", token, "limit", "10"))
            .getItems()
            .isEmpty());
  }

  @Test
  void workflowReconciliationListSupportsQuickSearchFieldSearchSortAndCursor() {
    String token = "wf-reconcile-list-" + UUID.randomUUID().toString().substring(0, 8);
    CaseResponse alpha = createMismatchCase(token + " Alpha");
    CaseResponse bravo = createMismatchCase(token + " Bravo");
    CaseResponse charlie = createMismatchCase(token + " Charlie");

    WorkflowReconciliationIssueListResponse quickSearchPageOne =
        listIssues(
            accessToken("supervisor-jkt"),
            Map.of("q", token, "sortBy", "CASE_NUMBER", "sortDirection", "ASC", "limit", "2"));

    assertEquals(2, quickSearchPageOne.getItems().size());
    assertNotNull(quickSearchPageOne.getNextCursor());
    assertEquals(alpha.getId(), quickSearchPageOne.getItems().get(0).getCaseId());
    assertEquals(bravo.getId(), quickSearchPageOne.getItems().get(1).getCaseId());

    WorkflowReconciliationIssueListResponse quickSearchPageTwo =
        listIssues(
            accessToken("supervisor-jkt"),
            Map.of(
                "q", token,
                "sortBy", "CASE_NUMBER",
                "sortDirection", "ASC",
                "limit", "2",
                "cursor", quickSearchPageOne.getNextCursor()));

    assertEquals(1, quickSearchPageTwo.getItems().size());
    assertEquals(charlie.getId(), quickSearchPageTwo.getItems().get(0).getCaseId());
    assertNull(quickSearchPageTwo.getNextCursor());

    String caseNumber = quickSearchPageOne.getItems().get(0).getCaseNumber();
    WorkflowReconciliationIssueListResponse fieldSearch =
        listIssues(
            accessToken("supervisor-jkt"),
            Map.of(
                "searchField", "CASE_NUMBER",
                "searchValue", caseNumber,
                "limit", "10"));

    assertEquals(1, fieldSearch.getItems().size());
    assertEquals(alpha.getId(), fieldSearch.getItems().get(0).getCaseId());
    assertEquals(caseNumber, fieldSearch.getItems().get(0).getCaseNumber());
  }

  @Test
  void workflowReconciliationEndpointsRequireSupervisorPermission() {
    Response response = listIssuesRaw(accessToken("triage-jkt"), Map.of("limit", "10"));

    assertEquals(403, response.getStatus());
    response.close();
  }

  private static CaseResponse createMismatchCase(String title) {
    ReportResponse report = createReport(accessToken("intake-jkt"), "JKT");
    CaseResponse createdCase =
        createCase(accessToken("triage-jkt"), report.getId(), title, "List mismatch.");
    assertEquals(
        1, executeUpdate("DELETE FROM workflow_instance WHERE case_id = ?", createdCase.getId()));
    return createdCase;
  }

  private static void advanceCaseToDecision(UUID caseId) {
    WorkflowTaskResponse triageTask =
        singleTask(accessToken("triage-jkt"), Map.of("caseId", caseId.toString(), "limit", "10"));
    claimTask(accessToken("triage-jkt"), triageTask.getTaskId());
    completeTask(accessToken("triage-jkt"), triageTask.getTaskId());

    long assignmentVersion = getCase(accessToken("triage-jkt"), caseId).getVersion();

    assignCase(
        accessToken("triage-jkt"),
        caseId,
        "JKT-WF-1",
        "investigator-jkt",
        assignmentVersion,
        "Assign investigator.");

    WorkflowTaskResponse investigationTask =
        singleTask(
            accessToken("investigator-jkt"), Map.of("caseId", caseId.toString(), "limit", "10"));
    claimTask(accessToken("investigator-jkt"), investigationTask.getTaskId());
    completeTask(accessToken("investigator-jkt"), investigationTask.getTaskId());

    WorkflowTaskResponse reviewTask =
        singleTask(accessToken("reviewer-jkt"), Map.of("caseId", caseId.toString(), "limit", "10"));
    claimTask(accessToken("reviewer-jkt"), reviewTask.getTaskId());
    completeTask(accessToken("reviewer-jkt"), reviewTask.getTaskId());

    WorkflowTaskResponse decisionTask =
        singleTask(accessToken("decision-jkt"), Map.of("caseId", caseId.toString(), "limit", "10"));
    claimTask(accessToken("decision-jkt"), decisionTask.getTaskId());
    completeTask(accessToken("decision-jkt"), decisionTask.getTaskId());
  }

  private static WorkflowReconciliationActionResponse reconcileCase(
      String accessToken, UUID caseId, WorkflowReconciliationOperationValue action, String reason) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/workflow-reconciliation/" + caseId + "/actions")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(
            Entity.entity(
                new ReconcileWorkflowRequest().action(action).reason(reason),
                MediaType.APPLICATION_JSON_TYPE),
            WorkflowReconciliationActionResponse.class);
  }

  private static WorkflowReconciliationIssueListResponse listIssues(
      String accessToken, Map<String, String> queryParams) {
    return listIssuesRaw(accessToken, queryParams)
        .readEntity(WorkflowReconciliationIssueListResponse.class);
  }

  private static Response listIssuesRaw(String accessToken, Map<String, String> queryParams) {
    WebTarget target =
        client.target(applicationRuntime.baseUri()).path("/api/v1/workflow-reconciliation");
    for (Map.Entry<String, String> entry : queryParams.entrySet()) {
      target = target.queryParam(entry.getKey(), entry.getValue());
    }
    return target
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .get();
  }

  private static WorkflowTaskResponse singleTask(
      String accessToken, Map<String, String> queryParams) {
    WorkflowTaskListResponse response = listTasks(accessToken, queryParams);
    assertEquals(1, response.getItems().size());
    return response.getItems().get(0);
  }

  private static WorkflowTaskListResponse listTasks(
      String accessToken, Map<String, String> queryParams) {
    WebTarget target = client.target(applicationRuntime.baseUri()).path("/api/v1/tasks");
    for (Map.Entry<String, String> entry : queryParams.entrySet()) {
      target = target.queryParam(entry.getKey(), entry.getValue());
    }
    return target
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .get(WorkflowTaskListResponse.class);
  }

  private static WorkflowTaskResponse claimTask(String accessToken, String taskId) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/tasks/" + taskId + "/claim")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .post(Entity.entity("", MediaType.APPLICATION_JSON_TYPE), WorkflowTaskResponse.class);
  }

  private static void completeTask(String accessToken, String taskId) {
    Response response =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/tasks/" + taskId + "/complete")
            .request()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .post(Entity.entity("", MediaType.APPLICATION_JSON_TYPE));
    assertEquals(204, response.getStatus());
    response.close();
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

  private static CaseResponse getCase(String accessToken, UUID caseId) {
    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/cases/" + caseId)
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .get(CaseResponse.class);
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
