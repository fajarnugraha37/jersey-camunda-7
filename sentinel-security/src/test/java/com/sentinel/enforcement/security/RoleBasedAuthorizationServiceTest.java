package com.sentinel.enforcement.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationDeniedException;
import com.sentinel.enforcement.application.security.CaseAuthorizationScope;
import com.sentinel.enforcement.application.security.Permission;
import com.sentinel.enforcement.domain.casefile.CaseClassification;
import java.util.Set;
import java.util.UUID;
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
            "subject-4",
            "investigator-jkt",
            Set.of("INVESTIGATOR"),
            Set.of("JKT"),
            Set.of("JKT-UNIT-1"),
            Set.of(CaseClassification.CONFIDENTIAL),
            Set.of());

    assertDoesNotThrow(
        () ->
            authorizationService.requirePermission(
                actor,
                Permission.READ_CASE,
                new AuthorizationContext(
                    "JKT",
                    "CASE",
                    "case-1",
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    "investigator-jkt",
                    "JKT-UNIT-1",
                    CaseClassification.CONFIDENTIAL,
                    "triage-jkt",
                    CaseAuthorizationScope.RESTRICTED_TO_ASSIGNED_UNITS_WHEN_PRESENT)));
  }

  @Test
  void rejectsInvestigatorWithoutDirectAssignment() {
    ApplicationActor actor =
        new ApplicationActor(
            "subject-5",
            "investigator-jkt",
            Set.of("INVESTIGATOR"),
            Set.of("JKT"),
            Set.of("JKT-UNIT-1"),
            Set.of(CaseClassification.CONFIDENTIAL),
            Set.of());

    assertThrows(
        AuthorizationDeniedException.class,
        () ->
            authorizationService.requirePermission(
                actor,
                Permission.READ_CASE,
                new AuthorizationContext(
                    "JKT",
                    "CASE",
                    "case-1",
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    "other-investigator",
                    "JKT-UNIT-1",
                    CaseClassification.CONFIDENTIAL,
                    "triage-jkt",
                    CaseAuthorizationScope.RESTRICTED_TO_ASSIGNED_UNITS_WHEN_PRESENT)));
  }

  @Test
  void rejectsInvestigatorWithoutDirectAssignmentForEvidenceRead() {
    ApplicationActor actor =
        new ApplicationActor(
            "subject-6",
            "investigator-jkt",
            Set.of("INVESTIGATOR"),
            Set.of("JKT"),
            Set.of("JKT-UNIT-1"),
            Set.of(CaseClassification.CONFIDENTIAL),
            Set.of());

    assertThrows(
        AuthorizationDeniedException.class,
        () ->
            authorizationService.requirePermission(
                actor,
                Permission.READ_EVIDENCE,
                new AuthorizationContext(
                    "JKT",
                    "CASE",
                    "case-1",
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    "other-investigator",
                    "JKT-UNIT-1",
                    CaseClassification.CONFIDENTIAL,
                    "triage-jkt",
                    CaseAuthorizationScope.RESTRICTED_TO_ASSIGNED_UNITS_WHEN_PRESENT)));
  }

  @Test
  void allowsTriageOfficerToTriageReportWithinJurisdiction() {
    ApplicationActor actor =
        new ApplicationActor("subject-7", "triage-jkt", Set.of("TRIAGE_OFFICER"), Set.of("JKT"));

    assertDoesNotThrow(
        () ->
            authorizationService.requirePermission(
                actor,
                Permission.TRIAGE_REPORT,
                new AuthorizationContext("JKT", "REPORT", "report-1", null)));
  }

  @Test
  void rejectsSupervisorWithWrongAssignedUnit() {
    ApplicationActor actor =
        new ApplicationActor(
            "subject-8",
            "supervisor-jkt-unit-2",
            Set.of("SUPERVISOR"),
            Set.of("JKT"),
            Set.of("JKT-UNIT-2"),
            Set.of(CaseClassification.CONFIDENTIAL),
            Set.of());

    assertThrows(
        AuthorizationDeniedException.class,
        () ->
            authorizationService.requirePermission(
                actor,
                Permission.READ_CASE,
                new AuthorizationContext(
                    "JKT",
                    "CASE",
                    "case-2",
                    UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    "investigator-jkt",
                    "JKT-UNIT-1",
                    CaseClassification.CONFIDENTIAL,
                    "triage-jkt",
                    CaseAuthorizationScope.RESTRICTED_TO_ASSIGNED_UNITS_WHEN_PRESENT)));
  }

  @Test
  void rejectsActorWithoutClassificationClearance() {
    ApplicationActor actor =
        new ApplicationActor(
            "subject-9",
            "reviewer-jkt-public",
            Set.of("CASE_REVIEWER"),
            Set.of("JKT"),
            Set.of("JKT-UNIT-1"),
            Set.of(CaseClassification.PUBLIC),
            Set.of());

    assertThrows(
        AuthorizationDeniedException.class,
        () ->
            authorizationService.requirePermission(
                actor,
                Permission.READ_CASE,
                new AuthorizationContext(
                    "JKT",
                    "CASE",
                    "case-3",
                    UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    "investigator-jkt",
                    "JKT-UNIT-1",
                    CaseClassification.SECRET,
                    "triage-jkt",
                    CaseAuthorizationScope.RESTRICTED_TO_ASSIGNED_UNITS_WHEN_PRESENT)));
  }

  @Test
  void rejectsActorWithConflictOfInterestAgainstResourceOwner() {
    ApplicationActor actor =
        new ApplicationActor(
            "subject-10",
            "reviewer-jkt-conflicted",
            Set.of("CASE_REVIEWER"),
            Set.of("JKT"),
            Set.of("JKT-UNIT-1"),
            Set.of(
                CaseClassification.PUBLIC,
                CaseClassification.CONFIDENTIAL,
                CaseClassification.SECRET),
            Set.of("investigator-jkt"));

    assertThrows(
        AuthorizationDeniedException.class,
        () ->
            authorizationService.requirePermission(
                actor,
                Permission.REVIEW_RECOMMENDATION,
                new AuthorizationContext(
                    "JKT",
                    "RECOMMENDATION",
                    "rec-1",
                    UUID.fromString("44444444-4444-4444-4444-444444444444"),
                    "investigator-jkt",
                    "JKT-UNIT-1",
                    CaseClassification.CONFIDENTIAL,
                    "investigator-jkt",
                    CaseAuthorizationScope.RESTRICTED_TO_ASSIGNED_UNITS_WHEN_PRESENT)));
  }
}
