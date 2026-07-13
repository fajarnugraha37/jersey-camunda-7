package com.sentinel.enforcement.application.security;

public final class UnauthenticatedException extends RuntimeException {
  public UnauthenticatedException(String message) {
    super(message);
  }

  public UnauthenticatedException(String message, Throwable cause) {
    super(message, cause);
  }
}
