package com.sentinel.enforcement.api.evidence;

import com.sentinel.enforcement.api.generated.model.CreateEvidenceDownloadSessionRequest;
import com.sentinel.enforcement.api.generated.model.CreateEvidenceDownloadSessionResponse;
import com.sentinel.enforcement.api.generated.model.CreateEvidenceUploadSessionRequest;
import com.sentinel.enforcement.api.generated.model.CreateEvidenceUploadSessionResponse;
import com.sentinel.enforcement.api.generated.model.EvidenceClassificationValue;
import com.sentinel.enforcement.api.generated.model.EvidenceResponse;
import com.sentinel.enforcement.api.generated.model.EvidenceStorageStatusValue;
import com.sentinel.enforcement.api.generated.model.EvidenceVersionResponse;
import com.sentinel.enforcement.api.generated.model.FinalizeEvidenceVersionRequest;
import com.sentinel.enforcement.application.evidence.CreateEvidenceDownloadSessionCommand;
import com.sentinel.enforcement.application.evidence.CreateEvidenceUploadSessionCommand;
import com.sentinel.enforcement.application.evidence.EvidenceDetailsView;
import com.sentinel.enforcement.application.evidence.EvidenceDownloadSession;
import com.sentinel.enforcement.application.evidence.FinalizeEvidenceVersionCommand;
import com.sentinel.enforcement.application.evidence.PreparedEvidenceUploadSession;
import com.sentinel.enforcement.domain.evidence.Evidence;
import com.sentinel.enforcement.domain.evidence.EvidenceClassification;
import com.sentinel.enforcement.domain.evidence.EvidenceStorageStatus;
import com.sentinel.enforcement.domain.evidence.EvidenceVersion;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class ApiEvidenceMapper {
  public static final ApiEvidenceMapper INSTANCE = new ApiEvidenceMapper();

  private ApiEvidenceMapper() {}

  public CreateEvidenceUploadSessionCommand toCreateUploadSessionCommand(
      CreateEvidenceUploadSessionRequest request, String correlationId, String sourceIp) {
    return new CreateEvidenceUploadSessionCommand(
        request.getExistingEvidenceId(),
        request.getTitle(),
        toDomainClassification(request.getClassification()),
        request.getOriginalFilename(),
        request.getMediaType(),
        request.getSizeBytes() == null ? 0L : request.getSizeBytes(),
        request.getSha256Checksum(),
        correlationId,
        sourceIp);
  }

  public FinalizeEvidenceVersionCommand toFinalizeCommand(
      FinalizeEvidenceVersionRequest request, String correlationId, String sourceIp) {
    return new FinalizeEvidenceVersionCommand(
        request.getUploadSessionId(), correlationId, sourceIp);
  }

  public CreateEvidenceDownloadSessionCommand toCreateDownloadSessionCommand(
      CreateEvidenceDownloadSessionRequest request, String correlationId, String sourceIp) {
    return new CreateEvidenceDownloadSessionCommand(request.getReason(), correlationId, sourceIp);
  }

  public CreateEvidenceUploadSessionResponse toCreateUploadSessionResponse(
      PreparedEvidenceUploadSession preparedSession) {
    return new CreateEvidenceUploadSessionResponse()
        .evidenceId(preparedSession.evidenceId())
        .uploadSessionId(preparedSession.uploadSessionId())
        .targetVersionNumber(preparedSession.targetVersionNumber())
        .uploadUrl(preparedSession.uploadUrl())
        .expiresAt(OffsetDateTime.ofInstant(preparedSession.expiresAt(), ZoneOffset.UTC))
        .objectKey(preparedSession.objectKey());
  }

  public CreateEvidenceDownloadSessionResponse toCreateDownloadSessionResponse(
      EvidenceDownloadSession downloadSession) {
    return new CreateEvidenceDownloadSessionResponse()
        .downloadUrl(downloadSession.downloadUrl())
        .expiresAt(OffsetDateTime.ofInstant(downloadSession.expiresAt(), ZoneOffset.UTC));
  }

  public EvidenceResponse toResponse(EvidenceDetailsView detailsView) {
    Evidence evidence = detailsView.evidence();
    return new EvidenceResponse()
        .id(evidence.id())
        .caseId(evidence.caseId())
        .title(evidence.title())
        .classification(toApiClassification(evidence.classification()))
        .storageStatus(toApiStorageStatus(evidence.storageStatus()))
        .latestVersion(evidence.latestVersion())
        .createdAt(OffsetDateTime.ofInstant(evidence.createdAt(), ZoneOffset.UTC))
        .createdBy(evidence.createdBy())
        .updatedAt(OffsetDateTime.ofInstant(evidence.updatedAt(), ZoneOffset.UTC))
        .updatedBy(evidence.updatedBy())
        .version(evidence.version())
        .latestVersionMetadata(toVersionResponse(detailsView.latestVersion()));
  }

  public EvidenceVersionResponse toVersionResponse(EvidenceVersion evidenceVersion) {
    return new EvidenceVersionResponse()
        .id(evidenceVersion.id())
        .evidenceId(evidenceVersion.evidenceId())
        .versionNumber(evidenceVersion.versionNumber())
        .originalFilename(evidenceVersion.originalFilename())
        .generatedFilename(evidenceVersion.generatedFilename())
        .bucket(evidenceVersion.bucket())
        .objectKey(evidenceVersion.objectKey())
        .mediaType(evidenceVersion.mediaType())
        .sizeBytes(evidenceVersion.sizeBytes())
        .sha256Checksum(evidenceVersion.sha256Checksum())
        .uploadedAt(OffsetDateTime.ofInstant(evidenceVersion.uploadedAt(), ZoneOffset.UTC))
        .uploadedBy(evidenceVersion.uploadedBy())
        .createdAt(OffsetDateTime.ofInstant(evidenceVersion.createdAt(), ZoneOffset.UTC))
        .createdBy(evidenceVersion.createdBy());
  }

  public EvidenceClassification toDomainClassification(EvidenceClassificationValue value) {
    return EvidenceClassification.valueOf(value.toString());
  }

  public EvidenceClassificationValue toApiClassification(EvidenceClassification classification) {
    return EvidenceClassificationValue.fromValue(classification.name());
  }

  public EvidenceStorageStatusValue toApiStorageStatus(EvidenceStorageStatus storageStatus) {
    return EvidenceStorageStatusValue.fromValue(storageStatus.name());
  }
}
