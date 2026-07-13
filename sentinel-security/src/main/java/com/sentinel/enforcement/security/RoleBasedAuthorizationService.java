package com.sentinel.enforcement.security;

import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationDeniedException;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.Permission;
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
  }

  private Set<String> requiredRoles(Permission permission) {
    return switch (permission) {
      case CREATE_REPORT -> Set.of("CASE_INTAKE_OFFICER");
      case READ_REPORT -> Set.of("CASE_INTAKE_OFFICER", "TRIAGE_OFFICER", "AUDITOR");
    };
  }
}
