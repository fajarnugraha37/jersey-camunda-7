package com.sentinel.enforcement.application.evidence;

import com.sentinel.enforcement.application.casefile.CaseNotFoundException;
import com.sentinel.enforcement.application.casefile.CaseRepository;
import com.sentinel.enforcement.application.messaging.ApplicationTransactionManager;
import com.sentinel.enforcement.application.messaging.MessagingEventFactory;
import com.sentinel.enforcement.application.messaging.OutboxRepository;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationDeniedException;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.Permission;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.evidence.Evidence;
import com.sentinel.enforcement.domain.evidence.EvidenceConflictException;
import com.sentinel.enforcement.domain.evidence.EvidenceUploadSession;
import com.sentinel.enforcement.domain.evidence.EvidenceVersion;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

public final class EvidenceApplicationService {
  private static final String CASE_RESOURCE_TYPE = "CASE";
  private static final String EVIDENCE_RESOURCE_TYPE = "EVIDENCE";

  private final AuthorizationService authorizationService;
  private final ApplicationTransactionManager transactionManager;
  private final CaseRepository caseRepository;
  private final EvidenceRepository evidenceRepository;
  private final OutboxRepository outboxRepository;
  private final EvidenceStoragePort evidenceStoragePort;
  private final Clock clock;
  private final String evidenceBucket;
  private final Duration uploadUrlTtl;
  private final Duration downloadUrlTtl;

  public EvidenceApplicationService(
      AuthorizationService authorizationService,
      ApplicationTransactionManager transactionManager,
      CaseRepository caseRepository,
      EvidenceRepository evidenceRepository,
      OutboxRepository outboxRepository,
      EvidenceStoragePort evidenceStoragePort,
      Clock clock,
      String evidenceBucket,
      Duration uploadUrlTtl,
      Duration downloadUrlTtl) {
    this.authorizationService = authorizationService;
    this.transactionManager = transactionManager;
    this.caseRepository = caseRepository;
    this.evidenceRepository = evidenceRepository;
    this.outboxRepository = outboxRepository;
    this.evidenceStoragePort = evidenceStoragePort;
    this.clock = clock;
    this.evidenceBucket = evidenceBucket;
    this.uploadUrlTtl = uploadUrlTtl;
    this.downloadUrlTtl = downloadUrlTtl;
  }

  public PreparedEvidenceUploadSession createUploadSession(
      ApplicationActor actor, UUID caseId, CreateEvidenceUploadSessionCommand command) {
    CaseRecord caseRecord = getRequiredCase(caseId);
    authorizationService.requirePermission(
        actor, Permission.CREATE_EVIDENCE_UPLOAD_SESSION, authorizationContext(caseRecord));

    Instant now = clock.instant();
    Evidence existingEvidence = null;
    UUID evidenceId =
        command.existingEvidenceId() == null ? UUID.randomUUID() : command.existingEvidenceId();
    if (command.existingEvidenceId() != null) {
      existingEvidence =
          evidenceRepository
              .findEvidenceById(command.existingEvidenceId())
              .orElseThrow(() -> new EvidenceNotFoundException(command.existingEvidenceId()));
      if (!existingEvidence.caseId().equals(caseId)) {
        throw new EvidenceConflictException(
            "EVIDENCE_CASE_MISMATCH",
            "Evidence " + existingEvidence.id() + " does not belong to case " + caseId + ".");
      }
      if (!existingEvidence.title().equals(command.title())) {
        throw new EvidenceConflictException(
            "EVIDENCE_TITLE_MISMATCH",
            "Existing evidence "
                + existingEvidence.id()
                + " must keep the same title across new versions.");
      }
      if (existingEvidence.classification() != command.classification()) {
        throw new EvidenceConflictException(
            "EVIDENCE_CLASSIFICATION_MISMATCH",
            "Existing evidence "
                + existingEvidence.id()
                + " must keep the same classification across new versions.");
      }
    }

    int targetVersionNumber = existingEvidence == null ? 1 : existingEvidence.latestVersion() + 1;
    String generatedFilename = generatedFilename(command.originalFilename());
    String objectKey =
        "/"
            + caseRecord.jurisdictionCode()
            + "/"
            + caseId
            + "/"
            + evidenceId
            + "/"
            + targetVersionNumber
            + "/"
            + generatedFilename;
    Instant expiresAt = now.plus(uploadUrlTtl);
    EvidenceUploadSession uploadSession =
        EvidenceUploadSession.create(
            UUID.randomUUID(),
            caseId,
            evidenceId,
            targetVersionNumber,
            command.originalFilename(),
            generatedFilename,
            evidenceBucket,
            objectKey,
            normalizedMediaType(command.mediaType()),
            command.sizeBytes(),
            normalizeChecksum(command.sha256Checksum()),
            command.classification(),
            now,
            expiresAt,
            actor.username());
    if (existingEvidence == null) {
      evidenceRepository.prepareNewEvidenceUpload(
          Evidence.create(
              evidenceId, caseId, command.title(), command.classification(), now, actor.username()),
          uploadSession);
    } else {
      evidenceRepository.prepareExistingEvidenceUpload(uploadSession);
    }
    return new PreparedEvidenceUploadSession(
        evidenceId,
        uploadSession.id(),
        targetVersionNumber,
        evidenceStoragePort.createPresignedUploadUrl(evidenceBucket, objectKey, uploadUrlTtl),
        expiresAt,
        objectKey);
  }

  public EvidenceDetailsView finalizeEvidenceVersion(
      ApplicationActor actor, UUID evidenceId, FinalizeEvidenceVersionCommand command) {
    Evidence evidence = getRequiredEvidence(evidenceId);
    CaseRecord caseRecord = getRequiredCase(evidence.caseId());
    authorizationService.requirePermission(
        actor, Permission.FINALIZE_EVIDENCE, authorizationContext(caseRecord));
    EvidenceUploadSession uploadSession =
        evidenceRepository
            .findUploadSessionById(command.uploadSessionId())
            .orElseThrow(
                () ->
                    new EvidenceConflictException(
                        "EVIDENCE_UPLOAD_SESSION_NOT_FOUND",
                        "Evidence upload session "
                            + command.uploadSessionId()
                            + " was not found."));
    if (!uploadSession.evidenceId().equals(evidenceId)) {
      throw new EvidenceConflictException(
          "EVIDENCE_UPLOAD_SESSION_MISMATCH",
          "Upload session "
              + uploadSession.id()
              + " does not belong to evidence "
              + evidenceId
              + ".");
    }
    if (uploadSession.targetVersionNumber() <= evidence.latestVersion()) {
      throw new EvidenceConflictException(
          "EVIDENCE_UPLOAD_SESSION_STALE",
          "Upload session "
              + uploadSession.id()
              + " targets version "
              + uploadSession.targetVersionNumber()
              + " but evidence "
              + evidenceId
              + " is already at version "
              + evidence.latestVersion()
              + ".");
    }

    EvidenceStoragePort.StoredEvidenceObject storedObject =
        evidenceStoragePort.statObject(uploadSession.bucket(), uploadSession.objectKey());
    if (storedObject.sizeBytes() != uploadSession.sizeBytes()) {
      throw new EvidenceConflictException(
          "EVIDENCE_SIZE_MISMATCH",
          "Uploaded object size does not match the evidence upload session contract.");
    }
    if (!normalizedMediaType(storedObject.mediaType()).equals(uploadSession.mediaType())) {
      throw new EvidenceConflictException(
          "EVIDENCE_MEDIA_TYPE_MISMATCH",
          "Uploaded object media type does not match the evidence upload session contract.");
    }
    String actualChecksum = calculateSha256(uploadSession.bucket(), uploadSession.objectKey());
    if (!actualChecksum.equals(uploadSession.sha256Checksum())) {
      throw new EvidenceConflictException(
          "EVIDENCE_CHECKSUM_MISMATCH",
          "Uploaded object checksum does not match the evidence upload session contract.");
    }

    Instant now = clock.instant();
    EvidenceUploadSession finalizedSession = uploadSession.finalizeSession(now, actor.username());
    Evidence activatedEvidence =
        evidence.activate(finalizedSession.targetVersionNumber(), now, actor.username());
    EvidenceVersion evidenceVersion =
        new EvidenceVersion(
            UUID.randomUUID(),
            evidenceId,
            finalizedSession.targetVersionNumber(),
            finalizedSession.originalFilename(),
            finalizedSession.generatedFilename(),
            finalizedSession.bucket(),
            finalizedSession.objectKey(),
            finalizedSession.mediaType(),
            finalizedSession.sizeBytes(),
            finalizedSession.sha256Checksum(),
            now,
            actor.username(),
            now,
            actor.username());
    transactionManager.required(
        () -> {
          evidenceRepository.finalizeUpload(activatedEvidence, evidenceVersion, finalizedSession);
          caseRepository.appendAuditEvent(
              auditEvent(
                  actor,
                  caseRecord,
                  evidenceId,
                  "EvidenceVersionFinalized",
                  "EVIDENCE_FINALIZED",
                  "SUCCESS",
                  "Evidence upload finalized.",
                  "evidenceId="
                      + evidenceId
                      + ";version="
                      + evidenceVersion.versionNumber()
                      + ";checksum="
                      + evidenceVersion.sha256Checksum(),
                  command.correlationId(),
                  command.sourceIp(),
                  now));
          outboxRepository.enqueue(
              MessagingEventFactory.evidenceVersionFinalized(
                  actor, activatedEvidence, evidenceVersion, command.correlationId(), now));
          return null;
        });
    return new EvidenceDetailsView(activatedEvidence, evidenceVersion);
  }

  public EvidenceDetailsView getEvidence(ApplicationActor actor, UUID evidenceId) {
    Evidence evidence = getRequiredEvidence(evidenceId);
    CaseRecord caseRecord = getRequiredCase(evidence.caseId());
    authorizationService.requirePermission(
        actor, Permission.READ_EVIDENCE, authorizationContext(caseRecord));
    EvidenceVersion latestVersion =
        evidenceRepository
            .findLatestVersion(evidenceId)
            .orElseThrow(
                () ->
                    new EvidenceConflictException(
                        "EVIDENCE_VERSION_NOT_FOUND",
                        "Evidence " + evidenceId + " does not have a finalized version yet."));
    return new EvidenceDetailsView(evidence, latestVersion);
  }

  public EvidenceDownloadSession createDownloadSession(
      ApplicationActor actor, UUID evidenceId, CreateEvidenceDownloadSessionCommand command) {
    Evidence evidence = getRequiredEvidence(evidenceId);
    CaseRecord caseRecord = getRequiredCase(evidence.caseId());
    EvidenceVersion latestVersion =
        evidenceRepository
            .findLatestVersion(evidenceId)
            .orElseThrow(
                () ->
                    new EvidenceConflictException(
                        "EVIDENCE_VERSION_NOT_FOUND",
                        "Evidence " + evidenceId + " does not have a finalized version yet."));
    Instant now = clock.instant();
    try {
      authorizationService.requirePermission(
          actor, Permission.CREATE_EVIDENCE_DOWNLOAD_SESSION, authorizationContext(caseRecord));
    } catch (AuthorizationDeniedException exception) {
      caseRepository.appendAuditEvent(
          auditEvent(
              actor,
              caseRecord,
              evidenceId,
              "EvidenceDownloadDenied",
              "EVIDENCE_DOWNLOAD_DENIED",
              "DENIED",
              command.reason(),
              "evidenceId=" + evidenceId,
              command.correlationId(),
              command.sourceIp(),
              now));
      throw exception;
    }
    caseRepository.appendAuditEvent(
        auditEvent(
            actor,
            caseRecord,
            evidenceId,
            "EvidenceDownloadSessionCreated",
            "EVIDENCE_DOWNLOAD_SESSION_CREATED",
            "SUCCESS",
            command.reason(),
            "evidenceId=" + evidenceId + ";version=" + latestVersion.versionNumber(),
            command.correlationId(),
            command.sourceIp(),
            now));
    return new EvidenceDownloadSession(
        evidenceStoragePort.createPresignedDownloadUrl(
            latestVersion.bucket(), latestVersion.objectKey(), downloadUrlTtl),
        now.plus(downloadUrlTtl));
  }

  private CaseRecord getRequiredCase(UUID caseId) {
    return caseRepository.findById(caseId).orElseThrow(() -> new CaseNotFoundException(caseId));
  }

  private Evidence getRequiredEvidence(UUID evidenceId) {
    return evidenceRepository
        .findEvidenceById(evidenceId)
        .orElseThrow(() -> new EvidenceNotFoundException(evidenceId));
  }

  private AuthorizationContext authorizationContext(CaseRecord caseRecord) {
    return new AuthorizationContext(
        caseRecord.jurisdictionCode(),
        CASE_RESOURCE_TYPE,
        caseRecord.id().toString(),
        caseRecord.assigneeUserId());
  }

  private AuditEvent auditEvent(
      ApplicationActor actor,
      CaseRecord caseRecord,
      UUID evidenceId,
      String eventType,
      String action,
      String result,
      String reason,
      String metadata,
      String correlationId,
      String sourceIp,
      Instant now) {
    return new AuditEvent(
        UUID.randomUUID(),
        eventType,
        "USER",
        actor.username(),
        String.join(",", actor.roles().stream().sorted().toList()),
        action,
        EVIDENCE_RESOURCE_TYPE,
        evidenceId.toString(),
        caseRecord.id(),
        now,
        correlationId,
        sourceIp,
        result,
        reason,
        null,
        null,
        metadata);
  }

  private static String generatedFilename(String originalFilename) {
    String sanitized = originalFilename.replace('\\', '-').replace('/', '-').replace(' ', '-');
    String extension = "";
    int extensionIndex = sanitized.lastIndexOf('.');
    if (extensionIndex >= 0) {
      extension = sanitized.substring(extensionIndex).toLowerCase(Locale.ROOT);
    }
    return UUID.randomUUID() + extension;
  }

  private static String normalizedMediaType(String mediaType) {
    if (mediaType == null || mediaType.isBlank()) {
      throw new IllegalArgumentException("mediaType must not be blank");
    }
    return mediaType.trim().toLowerCase(Locale.ROOT);
  }

  private static String normalizeChecksum(String checksum) {
    if (checksum == null || checksum.isBlank()) {
      throw new IllegalArgumentException("sha256Checksum must not be blank");
    }
    String normalized = checksum.trim().toLowerCase(Locale.ROOT);
    if (normalized.length() != 64) {
      throw new IllegalArgumentException(
          "sha256Checksum must be a 64 character lowercase hex digest");
    }
    return normalized;
  }

  private String calculateSha256(String bucket, String objectKey) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is not available.", exception);
    }
    try (InputStream inputStream =
        new DigestInputStream(evidenceStoragePort.getObjectStream(bucket, objectKey), digest)) {
      inputStream.transferTo(java.io.OutputStream.nullOutputStream());
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to compute SHA-256 for object " + objectKey + " in bucket " + bucket + ".",
          exception);
    }
  }
}
