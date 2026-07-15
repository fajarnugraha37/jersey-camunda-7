package com.sentinel.enforcement.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinel.enforcement.api.generated.model.AppealDecisionOutcomeValue;
import com.sentinel.enforcement.api.generated.model.AssignCaseRequest;
import com.sentinel.enforcement.api.generated.model.CaseClassificationValue;
import com.sentinel.enforcement.api.generated.model.CaseResponse;
import com.sentinel.enforcement.api.generated.model.CaseStatusValue;
import com.sentinel.enforcement.api.generated.model.CreateCaseRequest;
import com.sentinel.enforcement.api.generated.model.ReportResponse;
import com.sentinel.enforcement.api.generated.model.TransitionCaseRequest;
import com.sentinel.enforcement.api.generated.model.WorkflowTaskListResponse;
import com.sentinel.enforcement.api.generated.model.WorkflowTaskResponse;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class MessagingReliabilityIT extends AbstractApiIT {

  @Test
  void caseCreationCommitsWhileKafkaIsUnavailableAndPublishesAfterBrokerRecovery() {
    if (KAFKA.isRunning()) {
      KAFKA.stop();
    }

    ReportResponse report =
        createTriagedReport(accessToken("intake-jkt"), accessToken("triage-jkt"), "JKT");
    CaseResponse createdCase =
        createCase(accessToken("triage-jkt"), report.getId(), "Kafka outage case", "Outbox test.");

    assertNotNull(createdCase.getId());
    assertEquals(
        1L, queryForLong("SELECT COUNT(*) FROM case_record WHERE id = ?", createdCase.getId()));
    assertEquals(
        1L,
        queryForLong(
            "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND status = 'PENDING'",
            createdCase.getId()));

    KAFKA.start();

    awaitCondition(
        () ->
            queryForLong(
                    "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND status = 'PENDING'",
                    createdCase.getId())
                == 0L,
        Duration.ofSeconds(45));
    awaitCondition(
        () ->
            queryForLong(
                    "SELECT COUNT(*) FROM notification WHERE case_id = ? AND notification_type = 'CaseCreated'",
                    createdCase.getId())
                == 1L,
        Duration.ofSeconds(45));
  }

  @Test
  void duplicateEventDoesNotCreateDuplicateNotificationSideEffects() {
    if (!KAFKA.isRunning()) {
      KAFKA.start();
    }

    ReportResponse report =
        createTriagedReport(accessToken("intake-jkt"), accessToken("triage-jkt"), "JKT");
    CaseResponse createdCase =
        createCase(
            accessToken("triage-jkt"), report.getId(), "Duplicate event case", "Inbox test.");

    String eventId =
        queryForString(
            "SELECT event_id::text FROM outbox_event WHERE aggregate_id = ? AND event_type = 'CaseCreated'",
            createdCase.getId());
    assertNotNull(eventId);

    awaitCondition(
        () ->
            queryForLong(
                    "SELECT COUNT(*) FROM notification WHERE event_id = ?",
                    UUID.fromString(eventId))
                == 1L,
        Duration.ofSeconds(45));

    String envelopeJson =
        queryForString(
            """
            SELECT jsonb_build_object(
                     'eventId', event_id,
                     'eventType', event_type,
                     'eventVersion', event_version,
                     'aggregateType', aggregate_type,
                     'aggregateId', aggregate_id,
                     'occurredAt', occurred_at,
                     'correlationId', correlation_id,
                     'causationId', causation_id,
                     'actor', jsonb_build_object('type', actor_type, 'id', actor_id),
                     'payload', payload_json
                   )::text
            FROM outbox_event
            WHERE event_id = ?
            """,
            UUID.fromString(eventId));

    produceRawEvent("case.lifecycle.v1", createdCase.getId().toString(), envelopeJson);
    produceRawEvent("case.lifecycle.v1", createdCase.getId().toString(), envelopeJson);

    awaitCondition(
        () ->
            queryForLong(
                    "SELECT COUNT(*) FROM notification WHERE event_id = ?",
                    UUID.fromString(eventId))
                == 1L,
        Duration.ofSeconds(20));
    assertEquals(
        1L,
        queryForLong(
            "SELECT COUNT(*) FROM inbox_event WHERE consumer_name = ? AND event_id = ?",
            "notification-consumer",
            UUID.fromString(eventId)));
  }

  @Test
  void phaseSevenLifecycleEventsProduceNotificationsWithoutMissingTopicWarnings() {
    if (!KAFKA.isRunning()) {
      KAFKA.start();
    }

    DecisionFlowContext context =
        createPublishedDecisionContext(
            "Phase seven notifications", "Flow test.", true, LocalDate.parse("2026-08-15"));

    awaitCondition(
        () ->
            queryForLong(
                    "SELECT COUNT(*) FROM notification WHERE case_id = ? AND notification_type = 'DecisionPublished'",
                    context.caseResponse().getId())
                == 1L,
        Duration.ofSeconds(45));
    awaitCondition(
        () ->
            queryForLong(
                    "SELECT COUNT(*) FROM notification WHERE case_id = ? AND notification_type = 'SanctionCreated'",
                    context.caseResponse().getId())
                == 1L,
        Duration.ofSeconds(45));

    var appeal =
        createAppeal(
            accessToken("appeal-jkt"),
            context.decisionId(),
            "New mitigating evidence",
            OffsetDateTime.parse("2026-07-20T10:00:00Z"),
            false,
            null);
    decideAppeal(
        accessToken("supervisor-jkt"),
        appeal.getId(),
        AppealDecisionOutcomeValue.GRANTED,
        "Granted");

    WorkflowTaskResponse appealReviewTask =
        singleTask(accessToken("appeal-jkt"), context.caseResponse().getId(), "appealReviewTask");
    claimTask(accessToken("appeal-jkt"), appealReviewTask.getTaskId());
    completeTask(accessToken("appeal-jkt"), appealReviewTask.getTaskId());

    awaitCondition(
        () ->
            queryForLong(
                    "SELECT COUNT(*) FROM notification WHERE case_id = ? AND notification_type = 'AppealFiled'",
                    context.caseResponse().getId())
                == 1L,
        Duration.ofSeconds(45));
    awaitCondition(
        () ->
            queryForLong(
                    "SELECT COUNT(*) FROM notification WHERE case_id = ? AND notification_type = 'AppealDecided'",
                    context.caseResponse().getId())
                == 1L,
        Duration.ofSeconds(45));
    awaitCondition(
        () ->
            queryForLong(
                    "SELECT COUNT(*) FROM notification WHERE case_id = ? AND notification_type = 'SanctionCancelled'",
                    context.caseResponse().getId())
                == 1L,
        Duration.ofSeconds(45));
  }

  private static DecisionFlowContext createPublishedDecisionContext(
      String caseTitle, String caseSummary, boolean violationProven, LocalDate appealDeadline) {
    ReportResponse report =
        createTriagedReport(accessToken("intake-jkt"), accessToken("triage-jkt"), "JKT");
    CaseResponse createdCase =
        createCase(accessToken("triage-jkt"), report.getId(), caseTitle, caseSummary);
    CaseResponse assigned =
        assignCase(
            accessToken("triage-jkt"),
            createdCase.getId(),
            "JKT-UNIT-1",
            "investigator-jkt",
            createdCase.getVersion(),
            "Assign investigator for messaging flow.");
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

    var recommendation =
        createRecommendation(
            accessToken("investigator-jkt"),
            underInvestigation.getId(),
            "Recommendation for " + caseTitle,
            "Investigation summary for " + caseSummary,
            "Proceed to formal decision.",
            violationProven ? "Impose corrective sanction." : null);
    submitRecommendation(accessToken("investigator-jkt"), recommendation.getId());

    CaseResponse pendingReview =
        transitionCase(
            accessToken("investigator-jkt"),
            underInvestigation.getId(),
            CaseStatusValue.PENDING_REVIEW,
            underInvestigation.getVersion(),
            "Investigation complete and recommendation submitted.");
    approveRecommendation(
        accessToken("reviewer-jkt"),
        recommendation.getId(),
        "Recommendation approved for decision.");
    CaseResponse pendingDecision =
        transitionCase(
            accessToken("reviewer-jkt"),
            pendingReview.getId(),
            CaseStatusValue.PENDING_DECISION,
            pendingReview.getVersion(),
            "Review accepted and escalated to decision.");

    var decision =
        createDecision(
            accessToken("decision-jkt"),
            pendingDecision.getId(),
            "Decision for " + caseTitle,
            "Decision summary for " + caseSummary,
            violationProven,
            violationProven ? "Formal sanction imposed." : null,
            violationProven ? "Submit remediation report" : null,
            violationProven ? "Provide written remediation evidence." : null,
            violationProven ? appealDeadline.plusDays(14) : null,
            appealDeadline);
    approveDecision(accessToken("supervisor-jkt"), decision.getId());
    publishDecision(accessToken("decision-jkt"), decision.getId());
    CaseResponse decided =
        transitionCase(
            accessToken("decision-jkt"),
            pendingDecision.getId(),
            CaseStatusValue.DECIDED,
            pendingDecision.getVersion(),
            "Decision published and case marked as decided.");
    return new DecisionFlowContext(decided, decision.getId());
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
                new CreateCaseRequest()
                    .reportId(reportId)
                    .title(title)
                    .summary(summary)
                    .classification(CaseClassificationValue.CONFIDENTIAL),
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

  private static WorkflowTaskResponse singleTask(
      String accessToken, UUID caseId, String taskDefinitionKey) {
    WorkflowTaskListResponse response =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/tasks")
            .queryParam("caseId", caseId)
            .queryParam("taskDefinitionKey", taskDefinitionKey)
            .queryParam("limit", 10)
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .get(WorkflowTaskListResponse.class);
    assertEquals(1, response.getItems().size());
    return response.getItems().get(0);
  }

  private static void claimTask(String accessToken, String taskId) {
    try (Response response =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/tasks/" + taskId + "/claim")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .post(Entity.entity("", MediaType.APPLICATION_JSON_TYPE))) {
      assertTrue(
          response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL,
          "Task claim failed with status " + response.getStatus());
    }
  }

  private static void completeTask(String accessToken, String taskId) {
    try (Response response =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/tasks/" + taskId + "/complete")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .post(Entity.entity("", MediaType.APPLICATION_JSON_TYPE))) {
      assertTrue(
          response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL,
          "Task completion failed with status " + response.getStatus());
    }
  }

  private static void awaitCondition(BooleanSupplier supplier, Duration timeout) {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      if (supplier.getAsBoolean()) {
        return;
      }
      try {
        Thread.sleep(500L);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for condition.", exception);
      }
    }
    assertTrue(supplier.getAsBoolean(), "Condition was not satisfied before timeout.");
  }

  private record DecisionFlowContext(CaseResponse caseResponse, UUID decisionId) {}
}
