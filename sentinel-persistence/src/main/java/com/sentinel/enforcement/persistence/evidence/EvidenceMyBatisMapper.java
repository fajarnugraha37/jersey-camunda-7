package com.sentinel.enforcement.persistence.evidence;

import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EvidenceMyBatisMapper {

  @Insert(
      """
            INSERT INTO evidence (
                id,
                case_id,
                title,
                classification,
                storage_status,
                latest_version,
                created_at,
                created_by,
                updated_at,
                updated_by,
                version
            ) VALUES (
                #{id},
                #{caseId},
                #{title},
                #{classification},
                #{storageStatus},
                #{latestVersion},
                #{createdAt},
                #{createdBy},
                #{updatedAt},
                #{updatedBy},
                #{version}
            )
            """)
  int insertEvidence(EvidenceRecord evidenceRecord);

  @Insert(
      """
            INSERT INTO evidence_upload_session (
                id,
                case_id,
                evidence_id,
                target_version_number,
                original_filename,
                generated_filename,
                bucket,
                object_key,
                media_type,
                size_bytes,
                sha256_checksum,
                classification,
                status,
                expires_at,
                created_at,
                created_by,
                updated_at,
                updated_by,
                version
            ) VALUES (
                #{id},
                #{caseId},
                #{evidenceId},
                #{targetVersionNumber},
                #{originalFilename},
                #{generatedFilename},
                #{bucket},
                #{objectKey},
                #{mediaType},
                #{sizeBytes},
                #{sha256Checksum},
                #{classification},
                #{status},
                #{expiresAt},
                #{createdAt},
                #{createdBy},
                #{updatedAt},
                #{updatedBy},
                #{version}
            )
            """)
  int insertUploadSession(EvidenceUploadSessionRecord uploadSessionRecord);

  @Update(
      """
            <script>
            UPDATE evidence
            SET
                storage_status = #{evidence.storageStatus},
                latest_version = #{evidence.latestVersion},
                updated_at = #{evidence.updatedAt},
                updated_by = #{evidence.updatedBy},
                version = #{evidence.version}
            WHERE id = #{evidence.id}
              AND version = #{expectedVersion}
            </script>
            """)
  int updateEvidence(
      @Param("evidence") EvidenceRecord evidenceRecord,
      @Param("expectedVersion") long expectedVersion);

  @Update(
      """
            <script>
            UPDATE evidence_upload_session
            SET
                status = #{session.status},
                updated_at = #{session.updatedAt},
                updated_by = #{session.updatedBy},
                version = #{session.version}
            WHERE id = #{session.id}
              AND version = #{expectedVersion}
            </script>
            """)
  int updateUploadSession(
      @Param("session") EvidenceUploadSessionRecord uploadSessionRecord,
      @Param("expectedVersion") long expectedVersion);

  @Insert(
      """
            INSERT INTO evidence_version (
                id,
                evidence_id,
                version_number,
                original_filename,
                generated_filename,
                bucket,
                object_key,
                media_type,
                size_bytes,
                sha256_checksum,
                uploaded_at,
                uploaded_by,
                created_at,
                created_by
            ) VALUES (
                #{id},
                #{evidenceId},
                #{versionNumber},
                #{originalFilename},
                #{generatedFilename},
                #{bucket},
                #{objectKey},
                #{mediaType},
                #{sizeBytes},
                #{sha256Checksum},
                #{uploadedAt},
                #{uploadedBy},
                #{createdAt},
                #{createdBy}
            )
            """)
  int insertEvidenceVersion(EvidenceVersionRecord evidenceVersionRecord);

  @Select(
      """
            SELECT
                id,
                case_id AS caseId,
                title,
                classification,
                storage_status AS storageStatus,
                latest_version AS latestVersion,
                created_at AS createdAt,
                created_by AS createdBy,
                updated_at AS updatedAt,
                updated_by AS updatedBy,
                version
            FROM evidence
            WHERE id = #{id}
            """)
  EvidenceRecord findEvidenceById(UUID id);

  @Select(
      """
            SELECT
                id,
                evidence_id AS evidenceId,
                version_number AS versionNumber,
                original_filename AS originalFilename,
                generated_filename AS generatedFilename,
                bucket,
                object_key AS objectKey,
                media_type AS mediaType,
                size_bytes AS sizeBytes,
                sha256_checksum AS sha256Checksum,
                uploaded_at AS uploadedAt,
                uploaded_by AS uploadedBy,
                created_at AS createdAt,
                created_by AS createdBy
            FROM evidence_version
            WHERE evidence_id = #{evidenceId}
            ORDER BY version_number DESC
            LIMIT 1
            """)
  EvidenceVersionRecord findLatestVersion(UUID evidenceId);

  @Select(
      """
            SELECT
                id,
                case_id AS caseId,
                evidence_id AS evidenceId,
                target_version_number AS targetVersionNumber,
                original_filename AS originalFilename,
                generated_filename AS generatedFilename,
                bucket,
                object_key AS objectKey,
                media_type AS mediaType,
                size_bytes AS sizeBytes,
                sha256_checksum AS sha256Checksum,
                classification,
                status,
                expires_at AS expiresAt,
                created_at AS createdAt,
                created_by AS createdBy,
                updated_at AS updatedAt,
                updated_by AS updatedBy,
                version
            FROM evidence_upload_session
            WHERE id = #{id}
            """)
  EvidenceUploadSessionRecord findUploadSessionById(UUID id);
}
