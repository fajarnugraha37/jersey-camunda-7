package com.sentinel.enforcement.application.evidence;

import java.time.Instant;

public record EvidenceDownloadSession(String downloadUrl, Instant expiresAt) {}
