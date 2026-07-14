package com.sentinel.enforcement.bootstrap;

import java.time.Duration;
import java.util.Map;

public record AppConfiguration(
    int httpPort,
    String dbUrl,
    String dbUsername,
    String dbPassword,
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
