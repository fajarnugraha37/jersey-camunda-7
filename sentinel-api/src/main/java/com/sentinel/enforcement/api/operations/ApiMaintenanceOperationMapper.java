package com.sentinel.enforcement.api.operations;

import com.sentinel.enforcement.api.generated.model.MaintenanceOperationResultStatusValue;
import com.sentinel.enforcement.api.generated.model.MaintenanceOperationRunResponse;
import com.sentinel.enforcement.api.generated.model.RecalculateOverdueSanctionObligationsRequest;
import com.sentinel.enforcement.application.operations.MaintenanceOperationRunView;
import com.sentinel.enforcement.application.operations.RecalculateOverdueSanctionObligationsCommand;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class ApiMaintenanceOperationMapper {
  public static final ApiMaintenanceOperationMapper INSTANCE = new ApiMaintenanceOperationMapper();

  private ApiMaintenanceOperationMapper() {}

  public RecalculateOverdueSanctionObligationsCommand toCommand(
      RecalculateOverdueSanctionObligationsRequest request, String correlationId, String sourceIp) {
    return new RecalculateOverdueSanctionObligationsCommand(
        request.getEffectiveDate(), correlationId, sourceIp);
  }

  public MaintenanceOperationRunResponse toResponse(MaintenanceOperationRunView runView) {
    return new MaintenanceOperationRunResponse()
        .runId(runView.runId())
        .operationName(runView.operationName())
        .requestedBy(runView.requestedBy())
        .requestedAt(OffsetDateTime.ofInstant(runView.requestedAt(), ZoneOffset.UTC))
        .completedAt(
            runView.completedAt() == null
                ? null
                : OffsetDateTime.ofInstant(runView.completedAt(), ZoneOffset.UTC))
        .effectiveDate(runView.effectiveDate())
        .resultStatus(MaintenanceOperationResultStatusValue.fromValue(runView.resultStatus()))
        .affectedRows(runView.affectedRows());
  }
}
