package com.sentinel.enforcement.persistence.evidence;

import com.sentinel.enforcement.application.evidence.EvidenceRepository;
import com.sentinel.enforcement.domain.evidence.Evidence;
import com.sentinel.enforcement.domain.evidence.EvidenceClassification;
import com.sentinel.enforcement.domain.evidence.EvidenceConflictException;
import com.sentinel.enforcement.domain.evidence.EvidenceStorageStatus;
import com.sentinel.enforcement.domain.evidence.EvidenceUploadSession;
import com.sentinel.enforcement.domain.evidence.EvidenceUploadSessionStatus;
import com.sentinel.enforcement.domain.evidence.EvidenceVersion;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public final class EvidenceRepositoryMyBatisAdapter implements EvidenceRepository {
  private final SqlSessionFactory sqlSessionFactory;

  public EvidenceRepositoryMyBatisAdapter(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  @Override
  public void prepareNewEvidenceUpload(Evidence evidence, EvidenceUploadSession uploadSession) {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      EvidenceMyBatisMapper mapper = session.getMapper(EvidenceMyBatisMapper.class);
      mapper.insertEvidence(toRecord(evidence));
      mapper.insertUploadSession(toRecord(uploadSession));
      session.commit();
    }
  }

  @Override
  public void prepareExistingEvidenceUpload(EvidenceUploadSession uploadSession) {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      session.getMapper(EvidenceMyBatisMapper.class).insertUploadSession(toRecord(uploadSession));
      session.commit();
    }
  }

  @Override
  public Optional<Evidence> findEvidenceById(UUID evidenceId) {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      return Optional.ofNullable(session.getMapper(EvidenceMyBatisMapper.class).findEvidenceById(evidenceId))
          .map(this::toDomain);
    }
  }

  @Override
  public Optional<EvidenceVersion> findLatestVersion(UUID evidenceId) {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      return Optional.ofNullable(session.getMapper(EvidenceMyBatisMapper.class).findLatestVersion(evidenceId))
          .map(this::toDomain);
    }
  }

  @Override
  public Optional<EvidenceUploadSession> findUploadSessionById(UUID uploadSessionId) {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      return Optional.ofNullable(
              session.getMapper(EvidenceMyBatisMapper.class).findUploadSessionById(uploadSessionId))
          .map(this::toDomain);
    }
  }

  @Override
  public void finalizeUpload(
      Evidence evidence, EvidenceVersion evidenceVersion, EvidenceUploadSession uploadSession) {
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      EvidenceMyBatisMapper mapper = session.getMapper(EvidenceMyBatisMapper.class);
      int updatedEvidence = mapper.updateEvidence(toRecord(evidence), evidence.version() - 1);
      if (updatedEvidence != 1) {
        throw new EvidenceConflictException(
            "CONCURRENT_MODIFICATION",
            "Evidence " + evidence.id() + " was modified concurrently before finalize completed.");
      }
      int updatedSession =
          mapper.updateUploadSession(toRecord(uploadSession), uploadSession.version() - 1);
      if (updatedSession != 1) {
        throw new EvidenceConflictException(
            "CONCURRENT_MODIFICATION",
            "Evidence upload session "
                + uploadSession.id()
                + " was modified concurrently before finalize completed.");
      }
      mapper.insertEvidenceVersion(toRecord(evidenceVersion));
      session.commit();
    }
  }

  private EvidenceRecord toRecord(Evidence evidence) {
    return new EvidenceRecord(
        evidence.id(),
        evidence.caseId(),
        evidence.title(),
        evidence.classification().name(),
        evidence.storageStatus().name(),
        evidence.latestVersion(),
        evidence.createdAt().atOffset(ZoneOffset.UTC),
        evidence.createdBy(),
        evidence.updatedAt().atOffset(ZoneOffset.UTC),
        evidence.updatedBy(),
        evidence.version());
  }

  private EvidenceUploadSessionRecord toRecord(EvidenceUploadSession uploadSession) {
    return new EvidenceUploadSessionRecord(
        uploadSession.id(),
        uploadSession.caseId(),
        uploadSession.evidenceId(),
        uploadSession.targetVersionNumber(),
        uploadSession.originalFilename(),
        uploadSession.generatedFilename(),
        uploadSession.bucket(),
        uploadSession.objectKey(),
        uploadSession.mediaType(),
        uploadSession.sizeBytes(),
        uploadSession.sha256Checksum(),
        uploadSession.classification().name(),
        uploadSession.status().name(),
        uploadSession.expiresAt().atOffset(ZoneOffset.UTC),
        uploadSession.createdAt().atOffset(ZoneOffset.UTC),
        uploadSession.createdBy(),
        uploadSession.updatedAt().atOffset(ZoneOffset.UTC),
        uploadSession.updatedBy(),
        uploadSession.version());
  }

  private EvidenceVersionRecord toRecord(EvidenceVersion evidenceVersion) {
    return new EvidenceVersionRecord(
        evidenceVersion.id(),
        evidenceVersion.evidenceId(),
        evidenceVersion.versionNumber(),
        evidenceVersion.originalFilename(),
        evidenceVersion.generatedFilename(),
        evidenceVersion.bucket(),
        evidenceVersion.objectKey(),
        evidenceVersion.mediaType(),
        evidenceVersion.sizeBytes(),
        evidenceVersion.sha256Checksum(),
        evidenceVersion.uploadedAt().atOffset(ZoneOffset.UTC),
        evidenceVersion.uploadedBy(),
        evidenceVersion.createdAt().atOffset(ZoneOffset.UTC),
        evidenceVersion.createdBy());
  }

  private Evidence toDomain(EvidenceRecord evidenceRecord) {
    return new Evidence(
        evidenceRecord.id(),
        evidenceRecord.caseId(),
        evidenceRecord.title(),
        EvidenceClassification.valueOf(evidenceRecord.classification()),
        EvidenceStorageStatus.valueOf(evidenceRecord.storageStatus()),
        evidenceRecord.latestVersion(),
        evidenceRecord.createdAt().toInstant(),
        evidenceRecord.createdBy(),
        evidenceRecord.updatedAt().toInstant(),
        evidenceRecord.updatedBy(),
        evidenceRecord.version());
  }

  private EvidenceVersion toDomain(EvidenceVersionRecord evidenceVersionRecord) {
    return new EvidenceVersion(
        evidenceVersionRecord.id(),
        evidenceVersionRecord.evidenceId(),
        evidenceVersionRecord.versionNumber(),
        evidenceVersionRecord.originalFilename(),
        evidenceVersionRecord.generatedFilename(),
        evidenceVersionRecord.bucket(),
        evidenceVersionRecord.objectKey(),
        evidenceVersionRecord.mediaType(),
        evidenceVersionRecord.sizeBytes(),
        evidenceVersionRecord.sha256Checksum(),
        evidenceVersionRecord.uploadedAt().toInstant(),
        evidenceVersionRecord.uploadedBy(),
        evidenceVersionRecord.createdAt().toInstant(),
        evidenceVersionRecord.createdBy());
  }

  private EvidenceUploadSession toDomain(EvidenceUploadSessionRecord uploadSessionRecord) {
    return new EvidenceUploadSession(
        uploadSessionRecord.id(),
        uploadSessionRecord.caseId(),
        uploadSessionRecord.evidenceId(),
        uploadSessionRecord.targetVersionNumber(),
        uploadSessionRecord.originalFilename(),
        uploadSessionRecord.generatedFilename(),
        uploadSessionRecord.bucket(),
        uploadSessionRecord.objectKey(),
        uploadSessionRecord.mediaType(),
        uploadSessionRecord.sizeBytes(),
        uploadSessionRecord.sha256Checksum(),
        EvidenceClassification.valueOf(uploadSessionRecord.classification()),
        EvidenceUploadSessionStatus.valueOf(uploadSessionRecord.status()),
        uploadSessionRecord.expiresAt().toInstant(),
        uploadSessionRecord.createdAt().toInstant(),
        uploadSessionRecord.createdBy(),
        uploadSessionRecord.updatedAt().toInstant(),
        uploadSessionRecord.updatedBy(),
        uploadSessionRecord.version());
  }
}
