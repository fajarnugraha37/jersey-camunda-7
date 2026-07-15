package com.sentinel.enforcement.persistence.casefile;

import java.util.List;
import java.util.Set;
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
                classification,
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
                #{classification},
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
            <script>
            UPDATE case_record
            <set>
                status = #{caseRecord.status},
                assigned_unit_id = #{caseRecord.assignedUnitId},
                assignee_user_id = #{caseRecord.assigneeUserId},
                updated_at = #{caseRecord.updatedAt},
                updated_by = #{caseRecord.updatedBy},
                version = #{caseRecord.version}
            </set>
            WHERE id = #{caseRecord.id}
              AND version = #{expectedVersion}
            </script>
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
                classification,
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

  @Select(
      """
            <script>
            SELECT
                id,
                case_number AS caseNumber,
                report_id AS reportId,
                title,
                summary,
                jurisdiction_code AS jurisdictionCode,
                classification,
                status,
                assigned_unit_id AS assignedUnitId,
                assignee_user_id AS assigneeUserId,
                created_at AS createdAt,
                created_by AS createdBy,
                updated_at AS updatedAt,
                updated_by AS updatedBy,
                version
            FROM case_record
            WHERE id IN
            <foreach collection="caseIds" item="caseId" open="(" separator="," close=")">
              #{caseId}
            </foreach>
            </script>
            """)
  List<CaseRecordData> findCasesByIds(@Param("caseIds") Set<UUID> caseIds);

  @Select(
      """
            <script>
            SELECT
              id,
              case_number AS caseNumber,
              report_id AS reportId,
              title,
              summary,
              jurisdiction_code AS jurisdictionCode,
              classification,
              status,
              assigned_unit_id AS assignedUnitId,
              assignee_user_id AS assigneeUserId,
              created_at AS createdAt,
              created_by AS createdBy,
              updated_at AS updatedAt,
              updated_by AS updatedBy,
              version
            FROM case_record
            <where>
              jurisdiction_code IN
              <foreach collection="jurisdictionCodes" item="jurisdictionCode" open="(" separator="," close=")">
                #{jurisdictionCode}
              </foreach>
              <if test="restrictedAssigneeUserId != null">
                AND assignee_user_id = #{restrictedAssigneeUserId}
              </if>
              <if test="requestedAssigneeUserId != null">
                AND assignee_user_id = #{requestedAssigneeUserId}
              </if>
              <if test="restrictedAssignedUnitIds != null and !restrictedAssignedUnitIds.isEmpty()">
                AND
                <choose>
                  <when test="includeUnassignedWhenUnitRestricted">
                    (
                      assigned_unit_id IS NULL
                      OR assigned_unit_id IN
                      <foreach collection="restrictedAssignedUnitIds" item="restrictedAssignedUnitId" open="(" separator="," close=")">
                        #{restrictedAssignedUnitId}
                      </foreach>
                    )
                  </when>
                  <otherwise>
                    assigned_unit_id IN
                    <foreach collection="restrictedAssignedUnitIds" item="restrictedAssignedUnitId" open="(" separator="," close=")">
                      #{restrictedAssignedUnitId}
                    </foreach>
                  </otherwise>
                </choose>
              </if>
              AND
              classification IN
              <foreach collection="allowedClassifications" item="allowedClassification" open="(" separator="," close=")">
                #{allowedClassification}
              </foreach>
              <if test="excludedCreatedByUserIds != null and !excludedCreatedByUserIds.isEmpty()">
                AND created_by NOT IN
                <foreach collection="excludedCreatedByUserIds" item="excludedCreatedByUserId" open="(" separator="," close=")">
                  #{excludedCreatedByUserId}
                </foreach>
              </if>
              <if test="status != null">
                AND status = #{status}
              </if>
              <if test="classification != null">
                AND classification = #{classification}
              </if>
              <if test="assignedUnitId != null">
                AND assigned_unit_id = #{assignedUnitId}
              </if>
              <if test="createdBy != null">
                AND created_by = #{createdBy}
              </if>
              <if test="reportId != null">
                AND report_id = #{reportId}
              </if>
              <if test="quickSearchPattern != null">
                AND
                <trim prefix="(" suffix=")" prefixOverrides="OR ">
                  OR LOWER(case_number) LIKE #{quickSearchPattern}
                  OR LOWER(title) LIKE #{quickSearchPattern}
                  OR LOWER(summary) LIKE #{quickSearchPattern}
                  OR LOWER(classification) LIKE #{quickSearchPattern}
                  OR LOWER(COALESCE(assigned_unit_id, '')) LIKE #{quickSearchPattern}
                  OR LOWER(COALESCE(assignee_user_id, '')) LIKE #{quickSearchPattern}
                  OR LOWER(created_by) LIKE #{quickSearchPattern}
                </trim>
              </if>
              <if test="searchPattern != null">
                AND
                <choose>
                  <when test='searchField == "CASE_NUMBER"'>LOWER(case_number) LIKE #{searchPattern}</when>
                  <when test='searchField == "TITLE"'>LOWER(title) LIKE #{searchPattern}</when>
                  <when test='searchField == "SUMMARY"'>LOWER(summary) LIKE #{searchPattern}</when>
                  <when test='searchField == "CLASSIFICATION"'>LOWER(classification) LIKE #{searchPattern}</when>
                  <when test='searchField == "ASSIGNED_UNIT_ID"'>LOWER(COALESCE(assigned_unit_id, '')) LIKE #{searchPattern}</when>
                  <when test='searchField == "ASSIGNEE_USER_ID"'>LOWER(COALESCE(assignee_user_id, '')) LIKE #{searchPattern}</when>
                  <when test='searchField == "CREATED_BY"'>LOWER(created_by) LIKE #{searchPattern}</when>
                  <otherwise>1 = 0</otherwise>
                </choose>
              </if>
              <if test="cursorId != null">
                AND
                <choose>
                  <when test='sortBy == "CREATED_AT" and sortDirection == "DESC"'>(created_at &lt; #{cursorTimestampValue} OR (created_at = #{cursorTimestampValue} AND id &lt; #{cursorId}))</when>
                  <when test='sortBy == "CREATED_AT" and sortDirection == "ASC"'>(created_at &gt; #{cursorTimestampValue} OR (created_at = #{cursorTimestampValue} AND id &gt; #{cursorId}))</when>
                  <when test='sortBy == "UPDATED_AT" and sortDirection == "DESC"'>(updated_at &lt; #{cursorTimestampValue} OR (updated_at = #{cursorTimestampValue} AND id &lt; #{cursorId}))</when>
                  <when test='sortBy == "UPDATED_AT" and sortDirection == "ASC"'>(updated_at &gt; #{cursorTimestampValue} OR (updated_at = #{cursorTimestampValue} AND id &gt; #{cursorId}))</when>
                  <when test='sortBy == "CASE_NUMBER" and sortDirection == "DESC"'>(LOWER(case_number) &lt; #{cursorTextValue} OR (LOWER(case_number) = #{cursorTextValue} AND id &lt; #{cursorId}))</when>
                  <when test='sortBy == "CASE_NUMBER" and sortDirection == "ASC"'>(LOWER(case_number) &gt; #{cursorTextValue} OR (LOWER(case_number) = #{cursorTextValue} AND id &gt; #{cursorId}))</when>
                  <when test='sortBy == "TITLE" and sortDirection == "DESC"'>(LOWER(title) &lt; #{cursorTextValue} OR (LOWER(title) = #{cursorTextValue} AND id &lt; #{cursorId}))</when>
                  <when test='sortBy == "TITLE" and sortDirection == "ASC"'>(LOWER(title) &gt; #{cursorTextValue} OR (LOWER(title) = #{cursorTextValue} AND id &gt; #{cursorId}))</when>
                  <when test='sortBy == "CLASSIFICATION" and sortDirection == "DESC"'>(LOWER(classification) &lt; #{cursorTextValue} OR (LOWER(classification) = #{cursorTextValue} AND id &lt; #{cursorId}))</when>
                  <when test='sortBy == "CLASSIFICATION" and sortDirection == "ASC"'>(LOWER(classification) &gt; #{cursorTextValue} OR (LOWER(classification) = #{cursorTextValue} AND id &gt; #{cursorId}))</when>
                  <when test='sortBy == "STATUS" and sortDirection == "DESC"'>(LOWER(status) &lt; #{cursorTextValue} OR (LOWER(status) = #{cursorTextValue} AND id &lt; #{cursorId}))</when>
                  <when test='sortBy == "STATUS" and sortDirection == "ASC"'>(LOWER(status) &gt; #{cursorTextValue} OR (LOWER(status) = #{cursorTextValue} AND id &gt; #{cursorId}))</when>
                  <otherwise>1 = 0</otherwise>
                </choose>
              </if>
            </where>
            ORDER BY
            <choose>
              <when test='sortBy == "CREATED_AT"'>created_at</when>
              <when test='sortBy == "UPDATED_AT"'>updated_at</when>
              <when test='sortBy == "CASE_NUMBER"'>LOWER(case_number)</when>
              <when test='sortBy == "TITLE"'>LOWER(title)</when>
              <when test='sortBy == "CLASSIFICATION"'>LOWER(classification)</when>
              <when test='sortBy == "STATUS"'>LOWER(status)</when>
              <otherwise>created_at</otherwise>
            </choose>
            <choose>
              <when test='sortDirection == "ASC"'>ASC</when>
              <otherwise>DESC</otherwise>
            </choose>,
            id
            <choose>
              <when test='sortDirection == "ASC"'>ASC</when>
              <otherwise>DESC</otherwise>
            </choose>
            LIMIT #{limitPlusOne}
            </script>
            """)
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
            ON CONFLICT (event_id) DO NOTHING
            """)
  int insertAuditEventIfAbsent(AuditEventData auditEventData);

  @Select(
      """
            <script>
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
            <where>
              case_id = #{caseId}
              <if test="actorId != null">
                AND actor_id = #{actorId}
              </if>
              <if test="eventType != null">
                AND event_type = #{eventType}
              </if>
              <if test="action != null">
                AND action = #{action}
              </if>
              <if test="result != null">
                AND result = #{result}
              </if>
              <if test="quickSearchPattern != null">
                AND
                <trim prefix="(" suffix=")" prefixOverrides="OR ">
                  OR LOWER(event_type) LIKE #{quickSearchPattern}
                  OR LOWER(action) LIKE #{quickSearchPattern}
                  OR LOWER(actor_id) LIKE #{quickSearchPattern}
                  OR LOWER(result) LIKE #{quickSearchPattern}
                  OR LOWER(COALESCE(reason, '')) LIKE #{quickSearchPattern}
                  OR LOWER(resource_type) LIKE #{quickSearchPattern}
                  OR LOWER(resource_id) LIKE #{quickSearchPattern}
                  OR LOWER(correlation_id) LIKE #{quickSearchPattern}
                </trim>
              </if>
              <if test="searchPattern != null">
                AND
                <choose>
                  <when test='searchField == "EVENT_TYPE"'>LOWER(event_type) LIKE #{searchPattern}</when>
                  <when test='searchField == "ACTION"'>LOWER(action) LIKE #{searchPattern}</when>
                  <when test='searchField == "ACTOR_ID"'>LOWER(actor_id) LIKE #{searchPattern}</when>
                  <when test='searchField == "RESULT"'>LOWER(result) LIKE #{searchPattern}</when>
                  <when test='searchField == "REASON"'>LOWER(COALESCE(reason, '')) LIKE #{searchPattern}</when>
                  <when test='searchField == "RESOURCE_TYPE"'>LOWER(resource_type) LIKE #{searchPattern}</when>
                  <when test='searchField == "RESOURCE_ID"'>LOWER(resource_id) LIKE #{searchPattern}</when>
                  <when test='searchField == "CORRELATION_ID"'>LOWER(correlation_id) LIKE #{searchPattern}</when>
                  <otherwise>1 = 0</otherwise>
                </choose>
              </if>
              <if test="cursorId != null">
                AND
                <choose>
                  <when test='sortBy == "TIMESTAMP" and sortDirection == "DESC"'>(timestamp &lt; #{cursorTimestampValue} OR (timestamp = #{cursorTimestampValue} AND event_id &lt; #{cursorId}))</when>
                  <when test='sortBy == "TIMESTAMP" and sortDirection == "ASC"'>(timestamp &gt; #{cursorTimestampValue} OR (timestamp = #{cursorTimestampValue} AND event_id &gt; #{cursorId}))</when>
                  <when test='sortBy == "EVENT_TYPE" and sortDirection == "DESC"'>(LOWER(event_type) &lt; #{cursorTextValue} OR (LOWER(event_type) = #{cursorTextValue} AND event_id &lt; #{cursorId}))</when>
                  <when test='sortBy == "EVENT_TYPE" and sortDirection == "ASC"'>(LOWER(event_type) &gt; #{cursorTextValue} OR (LOWER(event_type) = #{cursorTextValue} AND event_id &gt; #{cursorId}))</when>
                  <when test='sortBy == "ACTION" and sortDirection == "DESC"'>(LOWER(action) &lt; #{cursorTextValue} OR (LOWER(action) = #{cursorTextValue} AND event_id &lt; #{cursorId}))</when>
                  <when test='sortBy == "ACTION" and sortDirection == "ASC"'>(LOWER(action) &gt; #{cursorTextValue} OR (LOWER(action) = #{cursorTextValue} AND event_id &gt; #{cursorId}))</when>
                  <when test='sortBy == "RESULT" and sortDirection == "DESC"'>(LOWER(result) &lt; #{cursorTextValue} OR (LOWER(result) = #{cursorTextValue} AND event_id &lt; #{cursorId}))</when>
                  <when test='sortBy == "RESULT" and sortDirection == "ASC"'>(LOWER(result) &gt; #{cursorTextValue} OR (LOWER(result) = #{cursorTextValue} AND event_id &gt; #{cursorId}))</when>
                  <when test='sortBy == "ACTOR_ID" and sortDirection == "DESC"'>(LOWER(actor_id) &lt; #{cursorTextValue} OR (LOWER(actor_id) = #{cursorTextValue} AND event_id &lt; #{cursorId}))</when>
                  <when test='sortBy == "ACTOR_ID" and sortDirection == "ASC"'>(LOWER(actor_id) &gt; #{cursorTextValue} OR (LOWER(actor_id) = #{cursorTextValue} AND event_id &gt; #{cursorId}))</when>
                  <otherwise>1 = 0</otherwise>
                </choose>
              </if>
            </where>
            ORDER BY
            <choose>
              <when test='sortBy == "TIMESTAMP"'>timestamp</when>
              <when test='sortBy == "EVENT_TYPE"'>LOWER(event_type)</when>
              <when test='sortBy == "ACTION"'>LOWER(action)</when>
              <when test='sortBy == "RESULT"'>LOWER(result)</when>
              <when test='sortBy == "ACTOR_ID"'>LOWER(actor_id)</when>
              <otherwise>timestamp</otherwise>
            </choose>
            <choose>
              <when test='sortDirection == "ASC"'>ASC</when>
              <otherwise>DESC</otherwise>
            </choose>,
            event_id
            <choose>
              <when test='sortDirection == "ASC"'>ASC</when>
              <otherwise>DESC</otherwise>
            </choose>
            LIMIT #{limitPlusOne}
            </script>
            """)
  List<AuditEventData> findAuditEventsPage(AuditEventPageQueryData pageQueryData);
}
