package com.sentinel.enforcement.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sentinel.enforcement.api.generated.model.AssignCaseRequest;
import com.sentinel.enforcement.api.generated.model.CaseResponse;
import com.sentinel.enforcement.api.generated.model.CreateCaseRequest;
import com.sentinel.enforcement.api.generated.model.CreateEvidenceDownloadSessionRequest;
import com.sentinel.enforcement.api.generated.model.CreateEvidenceDownloadSessionResponse;
import com.sentinel.enforcement.api.generated.model.CreateEvidenceUploadSessionRequest;
import com.sentinel.enforcement.api.generated.model.CreateEvidenceUploadSessionResponse;
import com.sentinel.enforcement.api.generated.model.EvidenceClassificationValue;
import com.sentinel.enforcement.api.generated.model.EvidenceResponse;
import com.sentinel.enforcement.api.generated.model.EvidenceStorageStatusValue;
import com.sentinel.enforcement.api.generated.model.ErrorResponse;
import com.sentinel.enforcement.api.generated.model.FinalizeEvidenceVersionRequest;
import com.sentinel.enforcement.api.generated.model.ReportResponse;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceApiIT extends AbstractApiIT {

  @Test
  void uploadFinalizeGetAndDownloadEvidenceWorksEndToEnd() throws Exception {
    CaseResponse assignedCase = createAssignedCaseForInvestigator();
    byte[] content = "gift,amount\nbook,100".getBytes(StandardCharsets.UTF_8);
    String checksum = sha256(content);

    CreateEvidenceUploadSessionResponse uploadSession =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/cases/" + assignedCase.getId() + "/evidence/upload-sessions")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("investigator-jkt"))
            .post(
                Entity.entity(
                    new CreateEvidenceUploadSessionRequest()
                        .title("Gift ledger export")
                        .classification(EvidenceClassificationValue.CONFIDENTIAL)
                        .originalFilename("ledger.csv")
                        .mediaType("text/csv")
                        .sizeBytes((long) content.length)
                        .sha256Checksum(checksum),
                    MediaType.APPLICATION_JSON_TYPE),
                CreateEvidenceUploadSessionResponse.class);

    Response uploadResponse =
        client
            .target(uploadSession.getUploadUrl())
            .request()
            .put(Entity.entity(content, "text/csv"));
    assertEquals(200, uploadResponse.getStatus());
    uploadResponse.close();

    EvidenceResponse finalized =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/evidence/" + uploadSession.getEvidenceId() + "/versions/finalize")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("investigator-jkt"))
            .post(
                Entity.entity(
                    new FinalizeEvidenceVersionRequest()
                        .uploadSessionId(uploadSession.getUploadSessionId()),
                    MediaType.APPLICATION_JSON_TYPE),
                EvidenceResponse.class);

    assertEquals(EvidenceStorageStatusValue.ACTIVE, finalized.getStorageStatus());
    assertEquals(1, finalized.getLatestVersion());
    assertEquals(checksum, finalized.getLatestVersionMetadata().getSha256Checksum());

    EvidenceResponse fetched =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/evidence/" + uploadSession.getEvidenceId())
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("auditor-jkt"))
            .get(EvidenceResponse.class);

    assertEquals(finalized.getId(), fetched.getId());
    assertNotNull(fetched.getLatestVersionMetadata());

    CreateEvidenceDownloadSessionResponse downloadSession =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/evidence/" + uploadSession.getEvidenceId() + "/download-sessions")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("auditor-jkt"))
            .post(
                Entity.entity(
                    new CreateEvidenceDownloadSessionRequest().reason("Audit verification."),
                    MediaType.APPLICATION_JSON_TYPE),
                CreateEvidenceDownloadSessionResponse.class);

    byte[] downloaded =
        client.target(downloadSession.getDownloadUrl()).request().get(byte[].class);

    assertArrayEquals(content, downloaded);
    assertEquals(
        1L,
        countAuditEventsByType(assignedCase.getId(), "EvidenceDownloadSessionCreated"));
  }

  @Test
  void finalizeRejectsChecksumMismatch() throws Exception {
    CaseResponse assignedCase = createAssignedCaseForInvestigator();
    byte[] content = "gift,amount\nbook,100".getBytes(StandardCharsets.UTF_8);

    CreateEvidenceUploadSessionResponse uploadSession =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/cases/" + assignedCase.getId() + "/evidence/upload-sessions")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("investigator-jkt"))
            .post(
                Entity.entity(
                    new CreateEvidenceUploadSessionRequest()
                        .title("Gift ledger export")
                        .classification(EvidenceClassificationValue.CONFIDENTIAL)
                        .originalFilename("ledger.csv")
                        .mediaType("text/csv")
                        .sizeBytes((long) content.length)
                        .sha256Checksum(
                            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
                    MediaType.APPLICATION_JSON_TYPE),
                CreateEvidenceUploadSessionResponse.class);

    Response uploadResponse =
        client
            .target(uploadSession.getUploadUrl())
            .request()
            .put(Entity.entity(content, "text/csv"));
    assertEquals(200, uploadResponse.getStatus());
    uploadResponse.close();

    Response finalizeResponse =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/evidence/" + uploadSession.getEvidenceId() + "/versions/finalize")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("investigator-jkt"))
            .post(
                Entity.entity(
                    new FinalizeEvidenceVersionRequest()
                        .uploadSessionId(uploadSession.getUploadSessionId()),
                    MediaType.APPLICATION_JSON_TYPE));

    ErrorResponse error = finalizeResponse.readEntity(ErrorResponse.class);
    assertEquals(409, finalizeResponse.getStatus());
    assertEquals("EVIDENCE_CHECKSUM_MISMATCH", error.getCode());
  }

  @Test
  void unauthorizedDownloadIsRejectedAndAudited() throws Exception {
    CaseResponse assignedCase = createAssignedCaseForInvestigator();
    byte[] content = "gift,amount\nbook,100".getBytes(StandardCharsets.UTF_8);
    String checksum = sha256(content);

    CreateEvidenceUploadSessionResponse uploadSession =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/cases/" + assignedCase.getId() + "/evidence/upload-sessions")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("investigator-jkt"))
            .post(
                Entity.entity(
                    new CreateEvidenceUploadSessionRequest()
                        .title("Gift ledger export")
                        .classification(EvidenceClassificationValue.CONFIDENTIAL)
                        .originalFilename("ledger.csv")
                        .mediaType("text/csv")
                        .sizeBytes((long) content.length)
                        .sha256Checksum(checksum),
                    MediaType.APPLICATION_JSON_TYPE),
                CreateEvidenceUploadSessionResponse.class);

    Response uploadResponse =
        client
            .target(uploadSession.getUploadUrl())
            .request()
            .put(Entity.entity(content, "text/csv"));
    assertEquals(200, uploadResponse.getStatus());
    uploadResponse.close();

    client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/evidence/" + uploadSession.getEvidenceId() + "/versions/finalize")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("investigator-jkt"))
        .post(
            Entity.entity(
                new FinalizeEvidenceVersionRequest().uploadSessionId(uploadSession.getUploadSessionId()),
                MediaType.APPLICATION_JSON_TYPE),
            EvidenceResponse.class);

    Response deniedResponse =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/evidence/" + uploadSession.getEvidenceId() + "/download-sessions")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("intake-jkt"))
            .post(
                Entity.entity(
                    new CreateEvidenceDownloadSessionRequest().reason("Not allowed."),
                    MediaType.APPLICATION_JSON_TYPE));

    ErrorResponse error = deniedResponse.readEntity(ErrorResponse.class);
    assertEquals(403, deniedResponse.getStatus());
    assertEquals("FORBIDDEN", error.getCode());
    assertEquals(1L, countAuditEventsByType(assignedCase.getId(), "EvidenceDownloadDenied"));
  }

  private static CaseResponse createAssignedCaseForInvestigator() {
    ReportResponse report =
        createTriagedReport(accessToken("intake-jkt"), accessToken("triage-jkt"), "JKT");
    CaseResponse createdCase =
        client
            .target(applicationRuntime.baseUri())
            .path("/api/v1/cases")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("triage-jkt"))
            .post(
                Entity.entity(
                    new CreateCaseRequest()
                        .reportId(report.getId())
                        .title("Evidence case")
                        .summary("Used for evidence API coverage."),
                    MediaType.APPLICATION_JSON_TYPE),
                CaseResponse.class);

    return client
        .target(applicationRuntime.baseUri())
        .path("/api/v1/cases/" + createdCase.getId() + "/assignments")
        .request(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken("triage-jkt"))
        .post(
            Entity.entity(
                new AssignCaseRequest()
                    .assignedUnitId("JKT-EVD-1")
                    .assigneeUserId("investigator-jkt")
                    .expectedVersion(createdCase.getVersion())
                    .reason("Assign investigator for evidence handling."),
                MediaType.APPLICATION_JSON_TYPE),
            CaseResponse.class);
  }

  private static String sha256(byte[] content) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
  }
}
