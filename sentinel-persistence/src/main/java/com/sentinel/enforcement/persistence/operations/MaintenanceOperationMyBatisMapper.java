package com.sentinel.enforcement.persistence.operations;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MaintenanceOperationMyBatisMapper {

  @Update("LOCK TABLE sanction_obligation IN SHARE ROW EXCLUSIVE MODE NOWAIT")
  int lockSanctionObligationTable();

  @Update(
      """
      CALL recalculate_overdue_sanction_obligations(
          #{effectiveDate},
          #{requestedBy},
          #{runId}
      )
      """)
  int recalculateOverdueSanctionObligations(MaintenanceOperationCallData callData);

  @Select(
      """
      SELECT
          id AS runId,
          operation_name AS operationName,
          requested_by AS requestedBy,
          requested_at AS requestedAt,
          completed_at AS completedAt,
          effective_date AS effectiveDate,
          result_status AS resultStatus,
          affected_rows AS affectedRows
      FROM maintenance_operation_run
      WHERE id = #{runId}
      """)
  MaintenanceOperationRunData findRunById(UUID runId);
}
