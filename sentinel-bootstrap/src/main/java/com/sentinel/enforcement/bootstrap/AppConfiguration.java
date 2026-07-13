package com.sentinel.enforcement.bootstrap;

import java.util.Map;

public record AppConfiguration(
    int httpPort,
    String dbUrl,
    String dbUsername,
    String dbPassword,
    String keycloakIssuer,
    String keycloakAudience,
    String keycloakJwksUrl) {

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
        required(environment, "KEYCLOAK_JWKS_URL"));
  }

  private static String required(Map<String, String> environment, String key) {
    String value = environment.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing required configuration: " + key);
    }
    return value;
  }
}
