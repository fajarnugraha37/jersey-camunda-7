package com.sentinel.enforcement.bootstrap;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public record AppConfiguration(
    int httpPort,
    String dbUrl,
    String dbUsername,
    String dbPassword,
    String kafkaBootstrapServers,
    String redisHost,
    int redisPort,
    String mailpitSmtpHost,
    int mailpitSmtpPort,
    String notificationFromEmail,
    String notificationToEmail,
    String appInstanceId,
    Duration outboxPollInterval,
    Duration outboxLeaseDuration,
    int outboxBatchSize,
    String notificationConsumerGroupId,
    int notificationMaxRetries,
    String minioEndpoint,
    String minioPublicEndpoint,
    String minioAccessKey,
    String minioSecretKey,
    String minioEvidenceBucket,
    Duration evidenceUploadUrlTtl,
    Duration evidenceDownloadUrlTtl,
    String keycloakIssuer,
    String keycloakAudience,
    String keycloakJwksUrl,
    String workflowEngineName,
    Duration workflowInvestigationEscalationDuration) {

  public static AppConfiguration fromEnvironment() {
    return fromEnvironment(System.getenv());
  }

  public static AppConfiguration fromEnvironment(Map<String, String> environment) {
    return new AppConfiguration(
        Integer.parseInt(required(environment, "HTTP_PORT")),
        required(environment, "DB_URL"),
        required(environment, "DB_USERNAME"),
        required(environment, "DB_PASSWORD"),
        required(environment, "KAFKA_BOOTSTRAP_SERVERS"),
        required(environment, "REDIS_HOST"),
        Integer.parseInt(required(environment, "REDIS_PORT")),
        required(environment, "MAILPIT_SMTP_HOST"),
        Integer.parseInt(required(environment, "MAILPIT_SMTP_PORT")),
        required(environment, "NOTIFICATION_FROM_EMAIL"),
        required(environment, "NOTIFICATION_TO_EMAIL"),
        environment.getOrDefault("APP_INSTANCE_ID", UUID.randomUUID().toString()),
        Duration.parse(environment.getOrDefault("OUTBOX_POLL_INTERVAL", "PT2S")),
        Duration.parse(environment.getOrDefault("OUTBOX_LEASE_DURATION", "PT30S")),
        Integer.parseInt(environment.getOrDefault("OUTBOX_BATCH_SIZE", "20")),
        environment.getOrDefault(
            "NOTIFICATION_CONSUMER_GROUP_ID", "sentinel-notification-consumer"),
        Integer.parseInt(environment.getOrDefault("NOTIFICATION_MAX_RETRIES", "3")),
        required(environment, "MINIO_ENDPOINT"),
        environment.getOrDefault("MINIO_PUBLIC_ENDPOINT", required(environment, "MINIO_ENDPOINT")),
        required(environment, "MINIO_ACCESS_KEY"),
        required(environment, "MINIO_SECRET_KEY"),
        required(environment, "MINIO_EVIDENCE_BUCKET"),
        Duration.parse(required(environment, "EVIDENCE_UPLOAD_URL_TTL")),
        Duration.parse(required(environment, "EVIDENCE_DOWNLOAD_URL_TTL")),
        required(environment, "KEYCLOAK_ISSUER"),
        required(environment, "KEYCLOAK_AUDIENCE"),
        required(environment, "KEYCLOAK_JWKS_URL"),
        environment.getOrDefault("WORKFLOW_ENGINE_NAME", "sentinel-workflow-engine"),
        Duration.parse(required(environment, "WORKFLOW_INVESTIGATION_ESCALATION_DURATION")));
  }

  private static String required(Map<String, String> environment, String key) {
    String value = environment.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing required configuration: " + key);
    }
    return value;
  }
}
