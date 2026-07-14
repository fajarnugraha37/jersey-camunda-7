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
                actor,
                Permission.CREATE_REPORT,
                new AuthorizationContext("JKT", "REPORT", null, null)));
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
                actor,
                Permission.CREATE_REPORT,
                new AuthorizationContext("JKT", "REPORT", null, null)));
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
                actor,
                Permission.CREATE_REPORT,
                new AuthorizationContext("JKT", "REPORT", null, null)));
  }

  @Test
  void allowsAssignedInvestigatorToReadCase() {
    ApplicationActor actor =
        new ApplicationActor(
            "subject-4", "investigator-jkt", Set.of("INVESTIGATOR"), Set.of("JKT"));

    assertDoesNotThrow(
        () ->
            authorizationService.requirePermission(
                actor,
                Permission.READ_CASE,
                new AuthorizationContext("JKT", "CASE", "case-1", "investigator-jkt")));
  }

  @Test
  void rejectsInvestigatorWithoutDirectAssignment() {
    ApplicationActor actor =
        new ApplicationActor(
            "subject-5", "investigator-jkt", Set.of("INVESTIGATOR"), Set.of("JKT"));

    assertThrows(
        AuthorizationDeniedException.class,
        () ->
            authorizationService.requirePermission(
                actor,
                Permission.READ_CASE,
                new AuthorizationContext("JKT", "CASE", "case-1", "other-investigator")));
  }

  @Test
  void rejectsInvestigatorWithoutDirectAssignmentForEvidenceRead() {
    ApplicationActor actor =
        new ApplicationActor(
            "subject-6", "investigator-jkt", Set.of("INVESTIGATOR"), Set.of("JKT"));

    assertThrows(
        AuthorizationDeniedException.class,
        () ->
            authorizationService.requirePermission(
                actor,
                Permission.READ_EVIDENCE,
                new AuthorizationContext("JKT", "CASE", "case-1", "other-investigator")));
  }

  @Test
  void allowsTriageOfficerToTriageReportWithinJurisdiction() {
    ApplicationActor actor =
        new ApplicationActor(
            "subject-7", "triage-jkt", Set.of("TRIAGE_OFFICER"), Set.of("JKT"));

    assertDoesNotThrow(
        () ->
            authorizationService.requirePermission(
                actor,
                Permission.TRIAGE_REPORT,
                new AuthorizationContext("JKT", "REPORT", "report-1", null)));
  }
}
