package com.sentinel.enforcement.application.operations;

import com.sentinel.enforcement.application.messaging.ApplicationTransactionManager;
import com.sentinel.enforcement.application.messaging.TransactionIsolation;
import com.sentinel.enforcement.application.messaging.TransactionOptions;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.Permission;
import java.util.UUID;

public final class MaintenanceOperationApplicationService {
  private static final String RESOURCE_TYPE = "MAINTENANCE_OPERATION";
  private static final String RECALCULATE_OVERDUE_OPERATION =
      "sanction-obligations/recalculate-overdue";

  private final AuthorizationService authorizationService;
  private final ApplicationTransactionManager transactionManager;
  private final MaintenanceOperationRepository maintenanceOperationRepository;

  public MaintenanceOperationApplicationService(
      AuthorizationService authorizationService,
      ApplicationTransactionManager transactionManager,
      MaintenanceOperationRepository maintenanceOperationRepository) {
    this.authorizationService = authorizationService;
    this.transactionManager = transactionManager;
    this.maintenanceOperationRepository = maintenanceOperationRepository;
  }

  public MaintenanceOperationRunView recalculateOverdueSanctionObligations(
      ApplicationActor actor, RecalculateOverdueSanctionObligationsCommand command) {
    authorizationService.requirePermission(
        actor,
        Permission.RUN_MAINTENANCE_OPERATION,
        new AuthorizationContext(null, RESOURCE_TYPE, RECALCULATE_OVERDUE_OPERATION, null));
    UUID runId = UUID.randomUUID();
    return transactionManager.required(
        TransactionOptions.write(
            TransactionIsolation.REPEATABLE_READ, "recalculate-overdue-sanction-obligations"),
        () -> {
          maintenanceOperationRepository.lockSanctionObligationTable();
          maintenanceOperationRepository.recalculateOverdueSanctionObligations(
              runId, command.effectiveDate(), actor.username());
          return maintenanceOperationRepository
              .findRunById(runId)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Maintenance run " + runId + " was not persisted after recalculation."));
        });
  }
}
