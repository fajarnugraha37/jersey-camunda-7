package com.sentinel.enforcement.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSAlgorithmFamilyJWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.TokenVerifier;
import com.sentinel.enforcement.application.security.UnauthenticatedException;
import java.net.URL;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class KeycloakTokenVerifier implements TokenVerifier {
  private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";
  private static final String CLAIM_REALM_ACCESS = "realm_access";
  private static final String CLAIM_ROLES = "roles";
  private static final String CLAIM_JURISDICTIONS = "jurisdictions";

  private final KeycloakSecurityConfiguration configuration;
  private final Clock clock;
  private final JWTProcessor<SecurityContext> jwtProcessor;

  public KeycloakTokenVerifier(KeycloakSecurityConfiguration configuration, Clock clock) {
    this(configuration, clock, createJwtProcessor(configuration));
  }

  KeycloakTokenVerifier(
      KeycloakSecurityConfiguration configuration,
      Clock clock,
      JWTProcessor<SecurityContext> jwtProcessor) {
    this.configuration = configuration;
    this.clock = clock;
    this.jwtProcessor = jwtProcessor;
  }

  @Override
  public ApplicationActor verify(String bearerToken) {
    if (bearerToken == null || bearerToken.isBlank()) {
      throw new UnauthenticatedException("Bearer token is required.");
    }
    try {
      JWTClaimsSet claims = jwtProcessor.process(bearerToken, null);
      validateClaims(claims);
      return new ApplicationActor(
          requiredClaim(claims.getSubject(), "sub"),
          requiredClaim(claims.getStringClaim(CLAIM_PREFERRED_USERNAME), CLAIM_PREFERRED_USERNAME),
          extractRoles(claims),
          extractJurisdictions(claims));
    } catch (UnauthenticatedException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new UnauthenticatedException("Access token is invalid.", exception);
    }
  }

  private void validateClaims(JWTClaimsSet claims) throws ParseException {
    if (!Objects.equals(configuration.issuer().toString(), claims.getIssuer())) {
      throw new UnauthenticatedException("Access token issuer is invalid.");
    }
    List<String> audience = claims.getAudience();
    if (audience == null || !audience.contains(configuration.audience())) {
      throw new UnauthenticatedException("Access token audience is invalid.");
    }

    Instant now = clock.instant();
    if (claims.getExpirationTime() == null
        || claims.getExpirationTime().toInstant().isBefore(now)) {
      throw new UnauthenticatedException("Access token has expired.");
    }
    if (claims.getNotBeforeTime() != null && claims.getNotBeforeTime().toInstant().isAfter(now)) {
      throw new UnauthenticatedException("Access token is not yet valid.");
    }
  }

  private Set<String> extractRoles(JWTClaimsSet claims) throws ParseException {
    Object realmAccessClaim = claims.getClaim(CLAIM_REALM_ACCESS);
    if (!(realmAccessClaim instanceof java.util.Map<?, ?> realmAccess)) {
      return Set.of();
    }
    Object rolesClaim = realmAccess.get(CLAIM_ROLES);
    if (!(rolesClaim instanceof Collection<?> rolesCollection)) {
      return Set.of();
    }
    LinkedHashSet<String> roles = new LinkedHashSet<>();
    for (Object role : rolesCollection) {
      if (role instanceof String roleValue && !roleValue.isBlank()) {
        roles.add(roleValue);
      }
    }
    return Set.copyOf(roles);
  }

  private Set<String> extractJurisdictions(JWTClaimsSet claims) throws ParseException {
    Object jurisdictionsClaim = claims.getClaim(CLAIM_JURISDICTIONS);
    if (jurisdictionsClaim instanceof String singleJurisdiction && !singleJurisdiction.isBlank()) {
      return Set.of(singleJurisdiction);
    }
    if (!(jurisdictionsClaim instanceof Collection<?> jurisdictionsCollection)) {
      return Set.of();
    }
    LinkedHashSet<String> jurisdictions = new LinkedHashSet<>();
    for (Object jurisdiction : jurisdictionsCollection) {
      if (jurisdiction instanceof String jurisdictionValue && !jurisdictionValue.isBlank()) {
        jurisdictions.add(jurisdictionValue);
      }
    }
    return Set.copyOf(jurisdictions);
  }

  private String requiredClaim(String value, String claimName) {
    if (value == null || value.isBlank()) {
      throw new UnauthenticatedException(
          "Access token is missing required claim: " + claimName + ".");
    }
    return value;
  }

  private static JWTProcessor<SecurityContext> createJwtProcessor(
      KeycloakSecurityConfiguration configuration) {
    try {
      URL jwksUrl = configuration.jwksUri().toURL();
      JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(jwksUrl);
      DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
      processor.setJWSKeySelector(
          new JWSAlgorithmFamilyJWSKeySelector<>(JWSAlgorithm.Family.RSA, jwkSource));
      return processor;
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to initialize JWKS verifier.", exception);
    }
  }
}
