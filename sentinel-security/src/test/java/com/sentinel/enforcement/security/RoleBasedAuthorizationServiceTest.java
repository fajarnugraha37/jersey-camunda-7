package com.sentinel.enforcement.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationDeniedException;
import com.sentinel.enforcement.application.security.Permission;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleBasedAuthorizationServiceTest {

  private final RoleBasedAuthorizationService authorizationService =
      new RoleBasedAuthorizationService();

  @Test
  void allowsIntakeOfficerWithinJurisdiction() {
    ApplicationActor actor =
        new ApplicationActor(
            "subject-1", "intake-jkt", Set.of("CASE_INTAKE_OFFICER"), Set.of("JKT"));

    assertDoesNotThrow(
        () ->
            authorizationService.requirePermission(
                actor, Permission.CREATE_REPORT, new AuthorizationContext("JKT", "REPORT", null)));
  }

  @Test
  void rejectsActorWithoutRequiredRole() {
    ApplicationActor actor =
        new ApplicationActor(
            "subject-2", "investigator-jkt", Set.of("INVESTIGATOR"), Set.of("JKT"));

    assertThrows(
        AuthorizationDeniedException.class,
        () ->
            authorizationService.requirePermission(
                actor, Permission.CREATE_REPORT, new AuthorizationContext("JKT", "REPORT", null)));
  }

  @Test
  void rejectsActorWithoutJurisdictionAccess() {
    ApplicationActor actor =
        new ApplicationActor(
            "subject-3", "intake-bdg", Set.of("CASE_INTAKE_OFFICER"), Set.of("BDG"));

    assertThrows(
        AuthorizationDeniedException.class,
        () ->
            authorizationService.requirePermission(
                actor, Permission.CREATE_REPORT, new AuthorizationContext("JKT", "REPORT", null)));
  }
}
