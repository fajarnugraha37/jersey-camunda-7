package com.sentinel.enforcement.application.report;

public record CreateReportCommand(
    String title,
    String description,
    String jurisdictionCode,
    String reporterName,
    String actorId) {}
