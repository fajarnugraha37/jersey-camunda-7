package com.sentinel.enforcement.application.appeal;

import java.util.UUID;

public final class AppealNotFoundException extends RuntimeException {
  public AppealNotFoundException(UUID appealId) {
    super("Appeal " + appealId + " was not found.");
  }
}
