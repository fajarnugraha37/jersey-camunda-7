---
type: File Handling
title: File Handling and Formats
description: Evidence file handling through MinIO presigned URLs — no file bytes pass through the application server.
tags: [sentinel, files, evidence, minio, presigned-url, s3]
---

# File Handling and Formats

## Evidence File Storage

All evidence files are stored in **MinIO** (S3-compatible object storage), never on the application filesystem. The application never handles file bytes directly — all uploads and downloads use **presigned URLs**.

Source: `/sentinel-storage/src/main/java/com/sentinel/enforcement/storage/MinioEvidenceStorageAdapter.java`.

## Upload Session Flow

```
Client                     Application                    MinIO
  │                            │                            │
  │  POST .../upload-sessions  │                            │
  │  {originalFilename,        │                            │
  │   sizeBytes,               │                            │
  │   sha256Checksum,          │                            │
  │   mediaType,               │                            │
  │   classification}          │                            │
  │ ──────────────────────►    │                            │
  │                            │  Generate objectKey:       │
  │                            │  /{jurisdiction}/{caseId}/ │
  │                            │   {evidenceId}/{version}/  │
  │                            │   {generatedFilename}      │
  │                            │                            │
  │                            │  createPresignedUploadUrl()│
  │                            │ ────────────────────────►  │
  │                            │   ◄────────────────────────│
  │                            │     Presigned PUT URL      │
  │  201 {evidenceId,          │    (ttl: uploadUrlTtl)     │
  │       uploadSessionId,     │                            │
  │       presignedUrl,        │                            │
  │       expiresAt,           │                            │
  │       objectKey}           │                            │
  │ ◄──────────────────────    │                            │
  │                            │                            │
  │  PUT {presignedUrl}        │                            │
  │  (binary file body)        │                            │
  │ ────────────────────────────────────────────────────►  │
  │                            │                            │
  │  POST .../versions/finalize│                            │
  │  {uploadSessionId}         │                            │
  │ ──────────────────────►    │                            │
  │                            │  statObject() → verify     │
  │                            │   size, mediaType, etag   │
  │                            │ ────────────────────────►  │
  │                            │   ◄────────────────────────│
  │                            │                            │
  │                            │  Calculate SHA-256 from    │
  │                            │   object stream            │
  │                            │  Compare with session      │
  │                            │   sha256Checksum           │
  │                            │                            │
  │  200 {evidenceId,          │                            │
  │       versionNumber,       │                            │
  │       storageStatus}       │                            │
  │ ◄──────────────────────    │                            │
```

### Step-by-step

1. **Client calls** `POST /api/v1/cases/{caseId}/evidence/upload-sessions` with metadata: `originalFilename`, `sizeBytes`, `sha256Checksum`, `mediaType`, `classification`. Source: `CaseEvidenceResource.java` at `/sentinel-api/src/main/java/com/sentinel/enforcement/api/evidence/CaseEvidenceResource.java` line 36.

2. **Application generates an object key** in the format `/{jurisdictionCode}/{caseId}/{evidenceId}/{versionNumber}/{generatedFilename}`. Source: `EvidenceApplicationService.java` at `/sentinel-application/src/main/java/com/sentinel/enforcement/application/evidence/EvidenceApplicationService.java` lines 107–118.

3. **Application creates a presigned PUT URL** via `MinioEvidenceStorageAdapter.createPresignedUploadUrl()` (`/sentinel-storage/src/main/java/com/sentinel/enforcement/storage/MinioEvidenceStorageAdapter.java` lines 65–77). TTL is controlled by `EVIDENCE_UPLOAD_URL_TTL` env var (default `PT15M`).

4. **Client uploads** the file directly to MinIO using the presigned PUT URL. The application never receives the file bytes.

5. **Client finalizes** by calling `POST /api/v1/evidence/{evidenceId}/versions/finalize` with the `uploadSessionId`. Source: `EvidenceResource.java` at `/sentinel-api/src/main/java/com/sentinel/enforcement/api/evidence/EvidenceResource.java` lines 37–51.

6. **Application verifies** the uploaded object:
   - Calls `statObject()` to get `StatObjectResponse` (size, content-type, etag) → `MinioEvidenceStorageAdapter.java` lines 95–113.
   - Checks that `storedObject.sizeBytes()` matches `uploadSession.sizeBytes()` → `EvidenceApplicationService.java` lines 195–198.
   - Checks that `storedObject.mediaType()` matches `uploadSession.mediaType()` → lines 200–203.
   - Computes SHA-256 from the actual stored object stream and compares it with the session's declared `sha256Checksum` → lines 205–209.
   - If all checks pass, persists the evidence version and removes the session.

## Download Session Flow

```
Client                     Application                    MinIO
  │                            │                            │
  │  POST .../download-sessions│                            │
  │ ──────────────────────►    │                            │
  │                            │  createPresignedDownloadUrl│
  │                            │ ────────────────────────►  │
  │                            │   ◄────────────────────────│
  │                            │     Presigned GET URL      │
  │                            │    (ttl: downloadUrlTtl)   │
  │  201 {presignedUrl,        │                            │
  │       expiresAt}           │                            │
  │ ◄──────────────────────    │                            │
  │                            │                            │
  │  GET {presignedUrl}        │                            │
  │ ────────────────────────────────────────────────────►  │
  │   ◄─────────────────────────────────────────────────────│
  │     (file bytes)           │                            │
```

1. **Client calls** `POST /api/v1/evidence/{evidenceId}/download-sessions`. Source: `EvidenceResource.java` lines 63–79.

2. **Application creates a presigned GET URL** with TTL from `EVIDENCE_DOWNLOAD_URL_TTL` (default `PT10M`). Source: `MinioEvidenceStorageAdapter.createPresignedDownloadUrl()` lines 80–92.

3. **Client downloads** directly from MinIO using the presigned URL. The application never serves file bytes.

## Verification on Finalize

The `finalizeEvidenceVersion` method in `EvidenceApplicationService.java` (lines 154–261) performs these verifications:

| Check | Source | Error on mismatch |
|---|---|---|
| Object existence | `statObject()` throws `ErrorResponseException` with `NoSuchKey`/`NoSuchObject` | `EvidenceObjectMissingException` |
| Size match | `storedObject.sizeBytes() != uploadSession.sizeBytes()` | `EVIDENCE_SIZE_MISMATCH` |
| Media type match | Normalized `content-type` comparison | `EVIDENCE_MEDIA_TYPE_MISMATCH` |
| SHA-256 checksum | Streamed hash vs session `sha256Checksum` | `EVIDENCE_CHECKSUM_MISMATCH` |

## No Multipart Uploads

The application server does **not** handle multipart file uploads. There are no `@FormDataParam`, `Part`, or `InputStream` parameters in any JAX-RS resource for file bytes. Files flow directly between the client and MinIO.

## File Formats

### OpenAPI Specification

The REST API contract is defined in `/docs/api/openapi.yaml` (79 KB). All request and response models are generated from this YAML specification via OpenAPI Generator (version 7.12.0, `/pom.xml` line 47). Generated model classes live in `sentinel-api/target/generated-sources/openapi/src/gen/java/com/sentinel/enforcement/api/generated/model/`.

### BPMN Files

Camunda BPMN process definitions are XML files in `/sentinel-workflow/src/main/resources/bpmn/`:

| File | Description |
|---|---|
| `regulatory-enforcement-case.bpmn` | Main case lifecycle process (CASE_MAIN) |
| `decision-appeal-review.bpmn` | Appeal review subprocess (APPEAL) |

Source: `WorkflowModule.java` at `/sentinel-workflow/src/main/java/com/sentinel/enforcement/workflow/WorkflowModule.java` lines 19–20. BPMN is deployed automatically on application startup via embedded Camunda engine (line 52–59).

### Postman Collection

A complete Postman collection is at `/docs/api/postman/sentinel-enforcement-platform.postman_collection.json`. See `/docs/api/postman/README.md` for usage instructions.

## Key Source Files

| File | Role |
|---|---|
| `/sentinel-storage/src/main/java/com/sentinel/enforcement/storage/MinioEvidenceStorageAdapter.java` | MinIO port implementation: presigned URLs, stat, stream |
| `/sentinel-api/src/main/java/com/sentinel/enforcement/api/evidence/EvidenceResource.java` | Evidence download + finalize REST endpoints |
| `/sentinel-api/src/main/java/com/sentinel/enforcement/api/evidence/CaseEvidenceResource.java` | Evidence upload session REST endpoint |
| `/sentinel-application/src/main/java/com/sentinel/enforcement/application/evidence/EvidenceApplicationService.java` | Upload/download session orchestration, finalize verification |
| `/docs/api/openapi.yaml` | REST API contract |
| `/sentinel-workflow/src/main/resources/bpmn/regulatory-enforcement-case.bpmn` | Main BPMN process |
| `/docs/api/postman/sentinel-enforcement-platform.postman_collection.json` | Postman API collection |
