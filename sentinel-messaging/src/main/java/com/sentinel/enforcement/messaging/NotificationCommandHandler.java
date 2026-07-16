package com.sentinel.enforcement.messaging;

import com.sentinel.enforcement.application.messaging.ApplicationTransactionManager;
import com.sentinel.enforcement.application.messaging.EventEnvelope;
import com.sentinel.enforcement.application.messaging.InboxEvent;
import com.sentinel.enforcement.application.messaging.InboxRepository;
import com.sentinel.enforcement.application.messaging.MessagingEventFactory;
import com.sentinel.enforcement.application.messaging.NotificationRecord;
import com.sentinel.enforcement.application.messaging.NotificationRepository;
import com.sentinel.enforcement.application.messaging.OutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class NotificationCommandHandler {
  static final String CONSUMER_NAME = "notification-command-consumer";

  private final ApplicationTransactionManager transactionManager;
  private final InboxRepository inboxRepository;
  private final NotificationRepository notificationRepository;
  private final OutboxRepository outboxRepository;
  private final NotificationEmailSender notificationEmailSender;
  private final Clock clock;

  NotificationCommandHandler(
      ApplicationTransactionManager transactionManager,
      InboxRepository inboxRepository,
      NotificationRepository notificationRepository,
      OutboxRepository outboxRepository,
      NotificationEmailSender notificationEmailSender,
      Clock clock) {
    this.transactionManager = transactionManager;
    this.inboxRepository = inboxRepository;
    this.notificationRepository = notificationRepository;
    this.outboxRepository = outboxRepository;
    this.notificationEmailSender = notificationEmailSender;
    this.clock = clock;
  }

  void handle(String topic, EventEnvelope eventEnvelope) {
    Instant now = clock.instant();
    NotificationPayload payload = NotificationPayload.from(eventEnvelope);
    notificationEmailSender.send(
        payload.fromEmail(), payload.toEmail(), payload.title(), payload.body());
    transactionManager.required(
        () -> {
          if (notificationRepository.findById(payload.notificationId()).isEmpty()) {
            notificationRepository.save(
                new NotificationRecord(
                    payload.notificationId(),
                    CONSUMER_NAME,
                    eventEnvelope.eventId(),
                    payload.caseId(),
                    payload.notificationType(),
                    payload.title(),
                    payload.body(),
                    "SENT",
                    now,
                    CONSUMER_NAME,
                    now,
                    CONSUMER_NAME,
                    0L));
          } else {
            notificationRepository.updateStatus(payload.notificationId(), "SENT", CONSUMER_NAME);
          }
          outboxRepository.enqueue(
              MessagingEventFactory.notificationResult(
                  payload.notificationId(),
                  payload.caseId(),
                  payload.notificationType(),
                  "SENT",
                  payload.toEmail(),
                  eventEnvelope.correlationId(),
                  null,
                  now));
          inboxRepository.completeProcessing(
              CONSUMER_NAME,
              eventEnvelope.eventId(),
              now,
              payload.notificationId().toString(),
              CONSUMER_NAME);
          return null;
        });
  }

  void markPermanentFailure(String topic, EventEnvelope eventEnvelope, String errorDetail) {
    Instant now = clock.instant();
    NotificationPayload payload = NotificationPayload.from(eventEnvelope);
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
          if (notificationRepository.findById(payload.notificationId()).isEmpty()) {
            notificationRepository.save(
                new NotificationRecord(
                    payload.notificationId(),
                    CONSUMER_NAME,
                    eventEnvelope.eventId(),
                    payload.caseId(),
                    payload.notificationType(),
                    payload.title(),
                    payload.body(),
                    "FAILED",
                    now,
                    CONSUMER_NAME,
                    now,
                    CONSUMER_NAME,
                    0L));
          } else {
            notificationRepository.updateStatus(payload.notificationId(), "FAILED", CONSUMER_NAME);
          }
          outboxRepository.enqueue(
              MessagingEventFactory.notificationResult(
                  payload.notificationId(),
                  payload.caseId(),
                  payload.notificationType(),
                  "FAILED",
                  payload.toEmail(),
                  eventEnvelope.correlationId(),
                  errorDetail,
                  now));
          inboxRepository.completeProcessing(
              CONSUMER_NAME,
              eventEnvelope.eventId(),
              now,
              payload.notificationId().toString(),
              CONSUMER_NAME);
          return null;
        });
  }

  private record NotificationPayload(
      UUID notificationId,
      UUID caseId,
      String notificationType,
      String title,
      String body,
      String toEmail,
      String fromEmail) {

    private static NotificationPayload from(EventEnvelope eventEnvelope) {
      Map<String, Object> payload = eventEnvelope.payload();
      return new NotificationPayload(
          UUID.fromString(required(payload, "notificationId")),
          optionalUuid(payload.get("caseId")),
          required(payload, "notificationType"),
          required(payload, "title"),
          required(payload, "body"),
          required(payload, "toEmail"),
          required(payload, "fromEmail"));
    }

    private static UUID optionalUuid(Object value) {
      String text = Objects.toString(value, null);
      return text == null || text.isBlank() ? null : UUID.fromString(text);
    }

    private static String required(Map<String, Object> payload, String key) {
      String value = Objects.toString(payload.get(key), null);
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException(
            "Notification command payload is missing required field: " + key);
      }
      return value;
    }
  }
}
