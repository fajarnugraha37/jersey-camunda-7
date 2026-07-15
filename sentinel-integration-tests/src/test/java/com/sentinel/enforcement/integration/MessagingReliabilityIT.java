package com.sentinel.enforcement.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentinel.enforcement.api.generated.model.CaseResponse;
import com.sentinel.enforcement.api.generated.model.CreateCaseRequest;
import com.sentinel.enforcement.api.generated.model.ReportResponse;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.time.Duration;
import java.time.Instant;
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
}
