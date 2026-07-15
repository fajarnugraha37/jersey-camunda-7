package com.sentinel.enforcement.messaging;

import com.sentinel.enforcement.application.messaging.ApplicationTransactionManager;
import com.sentinel.enforcement.application.messaging.EventEnvelope;
import com.sentinel.enforcement.application.messaging.InboxEvent;
import com.sentinel.enforcement.application.messaging.InboxRepository;
import com.sentinel.enforcement.application.messaging.NotificationRecord;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class NotificationEventHandler {
  static final String CONSUMER_NAME = "notification-consumer";

  private final ApplicationTransactionManager transactionManager;
  private final InboxRepository inboxRepository;
  private final com.sentinel.enforcement.application.messaging.NotificationRepository
      notificationRepository;
  private final Clock clock;

  NotificationEventHandler(
      ApplicationTransactionManager transactionManager,
      InboxRepository inboxRepository,
      com.sentinel.enforcement.application.messaging.NotificationRepository notificationRepository,
      Clock clock) {
    this.transactionManager = transactionManager;
    this.inboxRepository = inboxRepository;
    this.notificationRepository = notificationRepository;
    this.clock = clock;
  }

  void handle(String topic, EventEnvelope eventEnvelope) {
    Instant now = clock.instant();
    transactionManager.required(
        () -> {
          InboxEvent inboxEvent =
              new InboxEvent(
                  UUID.randomUUID(),
                  CONSUMER_NAME,
                  eventEnvelope.eventId(),
                  topic,
                  now,
                  CONSUMER_NAME,
                  null,
                  null,
                  0L);
          if (!inboxRepository.beginProcessing(inboxEvent)) {
            return null;
          }

          NotificationRecord notification = toNotificationRecord(eventEnvelope, now);
          notificationRepository.save(notification);
          inboxRepository.completeProcessing(
              CONSUMER_NAME,
              eventEnvelope.eventId(),
              now,
              notification.id().toString(),
              CONSUMER_NAME);
          return null;
        });
  }

  private NotificationRecord toNotificationRecord(EventEnvelope eventEnvelope, Instant now) {
    Map<String, Object> payload = eventEnvelope.payload();
    String eventType = eventEnvelope.eventType();
    String title;
    String body;
    UUID caseId;

    switch (eventType) {
      case "CaseCreated" -> {
        String caseNumber = required(payload, "caseNumber");
        title = "Case " + caseNumber + " created";
        body = "Case " + caseNumber + " entered the enforcement workflow.";
        caseId = UUID.fromString(required(payload, "caseId"));
      }
      case "CaseAssigned" -> {
        String caseNumber = required(payload, "caseNumber");
        title = "Case " + caseNumber + " assigned";
        body = "Case " + caseNumber + " assigned to " + required(payload, "assigneeUserId") + ".";
        caseId = UUID.fromString(required(payload, "caseId"));
      }
      case "CaseTransitioned" -> {
        String caseNumber = required(payload, "caseNumber");
        title = "Case " + caseNumber + " transitioned";
        body =
            "Case "
                + caseNumber
                + " moved from "
                + required(payload, "fromStatus")
                + " to "
                + required(payload, "toStatus")
                + ".";
        caseId = UUID.fromString(required(payload, "caseId"));
      }
      case "EvidenceVersionFinalized" -> {
        title = "Evidence finalized";
        body =
            "Evidence "
                + required(payload, "title")
                + " version "
                + required(payload, "versionNumber")
                + " is now active.";
        caseId = UUID.fromString(required(payload, "caseId"));
      }
      default -> throw new IllegalArgumentException("Unsupported event type: " + eventType);
    }

    return new NotificationRecord(
        UUID.randomUUID(),
        CONSUMER_NAME,
        eventEnvelope.eventId(),
        caseId,
        eventType,
        title,
        body,
        "GENERATED",
        now,
        CONSUMER_NAME,
        now,
        CONSUMER_NAME,
        0L);
  }

  private String required(Map<String, Object> payload, String key) {
    return Objects.toString(payload.get(key), null);
  }
}
