package com.sentinel.enforcement.application.security;

public record AuthorizationContext(
    String jurisdictionCode, String resourceType, String resourceId, String assigneeUserId) {}
