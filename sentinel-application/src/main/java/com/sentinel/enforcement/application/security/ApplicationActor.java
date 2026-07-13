package com.sentinel.enforcement.application.security;

import java.util.Set;

public record ApplicationActor(
    String subject, String username, Set<String> roles, Set<String> jurisdictions) {

  public ApplicationActor {
    roles = Set.copyOf(roles);
    jurisdictions = Set.copyOf(jurisdictions);
  }

  public boolean hasRole(String role) {
    return roles.contains(role);
  }

  public boolean hasJurisdiction(String jurisdictionCode) {
    return jurisdictions.contains(jurisdictionCode);
  }
}
