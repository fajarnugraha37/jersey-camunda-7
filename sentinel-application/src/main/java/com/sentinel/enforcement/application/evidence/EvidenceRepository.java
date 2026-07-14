package com.sentinel.enforcement.application.evidence;

import com.sentinel.enforcement.domain.evidence.Evidence;
import com.sentinel.enforcement.domain.evidence.EvidenceUploadSession;
import com.sentinel.enforcement.domain.evidence.EvidenceVersion;
import java.util.Optional;
import java.util.UUID;

public interface EvidenceRepository {

  void prepareNewEvidenceUpload(Evidence evidence, EvidenceUploadSession uploadSession);

  void prepareExistingEvidenceUpload(EvidenceUploadSession uploadSession);

  Optional<Evidence> findEvidenceById(UUID evidenceId);

  Optional<EvidenceVersion> findLatestVersion(UUID evidenceId);

  Optional<EvidenceUploadSession> findUploadSessionById(UUID uploadSessionId);

  void finalizeUpload(
      Evidence evidence, EvidenceVersion evidenceVersion, EvidenceUploadSession uploadSession);
}
