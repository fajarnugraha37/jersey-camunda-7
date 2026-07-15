package com.sentinel.enforcement.api.appeal;

import com.sentinel.enforcement.api.generated.model.AppealDecisionOutcomeValue;
import com.sentinel.enforcement.api.generated.model.AppealResponse;
import com.sentinel.enforcement.api.generated.model.AppealStatusValue;
import com.sentinel.enforcement.api.generated.model.DecideAppealRequest;
import com.sentinel.enforcement.application.appeal.DecideAppealCommand;
import com.sentinel.enforcement.domain.appeal.Appeal;
import com.sentinel.enforcement.domain.appeal.AppealDecisionOutcome;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class ApiAppealMapper {
  public static final ApiAppealMapper INSTANCE = new ApiAppealMapper();

  private ApiAppealMapper() {}

  public DecideAppealCommand toDecideCommand(
      DecideAppealRequest request, String correlationId, String sourceIp) {
    return new DecideAppealCommand(
        toOutcome(request.getOutcome()), request.getSummary(), correlationId, sourceIp);
  }

  public AppealResponse toResponse(Appeal appeal) {
    return new AppealResponse()
        .id(appeal.id())
        .caseId(appeal.caseId())
        .decisionId(appeal.decisionId())
        .rationale(appeal.rationale())
        .supervisorOverride(appeal.supervisorOverride())
        .supervisorOverrideReason(appeal.supervisorOverrideReason())
        .status(AppealStatusValue.fromValue(appeal.status().name()))
        .submittedAt(toOffsetDateTime(appeal.submittedAt()))
        .submittedBy(appeal.submittedBy())
        .decidedByAppealDecisionId(appeal.decidedByAppealDecisionId())
        .createdAt(toOffsetDateTime(appeal.createdAt()))
        .createdBy(appeal.createdBy())
        .updatedAt(toOffsetDateTime(appeal.updatedAt()))
        .updatedBy(appeal.updatedBy())
        .version(appeal.version());
  }

  public AppealDecisionOutcome toOutcome(AppealDecisionOutcomeValue value) {
    return AppealDecisionOutcome.valueOf(value.toString());
  }

  public OffsetDateTime toOffsetDateTime(java.time.Instant instant) {
    return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
