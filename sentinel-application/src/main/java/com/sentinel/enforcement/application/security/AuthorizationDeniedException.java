package com.sentinel.enforcement.application.security;

public final class AuthorizationDeniedException extends RuntimeException {
  public AuthorizationDeniedException(String message) {
    super(message);
  }
}
