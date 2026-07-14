package com.sentinel.enforcement.application.evidence;

import java.time.Instant;
import java.util.UUID;

public record PreparedEvidenceUploadSession(
    UUID evidenceId,
    UUID uploadSessionId,
    int targetVersionNumber,
    String uploadUrl,
    Instant expiresAt,
    String objectKey) {}
