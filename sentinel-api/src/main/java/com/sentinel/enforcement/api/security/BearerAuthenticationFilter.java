package com.sentinel.enforcement.api.security;

import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.TokenVerifier;
import com.sentinel.enforcement.application.security.UnauthenticatedException;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.AUTHENTICATION)
public final class BearerAuthenticationFilter implements ContainerRequestFilter {
  public static final String ACTOR_REQUEST_PROPERTY =
      BearerAuthenticationFilter.class.getName() + ".actor";

  private final TokenVerifier tokenVerifier;

  @Inject
  public BearerAuthenticationFilter(TokenVerifier tokenVerifier) {
    this.tokenVerifier = tokenVerifier;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (isPublicEndpoint(requestContext)) {
      return;
    }

    String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
      throw new UnauthenticatedException("Bearer access token is required.");
    }

    String token = authorizationHeader.substring("Bearer ".length()).trim();
    ApplicationActor actor = tokenVerifier.verify(token);
    requestContext.setProperty(ACTOR_REQUEST_PROPERTY, actor);
  }

  private boolean isPublicEndpoint(ContainerRequestContext requestContext) {
    String path = requestContext.getUriInfo().getPath(false);
    return "health".equals(path);
  }
}
