package com.sentinel.enforcement.application.evidence;

import java.util.UUID;

public record FinalizeEvidenceVersionCommand(
    UUID uploadSessionId, String correlationId, String sourceIp) {}
