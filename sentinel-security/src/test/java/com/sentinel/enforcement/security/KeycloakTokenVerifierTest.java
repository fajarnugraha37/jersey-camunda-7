package com.sentinel.enforcement.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.JWTProcessor;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.domain.casefile.CaseClassification;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeycloakTokenVerifierTest {

  @Test
  void verifiesExtendedAuthorizationClaimsFromToken() throws Exception {
    Clock clock = Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneOffset.UTC);
    KeycloakSecurityConfiguration configuration =
        new KeycloakSecurityConfiguration(
            URI.create("http://localhost/realms/sentinel"),
            "sentinel-api",
            URI.create("http://localhost/realms/sentinel/protocol/openid-connect/certs"));
    JWTProcessor<com.nimbusds.jose.proc.SecurityContext> jwtProcessor =
        new JWTProcessor<>() {
          @Override
          public JWTClaimsSet process(
              String token, com.nimbusds.jose.proc.SecurityContext context) {
            return claims(clock);
          }

          @Override
          public JWTClaimsSet process(
              com.nimbusds.jwt.JWT jwt, com.nimbusds.jose.proc.SecurityContext context)
              throws BadJOSEException, JOSEException {
            throw new UnsupportedOperationException();
          }

          @Override
          public JWTClaimsSet process(
              SignedJWT signedJWT, com.nimbusds.jose.proc.SecurityContext context)
              throws BadJOSEException, JOSEException {
            throw new UnsupportedOperationException();
          }

          @Override
          public JWTClaimsSet process(
              EncryptedJWT encryptedJWT, com.nimbusds.jose.proc.SecurityContext context)
              throws BadJOSEException, JOSEException {
            throw new UnsupportedOperationException();
          }

          @Override
          public JWTClaimsSet process(
              PlainJWT plainJWT, com.nimbusds.jose.proc.SecurityContext context)
              throws BadJOSEException, JOSEException {
            throw new UnsupportedOperationException();
          }
        };

    ApplicationActor actor =
        new KeycloakTokenVerifier(configuration, clock, jwtProcessor).verify("token");

    assertEquals("reviewer-jkt-conflicted", actor.username());
    assertEquals(List.of("JKT-UNIT-1"), actor.assignedUnits().stream().sorted().toList());
    assertTrue(actor.caseClassifications().contains(CaseClassification.PUBLIC));
    assertTrue(actor.caseClassifications().contains(CaseClassification.CONFIDENTIAL));
    assertTrue(actor.isConflictedWith("investigator-jkt"));
  }

  private static JWTClaimsSet claims(Clock clock) {
    return new JWTClaimsSet.Builder()
        .subject("subject-1")
        .issuer("http://localhost/realms/sentinel")
        .audience(List.of("sentinel-api"))
        .claim("preferred_username", "reviewer-jkt-conflicted")
        .claim("realm_access", Map.of("roles", List.of("CASE_REVIEWER")))
        .claim("jurisdictions", List.of("JKT"))
        .claim("assigned_units", List.of("JKT-UNIT-1"))
        .claim("case_classifications", List.of("PUBLIC", "CONFIDENTIAL"))
        .claim("conflicted_actor_ids", List.of("investigator-jkt"))
        .expirationTime(java.util.Date.from(clock.instant().plusSeconds(300)))
        .notBeforeTime(java.util.Date.from(clock.instant().minusSeconds(30)))
        .build();
  }
}
