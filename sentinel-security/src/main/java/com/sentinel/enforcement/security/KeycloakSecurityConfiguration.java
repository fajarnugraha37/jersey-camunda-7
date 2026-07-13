package com.sentinel.enforcement.security;

import java.net.URI;

public record KeycloakSecurityConfiguration(URI issuer, String audience, URI jwksUri) {}
