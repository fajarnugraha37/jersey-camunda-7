package com.sentinel.enforcement.persistence.casefile;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CaseMyBatisMapper {

  @Select("SELECT generate_case_number(#{jurisdictionCode}, 'ENF', #{year})")
  String nextCaseNumber(
      @Param("jurisdictionCode") String jurisdictionCode, @Param("year") int year);

  @Insert(
      """
            INSERT INTO case_record (
                id,
                case_number,
                report_id,
                title,
                summary,
                jurisdiction_code,
                status,
                assigned_unit_id,
                assignee_user_id,
                created_at,
                created_by,
                updated_at,
                updated_by,
                version
            ) VALUES (
                #{id},
                #{caseNumber},
                #{reportId},
                #{title},
                #{summary},
                #{jurisdictionCode},
                #{status},
                #{assignedUnitId},
                #{assigneeUserId},
                #{createdAt},
                #{createdBy},
                #{updatedAt},
                #{updatedBy},
                #{version}
            )
            """)
  int insertCase(CaseRecordData caseRecord);

  @Update(
      """
            UPDATE case_record
            SET
                status = #{caseRecord.status},
                assigned_unit_id = #{caseRecord.assignedUnitId},
                assignee_user_id = #{caseRecord.assigneeUserId},
                updated_at = #{caseRecord.updatedAt},
                updated_by = #{caseRecord.updatedBy},
                version = #{caseRecord.version}
            WHERE id = #{caseRecord.id}
              AND version = #{expectedVersion}
            """)
  int updateCase(
      @Param("caseRecord") CaseRecordData caseRecord,
      @Param("expectedVersion") long expectedVersion);

  @Select(
      """
            SELECT
                id,
                case_number AS caseNumber,
                report_id AS reportId,
                title,
                summary,
                jurisdiction_code AS jurisdictionCode,
                status,
                assigned_unit_id AS assignedUnitId,
                assignee_user_id AS assigneeUserId,
                created_at AS createdAt,
                created_by AS createdBy,
                updated_at AS updatedAt,
                updated_by AS updatedBy,
                version
            FROM case_record
            WHERE id = #{id}
            """)
  CaseRecordData findCaseById(UUID id);

  @Select({
    "<script>",
    "SELECT",
    "  id,",
    "  case_number AS caseNumber,",
    "  report_id AS reportId,",
    "  title,",
    "  summary,",
    "  jurisdiction_code AS jurisdictionCode,",
    "  status,",
    "  assigned_unit_id AS assignedUnitId,",
    "  assignee_user_id AS assigneeUserId,",
    "  created_at AS createdAt,",
    "  created_by AS createdBy,",
    "  updated_at AS updatedAt,",
    "  updated_by AS updatedBy,",
    "  version",
    "FROM case_record",
    "WHERE jurisdiction_code IN",
    "<foreach collection='jurisdictionCodes' item='jurisdictionCode' open='(' separator=',' close=')'>",
    "  #{jurisdictionCode}",
    "</foreach>",
    "<if test='assigneeUserId != null'>",
    "  AND assignee_user_id = #{assigneeUserId}",
    "</if>",
    "<if test='cursorCreatedAt != null'>",
    "  AND (created_at &lt; #{cursorCreatedAt}",
    "       OR (created_at = #{cursorCreatedAt} AND id &lt; #{cursorId}))",
    "</if>",
    "ORDER BY created_at DESC, id DESC",
    "LIMIT #{limitPlusOne}",
    "</script>"
  })
  List<CaseRecordData> findCasePage(CasePageQueryData pageQueryData);

  @Insert(
      """
            INSERT INTO case_assignment (
                id,
                case_id,
                assigned_unit_id,
                assignee_user_id,
                assignment_reason,
                assigned_at,
                assigned_by,
                created_at,
                created_by,
                updated_at,
                updated_by,
                version
            ) VALUES (
                #{id},
                #{caseId},
                #{assignedUnitId},
                #{assigneeUserId},
                #{assignmentReason},
                #{assignedAt},
                #{assignedBy},
                #{createdAt},
                #{createdBy},
                #{updatedAt},
                #{updatedBy},
                #{version}
            )
            """)
  int insertAssignment(CaseAssignmentData caseAssignment);

  @Insert(
      """
            INSERT INTO case_status_history (
                id,
                case_id,
                from_status,
                to_status,
                transition_reason,
                transitioned_at,
                transitioned_by,
                created_at,
                created_by
            ) VALUES (
                #{id},
                #{caseId},
                #{fromStatus},
                #{toStatus},
                #{transitionReason},
                #{transitionedAt},
                #{transitionedBy},
                #{createdAt},
                #{createdBy}
            )
            """)
  int insertStatusHistory(CaseStatusHistoryData statusHistoryData);

  @Insert(
      """
            INSERT INTO audit_event (
                event_id,
                event_type,
                actor_type,
                actor_id,
                actor_roles,
                action,
                resource_type,
                resource_id,
                case_id,
                timestamp,
                correlation_id,
                source_ip,
                result,
                reason,
                before_summary,
                after_summary,
                metadata
            ) VALUES (
                #{eventId},
                #{eventType},
                #{actorType},
                #{actorId},
                #{actorRoles},
                #{action},
                #{resourceType},
                #{resourceId},
                #{caseId},
                #{timestamp},
                #{correlationId},
                #{sourceIp},
                #{result},
                #{reason},
                #{beforeSummary},
                #{afterSummary},
                #{metadata}
            )
            """)
  int insertAuditEvent(AuditEventData auditEventData);

  @Select(
      """
            SELECT
                event_id AS eventId,
                event_type AS eventType,
                actor_type AS actorType,
                actor_id AS actorId,
                actor_roles AS actorRoles,
                action,
                resource_type AS resourceType,
                resource_id AS resourceId,
                case_id AS caseId,
                timestamp,
                correlation_id AS correlationId,
                source_ip AS sourceIp,
                result,
                reason,
                before_summary AS beforeSummary,
                after_summary AS afterSummary,
                metadata
            FROM audit_event
            WHERE case_id = #{caseId}
            ORDER BY timestamp DESC, event_id DESC
            LIMIT #{limit}
            """)
  List<AuditEventData> findAuditEvents(@Param("caseId") UUID caseId, @Param("limit") int limit);
}
