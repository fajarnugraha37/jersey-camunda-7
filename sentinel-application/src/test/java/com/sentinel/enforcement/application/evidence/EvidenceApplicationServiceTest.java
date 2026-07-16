package com.sentinel.enforcement.application.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sentinel.enforcement.application.casefile.CaseRepository;
import com.sentinel.enforcement.application.messaging.ApplicationTransactionManager;
import com.sentinel.enforcement.application.messaging.OutboxEvent;
import com.sentinel.enforcement.application.messaging.OutboxRepository;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.security.AuthorizationContext;
import com.sentinel.enforcement.application.security.AuthorizationDeniedException;
import com.sentinel.enforcement.application.security.AuthorizationService;
import com.sentinel.enforcement.application.security.Permission;
import com.sentinel.enforcement.domain.casefile.AuditEvent;
import com.sentinel.enforcement.domain.casefile.CaseAssignment;
import com.sentinel.enforcement.domain.casefile.CaseClassification;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import com.sentinel.enforcement.domain.casefile.CaseStatusHistoryEntry;
import com.sentinel.enforcement.domain.evidence.Evidence;
import com.sentinel.enforcement.domain.evidence.EvidenceClassification;
import com.sentinel.enforcement.domain.evidence.EvidenceConflictException;
import com.sentinel.enforcement.domain.evidence.EvidenceStorageStatus;
import com.sentinel.enforcement.domain.evidence.EvidenceUploadSession;
import com.sentinel.enforcement.domain.evidence.EvidenceVersion;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceApplicationServiceTest {

  @Test
  void createUploadSessionPreparesNewEvidenceAndPresignedUrl() {
    InMemoryCaseRepository caseRepository = new InMemoryCaseRepository();
    InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository();
    InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
    CapturingAuthorizationService authorizationService = new CapturingAuthorizationService();
    FakeEvidenceStoragePort storagePort = new FakeEvidenceStoragePort();
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T10:15:30Z"), ZoneOffset.UTC);
    EvidenceApplicationService service =
        new EvidenceApplicationService(
            authorizationService,
            new ImmediateTransactionManager(),
            caseRepository,
            evidenceRepository,
            outboxRepository,
            storagePort,
            clock,
            "sentinel-evidence",
            Duration.ofMinutes(15),
            Duration.ofMinutes(10));
    ApplicationActor actor =
        new ApplicationActor(
            "subject-1", "investigator-jkt", Set.of("INVESTIGATOR"), Set.of("JKT"));
    CaseRecord caseRecord = sampleCase("investigator-jkt");
    caseRepository.caseById.put(caseRecord.id(), caseRecord);

    PreparedEvidenceUploadSession preparedSession =
        service.createUploadSession(
            actor,
            caseRecord.id(),
            new CreateEvidenceUploadSessionCommand(
                null,
                "Gift ledger export",
                EvidenceClassification.CONFIDENTIAL,
                "ledger.csv",
                "text/csv",
                128L,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "corr-1",
                "127.0.0.1"));

    assertNotNull(preparedSession.evidenceId());
    assertNotNull(preparedSession.uploadSessionId());
    assertEquals(Permission.CREATE_EVIDENCE_UPLOAD_SESSION, authorizationService.permission);
    assertEquals("sentinel-evidence", evidenceRepository.savedUploadSession.bucket());
    assertEquals("https://storage.local/upload", preparedSession.uploadUrl());
    assertSame(
        evidenceRepository.savedEvidence,
        evidenceRepository.evidenceById.get(preparedSession.evidenceId()));
  }

  @Test
  void finalizeEvidenceVersionRejectsChecksumMismatch() {
    InMemoryCaseRepository caseRepository = new InMemoryCaseRepository();
    InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository();
    InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
    CapturingAuthorizationService authorizationService = new CapturingAuthorizationService();
    FakeEvidenceStoragePort storagePort = new FakeEvidenceStoragePort();
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T10:15:30Z"), ZoneOffset.UTC);
    EvidenceApplicationService service =
        new EvidenceApplicationService(
            authorizationService,
            new ImmediateTransactionManager(),
            caseRepository,
            evidenceRepository,
            outboxRepository,
            storagePort,
            clock,
            "sentinel-evidence",
            Duration.ofMinutes(15),
            Duration.ofMinutes(10));
    ApplicationActor actor =
        new ApplicationActor(
            "subject-2", "investigator-jkt", Set.of("INVESTIGATOR"), Set.of("JKT"));
    CaseRecord caseRecord = sampleCase("investigator-jkt");
    caseRepository.caseById.put(caseRecord.id(), caseRecord);
    UUID evidenceId = UUID.randomUUID();
    Evidence evidence =
        new Evidence(
            evidenceId,
            caseRecord.id(),
            "Gift ledger export",
            EvidenceClassification.CONFIDENTIAL,
            EvidenceStorageStatus.PENDING_UPLOAD,
            0,
            Instant.parse("2026-07-14T09:00:00Z"),
            "investigator-jkt",
            Instant.parse("2026-07-14T09:00:00Z"),
            "investigator-jkt",
            0L);
    EvidenceUploadSession uploadSession =
        EvidenceUploadSession.create(
            UUID.randomUUID(),
            caseRecord.id(),
            evidenceId,
            1,
            "ledger.csv",
            "generated.csv",
            "sentinel-evidence",
            "/JKT/" + caseRecord.id() + "/" + evidenceId + "/1/generated.csv",
            "text/csv",
            11L,
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            EvidenceClassification.CONFIDENTIAL,
            Instant.parse("2026-07-14T09:10:00Z"),
            Instant.parse("2026-07-14T10:25:30Z"),
            "investigator-jkt");
    evidenceRepository.evidenceById.put(evidenceId, evidence);
    evidenceRepository.uploadSessionById.put(uploadSession.id(), uploadSession);
    storagePort.objectBytes = "hello-world".getBytes(StandardCharsets.UTF_8);
    storagePort.storedObject = new EvidenceStoragePort.StoredEvidenceObject(11L, "text/csv");

    EvidenceConflictException conflict =
        assertThrows(
            EvidenceConflictException.class,
            () ->
                service.finalizeEvidenceVersion(
                    actor,
                    evidenceId,
                    new FinalizeEvidenceVersionCommand(uploadSession.id(), "corr-1", "127.0.0.1")));

    assertEquals("EVIDENCE_CHECKSUM_MISMATCH", conflict.code());
  }

  @Test
  void deniedDownloadIsAuditedBeforeAuthorizationErrorReturns() {
    InMemoryCaseRepository caseRepository = new InMemoryCaseRepository();
    InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository();
    InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
    CapturingAuthorizationService authorizationService = new CapturingAuthorizationService();
    authorizationService.deniedPermission = Permission.CREATE_EVIDENCE_DOWNLOAD_SESSION;
    FakeEvidenceStoragePort storagePort = new FakeEvidenceStoragePort();
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T10:15:30Z"), ZoneOffset.UTC);
    EvidenceApplicationService service =
        new EvidenceApplicationService(
            authorizationService,
            new ImmediateTransactionManager(),
            caseRepository,
            evidenceRepository,
            outboxRepository,
            storagePort,
            clock,
            "sentinel-evidence",
            Duration.ofMinutes(15),
            Duration.ofMinutes(10));
    ApplicationActor actor =
        new ApplicationActor(
            "subject-3", "intake-jkt", Set.of("CASE_INTAKE_OFFICER"), Set.of("JKT"));
    CaseRecord caseRecord = sampleCase("investigator-jkt");
    caseRepository.caseById.put(caseRecord.id(), caseRecord);
    UUID evidenceId = UUID.randomUUID();
    evidenceRepository.evidenceById.put(
        evidenceId,
        new Evidence(
            evidenceId,
            caseRecord.id(),
            "Gift ledger export",
            EvidenceClassification.CONFIDENTIAL,
            EvidenceStorageStatus.ACTIVE,
            1,
            Instant.parse("2026-07-14T09:00:00Z"),
            "investigator-jkt",
            Instant.parse("2026-07-14T09:30:00Z"),
            "investigator-jkt",
            1L));
    evidenceRepository.latestVersionByEvidenceId.put(
        evidenceId,
        new EvidenceVersion(
            UUID.randomUUID(),
            evidenceId,
            1,
            "ledger.csv",
            "generated.csv",
            "sentinel-evidence",
            "/JKT/" + caseRecord.id() + "/" + evidenceId + "/1/generated.csv",
            "text/csv",
            11L,
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            Instant.parse("2026-07-14T09:30:00Z"),
            "investigator-jkt",
            Instant.parse("2026-07-14T09:30:00Z"),
            "investigator-jkt"));

    assertThrows(
        AuthorizationDeniedException.class,
        () ->
            service.createDownloadSession(
                actor,
                evidenceId,
                new CreateEvidenceDownloadSessionCommand(
                    "Need to inspect file.", "corr-1", "127.0.0.1")));

    assertEquals(1, caseRepository.auditEvents.size());
    assertEquals("EvidenceDownloadDenied", caseRepository.auditEvents.get(0).eventType());
  }

  @Test
  void finalizeEvidenceVersionEnqueuesLifecycleEventAlongsideAuditAndVersionUpdate() {
    InMemoryCaseRepository caseRepository = new InMemoryCaseRepository();
    InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository();
    InMemoryOutboxRepository outboxRepository = new InMemoryOutboxRepository();
    CapturingAuthorizationService authorizationService = new CapturingAuthorizationService();
    FakeEvidenceStoragePort storagePort = new FakeEvidenceStoragePort();
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T10:15:30Z"), ZoneOffset.UTC);
    EvidenceApplicationService service =
        new EvidenceApplicationService(
            authorizationService,
            new ImmediateTransactionManager(),
            caseRepository,
            evidenceRepository,
            outboxRepository,
            storagePort,
            clock,
            "sentinel-evidence",
            Duration.ofMinutes(15),
            Duration.ofMinutes(10));
    ApplicationActor actor =
        new ApplicationActor(
            "subject-4", "investigator-jkt", Set.of("INVESTIGATOR"), Set.of("JKT"));
    CaseRecord caseRecord = sampleCase("investigator-jkt");
    caseRepository.caseById.put(caseRecord.id(), caseRecord);
    UUID evidenceId = UUID.randomUUID();
    byte[] content = "hello-world".getBytes(StandardCharsets.UTF_8);
    String checksum = "afa27b44d43b02a9fea41d13cedc2e4016cfcf87c5dbf990e593669aa8ce286d";
    Evidence evidence =
        new Evidence(
            evidenceId,
            caseRecord.id(),
            "Gift ledger export",
            EvidenceClassification.CONFIDENTIAL,
            EvidenceStorageStatus.PENDING_UPLOAD,
            0,
            Instant.parse("2026-07-14T09:00:00Z"),
            "investigator-jkt",
            Instant.parse("2026-07-14T09:00:00Z"),
            "investigator-jkt",
            0L);
    EvidenceUploadSession uploadSession =
        EvidenceUploadSession.create(
            UUID.randomUUID(),
            caseRecord.id(),
            evidenceId,
            1,
            "ledger.csv",
            "generated.csv",
            "sentinel-evidence",
            "/JKT/" + caseRecord.id() + "/" + evidenceId + "/1/generated.csv",
            "text/plain",
            (long) content.length,
            checksum,
            EvidenceClassification.CONFIDENTIAL,
            Instant.parse("2026-07-14T09:10:00Z"),
            Instant.parse("2026-07-14T10:25:30Z"),
            "investigator-jkt");
    evidenceRepository.evidenceById.put(evidenceId, evidence);
    evidenceRepository.uploadSessionById.put(uploadSession.id(), uploadSession);
    storagePort.objectBytes = content;
    storagePort.storedObject =
        new EvidenceStoragePort.StoredEvidenceObject((long) content.length, "text/plain");

    EvidenceDetailsView finalized =
        service.finalizeEvidenceVersion(
            actor,
            evidenceId,
            new FinalizeEvidenceVersionCommand(uploadSession.id(), "corr-2", "127.0.0.1"));

    assertEquals(1, finalized.latestVersion().versionNumber());
    assertEquals(1, caseRepository.auditEvents.size());
    assertEquals(2, outboxRepository.events.size());
    assertEquals("AuditIntegrated", outboxRepository.events.get(0).envelope().eventType());
    assertEquals("EvidenceVersionFinalized", outboxRepository.events.get(1).envelope().eventType());
  }

  private static CaseRecord sampleCase(String assigneeUserId) {
    return new CaseRecord(
        UUID.randomUUID(),
        "JKT-ENF-2026-00000001",
        UUID.randomUUID(),
        "Gift disclosure case",
        "Triaged into case.",
        "JKT",
        CaseClassification.CONFIDENTIAL,
        CaseStatus.UNDER_INVESTIGATION,
        "JKT-UNIT-1",
        assigneeUserId,
        Instant.parse("2026-07-14T09:00:00Z"),
        "triage-jkt",
        Instant.parse("2026-07-14T09:30:00Z"),
        "triage-jkt",
        1L);
  }

  private static final class InMemoryCaseRepository implements CaseRepository {
    private final Map<UUID, CaseRecord> caseById = new HashMap<>();
    private final List<AuditEvent> auditEvents = new java.util.ArrayList<>();

    @Override
    public String nextCaseNumber(String jurisdictionCode, int year) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void save(
        CaseRecord caseRecord, CaseStatusHistoryEntry statusHistoryEntry, AuditEvent auditEvent) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<CaseRecord> findById(UUID caseId) {
      return Optional.ofNullable(caseById.get(caseId));
    }

    @Override
    public List<CaseRecord> findByIds(Set<UUID> caseIds) {
      return List.of();
    }

    @Override
    public List<CaseRecord> findPage(
        com.sentinel.enforcement.application.casefile.CasePageRequest pageRequest) {
      return List.of();
    }

    @Override
    public void assign(
        CaseRecord caseRecord, CaseAssignment caseAssignment, AuditEvent auditEvent) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void transition(
        CaseRecord caseRecord, CaseStatusHistoryEntry statusHistoryEntry, AuditEvent auditEvent) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<AuditEvent> findAuditEventsPage(
        com.sentinel.enforcement.application.casefile.AuditEventPageRequest pageRequest) {
      return List.of();
    }

    @Override
    public void appendAuditEvent(AuditEvent auditEvent) {
      auditEvents.add(auditEvent);
    }
  }

  private static final class InMemoryEvidenceRepository implements EvidenceRepository {
    private final Map<UUID, Evidence> evidenceById = new HashMap<>();
    private final Map<UUID, EvidenceVersion> latestVersionByEvidenceId = new HashMap<>();
    private final Map<UUID, EvidenceUploadSession> uploadSessionById = new HashMap<>();
    private Evidence savedEvidence;
    private EvidenceUploadSession savedUploadSession;

    @Override
    public void prepareNewEvidenceUpload(Evidence evidence, EvidenceUploadSession uploadSession) {
      savedEvidence = evidence;
      savedUploadSession = uploadSession;
      evidenceById.put(evidence.id(), evidence);
      uploadSessionById.put(uploadSession.id(), uploadSession);
    }

    @Override
    public void prepareExistingEvidenceUpload(EvidenceUploadSession uploadSession) {
      savedUploadSession = uploadSession;
      uploadSessionById.put(uploadSession.id(), uploadSession);
    }

    @Override
    public Optional<Evidence> findEvidenceById(UUID evidenceId) {
      return Optional.ofNullable(evidenceById.get(evidenceId));
    }

    @Override
    public Optional<EvidenceVersion> findLatestVersion(UUID evidenceId) {
      return Optional.ofNullable(latestVersionByEvidenceId.get(evidenceId));
    }

    @Override
    public Optional<EvidenceUploadSession> findUploadSessionById(UUID uploadSessionId) {
      return Optional.ofNullable(uploadSessionById.get(uploadSessionId));
    }

    @Override
    public void finalizeUpload(
        Evidence evidence, EvidenceVersion evidenceVersion, EvidenceUploadSession uploadSession) {
      evidenceById.put(evidence.id(), evidence);
      latestVersionByEvidenceId.put(evidence.id(), evidenceVersion);
      uploadSessionById.put(uploadSession.id(), uploadSession);
    }
  }

  private static final class CapturingAuthorizationService implements AuthorizationService {
    private Permission permission;
    private Permission deniedPermission;

    @Override
    public void requirePermission(
        ApplicationActor actor, Permission permission, AuthorizationContext authorizationContext) {
      this.permission = permission;
      if (permission == deniedPermission) {
        throw new AuthorizationDeniedException("Forbidden");
      }
    }
  }

  private static final class FakeEvidenceStoragePort implements EvidenceStoragePort {
    private StoredEvidenceObject storedObject =
        new StoredEvidenceObject(0L, "application/octet-stream");
    private byte[] objectBytes = new byte[0];

    @Override
    public String createPresignedUploadUrl(String bucket, String objectKey, Duration ttl) {
      return "https://storage.local/upload";
    }

    @Override
    public String createPresignedDownloadUrl(String bucket, String objectKey, Duration ttl) {
      return "https://storage.local/download";
    }

    @Override
    public StoredEvidenceObject statObject(String bucket, String objectKey) {
      return storedObject;
    }

    @Override
    public InputStream getObjectStream(String bucket, String objectKey) {
      return new ByteArrayInputStream(objectBytes);
    }
  }

  private static final class ImmediateTransactionManager implements ApplicationTransactionManager {
    @Override
    public <T> T required(java.util.function.Supplier<T> work) {
      return work.get();
    }
  }

  private static final class InMemoryOutboxRepository implements OutboxRepository {
    private final List<OutboxEvent> events = new java.util.ArrayList<>();

    @Override
    public void enqueue(OutboxEvent outboxEvent) {
      events.add(outboxEvent);
    }

    @Override
    public List<OutboxEvent> claimPending(
        String leaseOwner, Instant now, Duration leaseDuration, int batchSize, String updatedBy) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void markPublished(UUID eventId, Instant publishedAt, String updatedBy) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void releaseForRetry(
        UUID eventId, Instant now, Instant nextAttemptAt, String lastError, String updatedBy) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long countPending() {
      return events.size();
    }
  }
}
