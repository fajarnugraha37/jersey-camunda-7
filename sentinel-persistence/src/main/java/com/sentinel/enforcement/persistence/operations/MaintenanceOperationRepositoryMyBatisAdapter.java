package com.sentinel.enforcement.persistence.operations;

import com.sentinel.enforcement.application.operations.MaintenanceOperationConflictException;
import com.sentinel.enforcement.application.operations.MaintenanceOperationRepository;
import com.sentinel.enforcement.application.operations.MaintenanceOperationRunView;
import com.sentinel.enforcement.persistence.MyBatisRepositorySupport;
import com.sentinel.enforcement.persistence.PersistenceExceptionClassifier;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;

public final class MaintenanceOperationRepositoryMyBatisAdapter extends MyBatisRepositorySupport
    implements MaintenanceOperationRepository {

  public MaintenanceOperationRepositoryMyBatisAdapter(SqlSessionFactory sqlSessionFactory) {
    super(sqlSessionFactory);
  }

  @Override
  public void lockSanctionObligationTable() {
    try {
      executeBoundSession(
          session -> {
            session
                .getMapper(MaintenanceOperationMyBatisMapper.class)
                .lockSanctionObligationTable();
            return null;
          });
    } catch (RuntimeException exception) {
      if (PersistenceExceptionClassifier.isLockNotAvailable(exception)) {
        throw new MaintenanceOperationConflictException(
            "MAINTENANCE_OPERATION_LOCKED",
            "Sanction obligation recalculation is already running in another transaction.");
      }
      throw exception;
    }
  }

  @Override
  public void recalculateOverdueSanctionObligations(
      UUID runId, LocalDate effectiveDate, String requestedBy) {
    executeBoundSession(
        session -> {
          session
              .getMapper(MaintenanceOperationMyBatisMapper.class)
              .recalculateOverdueSanctionObligations(
                  new MaintenanceOperationCallData(runId, effectiveDate, requestedBy));
          return null;
        });
  }

  @Override
  public Optional<MaintenanceOperationRunView> findRunById(UUID runId) {
    return executeRead(
        session ->
            Optional.ofNullable(
                    session.getMapper(MaintenanceOperationMyBatisMapper.class).findRunById(runId))
                .map(this::toView));
  }

  private MaintenanceOperationRunView toView(MaintenanceOperationRunData runData) {
    return new MaintenanceOperationRunView(
        runData.runId(),
        runData.operationName(),
        runData.requestedBy(),
        runData.requestedAt().toInstant(),
        runData.completedAt() == null ? null : runData.completedAt().toInstant(),
        runData.effectiveDate(),
        runData.resultStatus(),
        runData.affectedRows());
  }
}
