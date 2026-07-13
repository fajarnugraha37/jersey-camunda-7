package com.sentinel.enforcement.application.security;

public interface AuthorizationService {
  void requirePermission(
      ApplicationActor actor, Permission permission, AuthorizationContext authorizationContext);
}
