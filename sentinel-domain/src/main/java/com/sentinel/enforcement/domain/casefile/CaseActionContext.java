package com.sentinel.enforcement.domain.casefile;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record CaseActionContext(
    String actorId,
    Set<String> actorRoles,
    long expectedVersion,
    String reason,
    Instant timestamp) {

  public CaseActionContext {
    actorId = requireNonBlank(actorId, "actorId");
    actorRoles = Set.copyOf(actorRoles);
    if (actorRoles.isEmpty()) {
      throw new IllegalArgumentException("actorRoles must not be empty");
    }
    reason = requireNonBlank(reason, "reason");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
    if (expectedVersion < 0) {
      throw new IllegalArgumentException("expectedVersion must not be negative");
    }
  }

  public boolean hasAnyRole(String... roles) {
    for (String role : roles) {
      if (actorRoles.contains(role)) {
        return true;
      }
    }
    return false;
  }

  private static String requireNonBlank(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}
