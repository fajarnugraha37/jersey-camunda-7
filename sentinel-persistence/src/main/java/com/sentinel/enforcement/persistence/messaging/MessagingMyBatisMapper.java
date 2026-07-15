package com.sentinel.enforcement.persistence.messaging;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MessagingMyBatisMapper {

  @Insert(
      """
            INSERT INTO outbox_event (
                event_id,
                topic,
                message_key,
                event_type,
                event_version,
                aggregate_type,
                aggregate_id,
                occurred_at,
                correlation_id,
                causation_id,
                actor_type,
                actor_id,
                payload_json,
                status,
                available_at,
                lease_owner,
                lease_expires_at,
                publish_attempts,
                last_error,
                published_at,
                created_at,
                created_by,
                updated_at,
                updated_by,
                version
            ) VALUES (
                #{eventId},
                #{topic},
                #{messageKey},
                #{eventType},
                #{eventVersion},
                #{aggregateType},
                #{aggregateId},
                #{occurredAt},
                #{correlationId},
                #{causationId},
                #{actorType},
                #{actorId},
                CAST(#{payloadJson} AS JSONB),
                #{status},
                #{availableAt},
                #{leaseOwner},
                #{leaseExpiresAt},
                #{publishAttempts},
                #{lastError},
                #{publishedAt},
                #{createdAt},
                #{createdBy},
                #{updatedAt},
                #{updatedBy},
                #{version}
            )
            """)
  int insertOutboxEvent(OutboxEventData outboxEventData);

  @Select(
      """
            <script>
            WITH claimed AS (
              SELECT event_id
              FROM outbox_event
              WHERE status = 'PENDING'
                AND available_at &lt;= #{now}
                AND (lease_expires_at IS NULL OR lease_expires_at &lt;= #{now})
              ORDER BY occurred_at, event_id
              FOR UPDATE SKIP LOCKED
              LIMIT #{batchSize}
            )
            UPDATE outbox_event o
            SET
              lease_owner = #{leaseOwner},
              lease_expires_at = #{leaseExpiresAt},
              updated_at = #{now},
              updated_by = #{updatedBy}
            FROM claimed
            WHERE o.event_id = claimed.event_id
            RETURNING
              o.event_id AS eventId,
              o.topic,
              o.message_key AS messageKey,
              o.event_type AS eventType,
              o.event_version AS eventVersion,
              o.aggregate_type AS aggregateType,
              o.aggregate_id AS aggregateId,
              o.occurred_at AS occurredAt,
              o.correlation_id AS correlationId,
              o.causation_id AS causationId,
              o.actor_type AS actorType,
              o.actor_id AS actorId,
              CAST(o.payload_json AS TEXT) AS payloadJson,
              o.status,
              o.available_at AS availableAt,
              o.lease_owner AS leaseOwner,
              o.lease_expires_at AS leaseExpiresAt,
              o.publish_attempts AS publishAttempts,
              o.last_error AS lastError,
              o.published_at AS publishedAt,
              o.created_at AS createdAt,
              o.created_by AS createdBy,
              o.updated_at AS updatedAt,
              o.updated_by AS updatedBy,
              o.version
            </script>
            """)
  List<OutboxEventData> claimPending(
      @Param("leaseOwner") String leaseOwner,
      @Param("now") OffsetDateTime now,
      @Param("leaseExpiresAt") OffsetDateTime leaseExpiresAt,
      @Param("batchSize") int batchSize,
      @Param("updatedBy") String updatedBy);

  @Update(
      """
            UPDATE outbox_event
            SET
              status = 'PUBLISHED',
              publish_attempts = publish_attempts + 1,
              published_at = #{publishedAt},
              lease_owner = NULL,
              lease_expires_at = NULL,
              last_error = NULL,
              updated_at = #{publishedAt},
              updated_by = #{updatedBy},
              version = version + 1
            WHERE event_id = #{eventId}
            """)
  int markPublished(
      @Param("eventId") UUID eventId,
      @Param("publishedAt") OffsetDateTime publishedAt,
      @Param("updatedBy") String updatedBy);

  @Update(
      """
            UPDATE outbox_event
            SET
              publish_attempts = publish_attempts + 1,
              available_at = #{nextAttemptAt},
              lease_owner = NULL,
              lease_expires_at = NULL,
              last_error = #{lastError},
              updated_at = #{now},
              updated_by = #{updatedBy},
              version = version + 1
            WHERE event_id = #{eventId}
            """)
  int releaseForRetry(
      @Param("eventId") UUID eventId,
      @Param("now") OffsetDateTime now,
      @Param("nextAttemptAt") OffsetDateTime nextAttemptAt,
      @Param("lastError") String lastError,
      @Param("updatedBy") String updatedBy);

  @Select("SELECT COUNT(*) FROM outbox_event WHERE status = 'PENDING'")
  long countPending();

  @Insert(
      """
            INSERT INTO inbox_event (
                id,
                consumer_name,
                event_id,
                topic,
                created_at,
                created_by,
                processed_at,
                result_reference,
                version
            ) VALUES (
                #{id},
                #{consumerName},
                #{eventId},
                #{topic},
                #{createdAt},
                #{createdBy},
                #{processedAt},
                #{resultReference},
                #{version}
            )
            ON CONFLICT (consumer_name, event_id) DO NOTHING
            """)
  int insertInboxEventIfAbsent(InboxEventData inboxEventData);

  @Update(
      """
            UPDATE inbox_event
            SET
              processed_at = #{processedAt},
              result_reference = #{resultReference},
              version = version + 1
            WHERE consumer_name = #{consumerName}
              AND event_id = #{eventId}
            """)
  int completeInboxEvent(
      @Param("consumerName") String consumerName,
      @Param("eventId") UUID eventId,
      @Param("processedAt") OffsetDateTime processedAt,
      @Param("resultReference") String resultReference);

  @Insert(
      """
            INSERT INTO notification (
                id,
                consumer_name,
                event_id,
                case_id,
                notification_type,
                title,
                body,
                status,
                created_at,
                created_by,
                updated_at,
                updated_by,
                version
            ) VALUES (
                #{id},
                #{consumerName},
                #{eventId},
                #{caseId},
                #{notificationType},
                #{title},
                #{body},
                #{status},
                #{createdAt},
                #{createdBy},
                #{updatedAt},
                #{updatedBy},
                #{version}
            )
            """)
  int insertNotification(NotificationData notificationData);
}
