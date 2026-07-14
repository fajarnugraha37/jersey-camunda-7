package com.sentinel.enforcement.persistence.workflow;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WorkflowInstanceMyBatisMapper {

  @Insert(
      """
      INSERT INTO workflow_instance (
          case_id,
          process_instance_id,
          process_definition_id,
          process_definition_version,
          business_key,
          status,
          created_at,
          updated_at
      ) VALUES (
          #{caseId},
          #{processInstanceId},
          #{processDefinitionId},
          #{processDefinitionVersion},
          #{businessKey},
          'ACTIVE',
          #{createdAt},
          #{updatedAt}
      )
      ON CONFLICT (case_id) DO UPDATE
      SET process_instance_id = EXCLUDED.process_instance_id,
          process_definition_id = EXCLUDED.process_definition_id,
          process_definition_version = EXCLUDED.process_definition_version,
          business_key = EXCLUDED.business_key,
          status = 'ACTIVE',
          updated_at = EXCLUDED.updated_at
      """)
  int upsertStartedWorkflow(WorkflowInstanceData workflowInstanceData);

  @Select(
      """
      SELECT
          case_id AS caseId,
          process_instance_id AS processInstanceId,
          process_definition_id AS processDefinitionId,
          process_definition_version AS processDefinitionVersion,
          business_key AS businessKey,
          status,
          created_at AS createdAt,
          updated_at AS updatedAt
      FROM workflow_instance
      WHERE case_id = #{caseId}
      """)
  WorkflowInstanceData findByCaseId(UUID caseId);

  @Update(
      """
      UPDATE workflow_instance
      SET status = 'COMPLETED',
          updated_at = #{updatedAt}
      WHERE process_instance_id = #{processInstanceId}
      """)
  int markCompleted(
      @Param("processInstanceId") String processInstanceId,
      @Param("updatedAt") OffsetDateTime updatedAt);

  @Update(
      """
      UPDATE workflow_instance
      SET status = 'CANCELLED',
          updated_at = #{updatedAt}
      WHERE case_id = #{caseId}
      """)
  int markCancelled(@Param("caseId") UUID caseId, @Param("updatedAt") OffsetDateTime updatedAt);
}
