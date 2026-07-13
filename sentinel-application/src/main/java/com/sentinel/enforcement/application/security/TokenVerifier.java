package com.sentinel.enforcement.application.security;

public interface TokenVerifier {
  ApplicationActor verify(String bearerToken);
}
