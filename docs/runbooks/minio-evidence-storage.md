# MinIO Evidence Storage Runbook

Use this runbook when evidence upload, finalize, or download flow fails in local or lower environments.

## Symptoms

- `POST /api/v1/cases/{caseId}/evidence/upload-sessions` returns `503`.
- Upload to the returned presigned URL fails.
- `POST /api/v1/evidence/{evidenceId}/versions/finalize` returns checksum, media type, size, or missing-object conflict.
- `POST /api/v1/evidence/{evidenceId}/download-sessions` returns `403` or `503`.

## Checks

1. Confirm MinIO is healthy.
   - `docker compose ps minio`
   - `docker compose logs minio`
2. Confirm the evidence bucket exists.
   - `make minio-init`
3. Confirm application config is aligned.
   - `MINIO_ENDPOINT`
   - `MINIO_ACCESS_KEY`
   - `MINIO_SECRET_KEY`
   - `MINIO_EVIDENCE_BUCKET`
   - `EVIDENCE_UPLOAD_URL_TTL`
   - `EVIDENCE_DOWNLOAD_URL_TTL`
4. Confirm the client uploaded with the same `Content-Type` and binary payload used when the upload session was created.
5. Confirm the SHA-256 sent in the upload-session request matches the uploaded object exactly.

## Expected behavior

- Upload-session creation should return `201` plus a presigned PUT URL.
- Finalize should reject mismatched size, media type, checksum, expired session, stale upload session, or missing object with a conflict error envelope.
- Unauthorized download-session creation should return `403` and write `EvidenceDownloadDenied` audit evidence.

## Operator actions

- If the bucket is missing, run `make minio-init`.
- If upload used the wrong `Content-Type`, discard that upload session and request a new one.
- If checksum mismatch happens, recompute SHA-256 from the exact uploaded bytes and retry with a new upload session.
- If the object is missing, inspect MinIO logs and re-upload through a fresh session.
- If `503` persists, inspect MinIO availability and application connectivity before retrying.
