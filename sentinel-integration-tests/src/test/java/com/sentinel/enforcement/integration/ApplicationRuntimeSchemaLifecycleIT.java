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
                        .withStartupTimeout(Duration.ofMinutes(3)))) {
      postgres.start();
      keycloak.start();

      AppConfiguration configuration =
          AppConfiguration.fromEnvironment(
              Map.of(
                  "HTTP_PORT",
                  "0",
                  "DB_URL",
                  postgres.getJdbcUrl(),
                  "DB_USERNAME",
                  postgres.getUsername(),
                  "DB_PASSWORD",
                  postgres.getPassword(),
                  "KEYCLOAK_ISSUER",
                  "http://127.0.0.1:" + keycloak.getMappedPort(8080) + "/realms/sentinel",
                  "KEYCLOAK_AUDIENCE",
                  "sentinel-api",
                  "KEYCLOAK_JWKS_URL",
                  "http://127.0.0.1:"
                      + keycloak.getMappedPort(8080)
                      + "/realms/sentinel/protocol/openid-connect/certs",
                  "WORKFLOW_INVESTIGATION_ESCALATION_DURATION",
                  "PT2S"));

      assertThrows(ProcessEngineException.class, () -> ApplicationRuntime.start(configuration));

      ApplicationRuntime.migrate(configuration);
      try (ApplicationRuntime runtime = ApplicationRuntime.start(configuration)) {
        assertNotNull(runtime.baseUri());
      }
    }
  }
}
