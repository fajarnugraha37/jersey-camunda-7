package com.sentinel.enforcement.application.operations;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceOperationRepository {

  void lockSanctionObligationTable();

  void recalculateOverdueSanctionObligations(
      UUID runId, LocalDate effectiveDate, String requestedBy);

  Optional<MaintenanceOperationRunView> findRunById(UUID runId);
}
