# Evidence Lifecycle

## Flow

```
1. Create Upload Session
   POST /api/v1/cases/{caseId}/evidence/upload-sessions
   → Returns presigned MinIO upload URL + session ID

2. Upload File (client-side, direct to MinIO)
   PUT <presigned-url>
   Content-Type: <media-type>
   Body: <file bytes>

3. Finalize Version
   POST /api/v1/evidence/{evidenceId}/versions/finalize
   → Verifies: object exists, size, media type, SHA-256
   → Activates evidence version

4. Read Evidence
   GET /api/v1/evidence/{evidenceId}
   → Returns metadata + latest version

5. Create Download Session
   POST /api/v1/evidence/{evidenceId}/download-sessions
   → Returns presigned download URL (authorized + audited)
```

---

## Upload Session Creation

```json
POST /api/v1/cases/{caseId}/evidence/upload-sessions
{
  "title": "Financial Document",
  "originalFilename": "report.pdf",
  "mediaType": "application/pdf",
  "sizeBytes": 1048576,
  "sha256Checksum": "abc123...",
  "classification": "CONFIDENTIAL"
}
```

**Response (201):**
```json
{
  "uploadSessionId": "uuid",
  "uploadUrl": "https://minio:9000/sentinel-evidence/...?...",
  "expiresAt": "2026-07-22T00:15:00Z"
}
```

### Object Key Structure
```
evidence/{caseId}/{evidenceId}/{versionNumber}/{generatedFilename}
```

### Presigned URL TTL
- Upload: configured via `EVIDENCE_UPLOAD_URL_TTL` (default: PT15M)
- Download: configured via `EVIDENCE_DOWNLOAD_URL_TTL` (default: PT10M)

---

## Finalization Verification

When the client calls finalize, the server performs server-side verification:

1. **Object existence** — MinIO `statObject()` confirms the file was uploaded
2. **Size match** — MinIO object size matches the declared `sizeBytes`
3. **Media type match** — MinIO content type matches declared `mediaType`
4. **SHA-256 checksum** — Calculated from MinIO object stream, matches declared `sha256Checksum`

If any check fails → `409 Conflict` with code `EVIDENCE_VERIFICATION_FAILED`

### After Successful Finalize
- Evidence status → `ACTIVE`
- `latestVersion` incremented
- `evidence_version` row created (immutable snapshot)
- Outbox event emitted: `evidence.lifecycle.v1` → `EvidenceVersionFinalized`

---

## Download Authorization

Download session creation is **authorized AND audited**:

1. AuthorizationService checks: `CREATE_EVIDENCE_DOWNLOAD_SESSION` permission
2. All 7 axes evaluated (role, jurisdiction, classification, conflict, unit, assignment)
3. On success → presigned download URL returned
4. On DENY → audit event written recording the denied access attempt

---

## Evidence Object Key Structure

```
evidence/{caseId}/{evidenceId}/{versionNumber}/{generatedFilename}
```

Example:
```
evidence/123e4567-e89b-12d3-a456-426614174000/987fcdeb-...
```



---

## Immutability

Once an evidence version is finalized, it is **immutable**:
- The `evidence_version` record cannot be modified
- The MinIO object cannot be deleted (application does not expose delete)
- New versions can be uploaded via new upload sessions (creates next version number)
- Previous versions remain accessible

---

## Bucket Initialization

The `minio-init` service creates the evidence bucket at startup:

```bash
mc alias set sentinel-minio http://minio:9000 ${MINIO_ACCESS_KEY} ${MINIO_SECRET_KEY}
mc mb sentinel-minio/${MINIO_EVIDENCE_BUCKET} --ignore-existing
mc anonymous set private sentinel-minio/${MINIO_EVIDENCE_BUCKET}
```
