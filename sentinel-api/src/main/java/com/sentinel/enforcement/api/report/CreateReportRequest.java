package com.sentinel.enforcement.api.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 4000) String description,
    @NotBlank @Size(max = 16) String jurisdictionCode,
    @NotBlank @Size(max = 100) String reporterName) {}
