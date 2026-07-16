package com.sentinel.enforcement.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sentinel.enforcement.bootstrap.AppConfiguration;
import com.sentinel.enforcement.bootstrap.ApplicationRuntime;
import java.time.Duration;
import java.util.Map;
import org.camunda.bpm.engine.ProcessEngineException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class ApplicationRuntimeSchemaLifecycleIT {

  @Test
  void applicationStartupRequiresMigrationToRunFirst() {
    try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.3-alpine");
        AbstractApiIT.StablePortKafkaContainer kafka = AbstractApiIT.createKafkaContainer();
        GenericContainer<?> redis =
            new GenericContainer<>("redis:7.2.7-alpine")
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
        GenericContainer<?> mailpit =
            new GenericContainer<>("axllent/mailpit:latest")
                .withExposedPorts(1025, 8025)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
        GenericContainer<?> keycloak =
            new GenericContainer<>("quay.io/keycloak/keycloak:26.6")
                .withExposedPorts(8080, 9000)
                .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
                .withEnv("KC_HEALTH_ENABLED", "true")
                .withCommand("start-dev", "--http-port=8080", "--import-realm")
                .withClasspathResourceMapping(
                    "keycloak/sentinel-realm.json",
                    "/opt/keycloak/data/import/sentinel-realm.json",
                    BindMode.READ_ONLY)
                .waitingFor(
                    Wait.forHttp("/health/ready")
                        .forPort(9000)
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        GenericContainer<?> minio =
            new GenericContainer<>("quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z")
                .withExposedPorts(9000, 9001)
                .withEnv("MINIO_ROOT_USER", "sentinel")
                .withEnv("MINIO_ROOT_PASSWORD", "sentinel-secret")
                .withCommand("server", "/data", "--console-address", ":9001")
                .waitingFor(
                    Wait.forHttp("/minio/health/ready")
                        .forPort(9000)
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(2)))) {
      postgres.start();
      kafka.start();
      redis.start();
      mailpit.start();
      keycloak.start();
      minio.start();

      AppConfiguration configuration =
          AppConfiguration.fromEnvironment(
              Map.ofEntries(
                  Map.entry("HTTP_PORT", "0"),
                  Map.entry("DB_URL", postgres.getJdbcUrl()),
                  Map.entry("DB_USERNAME", postgres.getUsername()),
                  Map.entry("DB_PASSWORD", postgres.getPassword()),
                  Map.entry("KAFKA_BOOTSTRAP_SERVERS", kafka.getBootstrapServers()),
                  Map.entry("APP_INSTANCE_ID", "schema-lifecycle-it"),
                  Map.entry("OUTBOX_POLL_INTERVAL", "PT1S"),
                  Map.entry("OUTBOX_LEASE_DURATION", "PT10S"),
                  Map.entry("OUTBOX_BATCH_SIZE", "10"),
                  Map.entry("NOTIFICATION_CONSUMER_GROUP_ID", "schema-lifecycle-it"),
                  Map.entry("NOTIFICATION_MAX_RETRIES", "2"),
                  Map.entry("REDIS_HOST", redis.getHost()),
                  Map.entry("REDIS_PORT", Integer.toString(redis.getMappedPort(6379))),
                  Map.entry("MAILPIT_SMTP_HOST", mailpit.getHost()),
                  Map.entry("MAILPIT_SMTP_PORT", Integer.toString(mailpit.getMappedPort(1025))),
                  Map.entry("NOTIFICATION_FROM_EMAIL", "schema-it@local.test"),
                  Map.entry("NOTIFICATION_TO_EMAIL", "ops-schema-it@local.test"),
                  Map.entry("MINIO_ENDPOINT", "http://127.0.0.1:" + minio.getMappedPort(9000)),
                  Map.entry("MINIO_ACCESS_KEY", "sentinel"),
                  Map.entry("MINIO_SECRET_KEY", "sentinel-secret"),
                  Map.entry("MINIO_EVIDENCE_BUCKET", "sentinel-evidence"),
                  Map.entry("EVIDENCE_UPLOAD_URL_TTL", "PT15M"),
                  Map.entry("EVIDENCE_DOWNLOAD_URL_TTL", "PT10M"),
                  Map.entry(
                      "KEYCLOAK_ISSUER",
                      "http://127.0.0.1:" + keycloak.getMappedPort(8080) + "/realms/sentinel"),
                  Map.entry("KEYCLOAK_AUDIENCE", "sentinel-api"),
                  Map.entry(
                      "KEYCLOAK_JWKS_URL",
                      "http://127.0.0.1:"
                          + keycloak.getMappedPort(8080)
                          + "/realms/sentinel/protocol/openid-connect/certs"),
                  Map.entry("WORKFLOW_INVESTIGATION_ESCALATION_DURATION", "PT2S")));

      assertThrows(ProcessEngineException.class, () -> ApplicationRuntime.start(configuration));

      ApplicationRuntime.migrate(configuration);
      try (ApplicationRuntime runtime = ApplicationRuntime.start(configuration)) {
        assertNotNull(runtime.baseUri());
      }
    }
  }
}
