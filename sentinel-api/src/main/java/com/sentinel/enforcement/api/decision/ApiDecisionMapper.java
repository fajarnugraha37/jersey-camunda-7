package com.sentinel.enforcement.api.decision;

import com.sentinel.enforcement.api.generated.model.CreateDecisionRequest;
import com.sentinel.enforcement.api.generated.model.DecisionResponse;
import com.sentinel.enforcement.api.generated.model.DecisionStatusValue;
import com.sentinel.enforcement.application.decision.CreateDecisionCommand;
import com.sentinel.enforcement.domain.decision.Decision;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class ApiDecisionMapper {
  public static final ApiDecisionMapper INSTANCE = new ApiDecisionMapper();

  private ApiDecisionMapper() {}

  public CreateDecisionCommand toCreateCommand(
      CreateDecisionRequest request, String correlationId, String sourceIp) {
    if (request == null && correlationId == null && sourceIp == null) {
      return null;
    }
    return new CreateDecisionCommand(
        request == null ? null : request.getTitle(),
        request == null ? null : request.getSummary(),
        request != null && Boolean.TRUE.equals(request.getViolationProven()),
        request == null ? null : request.getSanctionSummary(),
        request == null ? null : request.getObligationTitle(),
        request == null ? null : request.getObligationDetails(),
        request == null ? null : request.getObligationDueDate(),
        request == null ? null : request.getAppealDeadline(),
        correlationId,
        sourceIp);
  }

  public DecisionResponse toResponse(Decision decision) {
    return new DecisionResponse()
        .id(decision.id())
        .caseId(decision.caseId())
        .recommendationId(decision.recommendationId())
        .title(decision.title())
        .summary(decision.summary())
        .violationProven(decision.violationProven())
        .sanctionSummary(decision.sanctionSummary())
        .obligationTitle(decision.obligationTitle())
        .obligationDetails(decision.obligationDetails())
        .obligationDueDate(decision.obligationDueDate())
        .appealDeadline(decision.appealDeadline())
        .status(DecisionStatusValue.fromValue(decision.status().name()))
        .approvedAt(toOffsetDateTime(decision.approvedAt()))
        .approvedBy(decision.approvedBy())
        .publishedAt(toOffsetDateTime(decision.publishedAt()))
        .publishedBy(decision.publishedBy())
        .createdAt(toOffsetDateTime(decision.createdAt()))
        .createdBy(decision.createdBy())
        .updatedAt(toOffsetDateTime(decision.updatedAt()))
        .updatedBy(decision.updatedBy())
        .version(decision.version());
  }

  private OffsetDateTime toOffsetDateTime(Instant instant) {
    return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
