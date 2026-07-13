package com.sentinel.enforcement.api.security;

import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.UnauthenticatedException;
import jakarta.ws.rs.container.ContainerRequestContext;

public final class RequestActorResolver {
  private RequestActorResolver() {}

  public static ApplicationActor resolveRequired(ContainerRequestContext requestContext) {
    Object actor = requestContext.getProperty(BearerAuthenticationFilter.ACTOR_REQUEST_PROPERTY);
    if (actor instanceof ApplicationActor applicationActor) {
      return applicationActor;
    }
    throw new UnauthenticatedException("Authenticated actor is missing from request context.");
  }
}
