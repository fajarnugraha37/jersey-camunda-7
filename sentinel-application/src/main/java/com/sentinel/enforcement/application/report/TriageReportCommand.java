package com.sentinel.enforcement.application.report;

public record TriageReportCommand(
    long expectedVersion, String reason, String correlationId, String sourceIp) {}
