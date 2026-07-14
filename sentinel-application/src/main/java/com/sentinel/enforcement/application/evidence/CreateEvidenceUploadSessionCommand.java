package com.sentinel.enforcement.application.evidence;

import com.sentinel.enforcement.domain.evidence.EvidenceClassification;
import java.util.UUID;

public record CreateEvidenceUploadSessionCommand(
    UUID existingEvidenceId,
    String title,
    EvidenceClassification classification,
    String originalFilename,
    String mediaType,
    long sizeBytes,
    String sha256Checksum,
    String correlationId,
    String sourceIp) {}
