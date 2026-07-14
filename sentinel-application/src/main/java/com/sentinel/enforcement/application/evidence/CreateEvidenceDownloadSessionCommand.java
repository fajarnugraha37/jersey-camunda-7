package com.sentinel.enforcement.application.evidence;

public record CreateEvidenceDownloadSessionCommand(
    String reason, String correlationId, String sourceIp) {}
