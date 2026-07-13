package com.sentinel.enforcement.api.error;

import java.util.List;

public record ErrorResponse(
    String type,
    String title,
    int status,
    String code,
    String detail,
    String instance,
    String correlationId,
    List<ViolationResponse> violations) {}
