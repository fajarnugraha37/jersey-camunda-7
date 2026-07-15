package com.sentinel.enforcement.security;

import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationDeniedException;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.Permission;
import java.util.Objects;
import java.util.Set;

public final class RoleBasedAuthorizationService implements AuthorizationService {
  private static final String SYSTEM_ADMIN_ROLE = "SYSTEM_ADMIN";

  @Override
  public void requirePermission(
      ApplicationActor actor, Permission permission, AuthorizationContext authorizationContext) {
    if (actor.hasRole(SYSTEM_ADMIN_ROLE)) {
      return;
    }
    if (requiredRoles(permission).stream().noneMatch(actor::hasRole)) {
      throw new AuthorizationDeniedException(
          "Actor does not have permission "
              + permission
              + " for "
              + authorizationContext.resourceType()
              + ".");
    }

    String jurisdictionCode = authorizationContext.jurisdictionCode();
    if (jurisdictionCode != null && !actor.hasJurisdiction(jurisdictionCode)) {
      throw new AuthorizationDeniedException(
          "Actor does not have jurisdiction access for " + jurisdictionCode + ".");
    }

    if (requiresDirectAssignment(actor, permission)
        && !Objects.equals(actor.username(), authorizationContext.assigneeUserId())) {
      throw new AuthorizationDeniedException(
          "Actor does not have direct assignment access for "
              + authorizationContext.resourceType()
              + " "
              + authorizationContext.resourceId()
              + ".");
    }
  }

  private Set<String> requiredRoles(Permission permission) {
    return switch (permission) {
      case CREATE_REPORT -> Set.of("CASE_INTAKE_OFFICER");
      case READ_REPORT -> Set.of("CASE_INTAKE_OFFICER", "TRIAGE_OFFICER", "AUDITOR");
      case TRIAGE_REPORT -> Set.of("TRIAGE_OFFICER", "SUPERVISOR");
      case CREATE_CASE -> Set.of("TRIAGE_OFFICER", "SUPERVISOR");
      case READ_CASE, LIST_CASES ->
          Set.of(
              "TRIAGE_OFFICER",
              "INVESTIGATOR",
              "CASE_REVIEWER",
              "DECISION_MAKER",
              "APPEAL_OFFICER",
              "SUPERVISOR",
              "AUDITOR");
      case CREATE_EVIDENCE_UPLOAD_SESSION,
              FINALIZE_EVIDENCE,
              READ_EVIDENCE,
              CREATE_EVIDENCE_DOWNLOAD_SESSION ->
          Set.of(
              "TRIAGE_OFFICER",
              "INVESTIGATOR",
              "CASE_REVIEWER",
              "DECISION_MAKER",
              "APPEAL_OFFICER",
               "SUPERVISOR",
               "AUDITOR");
      case CREATE_RECOMMENDATION, SUBMIT_RECOMMENDATION ->
          Set.of("INVESTIGATOR", "SUPERVISOR");
      case REVIEW_RECOMMENDATION -> Set.of("CASE_REVIEWER", "SUPERVISOR");
      case CREATE_DECISION, APPROVE_DECISION, PUBLISH_DECISION ->
          Set.of("DECISION_MAKER", "SUPERVISOR");
      case CREATE_APPEAL, DECIDE_APPEAL -> Set.of("APPEAL_OFFICER", "SUPERVISOR");
      case ASSIGN_CASE -> Set.of("TRIAGE_OFFICER", "SUPERVISOR");
      case TRANSITION_CASE ->
          Set.of(
              "TRIAGE_OFFICER",
              "INVESTIGATOR",
              "CASE_REVIEWER",
              "DECISION_MAKER",
              "APPEAL_OFFICER",
              "SUPERVISOR");
      case READ_CASE_AUDIT -> Set.of("SUPERVISOR", "AUDITOR");
      case LIST_TASKS, CLAIM_TASK, COMPLETE_TASK ->
          Set.of(
              "TRIAGE_OFFICER",
              "INVESTIGATOR",
              "CASE_REVIEWER",
              "DECISION_MAKER",
              "APPEAL_OFFICER",
              "SUPERVISOR");
      case RECONCILE_WORKFLOW -> Set.of("SUPERVISOR");
    };
  }

  private boolean requiresDirectAssignment(ApplicationActor actor, Permission permission) {
    if (permission != Permission.READ_CASE
        && permission != Permission.TRANSITION_CASE
        && permission != Permission.CREATE_EVIDENCE_UPLOAD_SESSION
        && permission != Permission.FINALIZE_EVIDENCE
        && permission != Permission.READ_EVIDENCE
        && permission != Permission.CREATE_EVIDENCE_DOWNLOAD_SESSION
        && permission != Permission.CREATE_RECOMMENDATION
        && permission != Permission.SUBMIT_RECOMMENDATION) {
      return false;
    }
    if (!actor.hasRole("INVESTIGATOR")) {
      return false;
    }
    return !actor.hasRole("SUPERVISOR")
        && !actor.hasRole("TRIAGE_OFFICER")
        && !actor.hasRole("CASE_REVIEWER")
        && !actor.hasRole("DECISION_MAKER")
        && !actor.hasRole("APPEAL_OFFICER")
        && !actor.hasRole("AUDITOR")
        && !actor.hasRole(SYSTEM_ADMIN_ROLE);
  }
}
