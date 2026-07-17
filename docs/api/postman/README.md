# Sentinel API Postman Guide

Guide ini mendampingi collection [sentinel-enforcement-platform.postman_collection.json](./sentinel-enforcement-platform.postman_collection.json) untuk mencoba seluruh API lokal Sentinel lewat Postman, termasuk login ke Keycloak.

## 1. Prasyarat Local Runtime

Jalankan stack lokal terlebih dahulu:

```bash
make bootstrap
make up
make migrate
make seed
make smoke-test
```

Gunakan `localhost` secara konsisten untuk aplikasi dan Keycloak. Jangan campur `localhost` dengan `127.0.0.1` karena issuer JWT diverifikasi exact-match.

- App: `http://localhost:8080`
- Keycloak: `http://localhost:8081`
- Realm: `sentinel`
- Client ID: `sentinel-api`

## 2. Cara Pakai Collection

Import file berikut ke Postman:

- `docs/api/postman/sentinel-enforcement-platform.postman_collection.json`

Collection ini sudah punya variable internal, jadi Anda tidak perlu membuat Postman environment terpisah untuk trial dasar.

Variable penting:

- `accessToken`, `refreshToken`: diisi otomatis setelah login.
- `reportId`, `reportVersion`: diisi otomatis dari response report.
- `caseId`, `caseVersion`, `caseNumber`: diisi otomatis dari response case.
- `relatedCaseId`, `relatedCaseNumber`: dipakai untuk flow relationship antar case.
- `recommendationId`, `decisionId`, `appealId`: diisi otomatis dari response masing-masing endpoint create.
- `evidenceId`, `uploadSessionId`, `uploadUrl`, `downloadUrl`: diisi otomatis dari flow evidence.
- `taskId`: diisi otomatis dari `GET /api/v1/tasks` bila ada task.
- `workflowCaseId`: diisi otomatis dari list reconciliation atau fallback ke `caseId`.
- `maintenanceRunId`: diisi otomatis dari endpoint operator batch overdue obligation.

Catatan penting:

- Field `expectedVersion` harus selalu memakai versi terbaru dari entity terkait.
- Bila request update gagal `409`, biasanya `expectedVersion` Anda stale atau state entity tidak lagi cocok.

## 3. Login dan User Default

Semua user default memakai password `sentinel`.

User utama yang sudah disiapkan di collection:

- `intake-jkt`: `CASE_INTAKE_OFFICER`
- `triage-jkt`: `TRIAGE_OFFICER`
- `investigator-jkt`: `INVESTIGATOR`
- `reviewer-jkt`: `CASE_REVIEWER`
- `decision-jkt`: `DECISION_MAKER`
- `appeal-jkt`: `APPEAL_OFFICER`
- `supervisor-jkt`: `SUPERVISOR`

Ada juga user tambahan yang berguna untuk negative testing:

- `auditor-jkt`
- `system-admin`
- `reviewer-jkt-public`
- `reviewer-jkt-conflicted`
- `supervisor-jkt-unit-2`

Login endpoint yang dipakai collection:

```http
POST http://localhost:8081/realms/sentinel/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

client_id=sentinel-api
grant_type=password
username=<username>
password=sentinel
```

Request `Login (activeUsername)` akan memakai variable `activeUsername` dan `activePassword`. Request login per-role di folder `Authentication` akan mengganti `activeUsername` dulu, lalu mengambil token baru.

## 4. Flow Minimum End-to-End

Flow ini adalah urutan paling praktis untuk mencoba lifecycle utama.

### 4.1 Health Check

1. Jalankan `Health / GET /health`

Tujuan:

- memastikan aplikasi hidup sebelum login dan sebelum menguji API domain.

### 4.2 Report Intake

1. Login sebagai `intake-jkt`
2. Jalankan `Reports / Create Report`
3. Jalankan `Reports / Get Report`

Endpoint:

- `POST /api/v1/reports`
- `GET /api/v1/reports/{reportId}`

Request body create report:

```json
{
  "title": "Potential gift disclosure violation",
  "description": "Potential violation involving unreported gifts from a vendor.",
  "jurisdictionCode": "JKT",
  "reporterName": "Analyst A"
}
```

Response penting:

- `id` -> disimpan ke `reportId`
- `version` -> disimpan ke `reportVersion`

### 4.3 Triage Report

1. Login sebagai `triage-jkt`
2. Jalankan `Reports / Triage Report`

Endpoint:

- `POST /api/v1/reports/{reportId}/triage`

Body:

```json
{
  "expectedVersion": 0,
  "reason": "Report accepted for case creation."
}
```

Gunakan `reportVersion` terbaru dari response sebelumnya.

### 4.4 Create dan Inspect Case

1. Login sebagai `triage-jkt`
2. Jalankan `Cases / Create Case`
3. Jalankan `Cases / Get Case`
4. Opsional: `Cases / List Cases`

Endpoint:

- `POST /api/v1/cases`
- `GET /api/v1/cases/{caseId}`
- `GET /api/v1/cases`

Body create case:

```json
{
  "reportId": "{{reportId}}",
  "title": "Gift disclosure enforcement case",
  "summary": "Open a case from the triaged report for investigation.",
  "classification": "CONFIDENTIAL"
}
```

Response penting:

- `id` -> `caseId`
- `version` -> `caseVersion`
- `caseNumber` -> `caseNumber`

Query penting untuk `GET /api/v1/cases`:

- `q`
- `searchField`
- `searchValue`
- `status`
- `classification`
- `assignedUnitId`
- `assigneeUserId`
- `createdBy`
- `reportId`
- `sortBy`
- `sortDirection`
- `limit`
- `cursor`

### 4.5 Assign Case

1. Login sebagai `triage-jkt` atau role yang diizinkan oleh policy lokal
2. Jalankan `Cases / Assign Case`

Endpoint:

- `POST /api/v1/cases/{caseId}/assignments`

Body:

```json
{
  "assignedUnitId": "JKT-UNIT-1",
  "assigneeUserId": "investigator-jkt",
  "expectedVersion": 0,
  "reason": "Assign investigator after triage."
}
```

Pastikan `expectedVersion` memakai `caseVersion` terbaru.

### 4.6 Case Relationship Flow

Flow ini dipakai untuk mencoba reference recursive CTE pada lineage case.

1. Buat case pertama seperti pada langkah `4.4`
2. Salin `caseId` case pertama ke variable collection `relatedCaseId`
3. Buat case kedua, sehingga variable `caseId` sekarang menunjuk source case yang baru
4. Login sebagai `triage-jkt`
5. Jalankan `Cases / Create Case Relationship`
6. Jalankan `Cases / List Case Relationships`

Endpoint:

- `POST /api/v1/cases/{caseId}/relationships`
- `GET /api/v1/cases/{caseId}/relationships`

Body create relationship:

```json
{
  "relatedCaseId": "{{relatedCaseId}}",
  "relationshipType": "MERGE",
  "direction": "PARENT_OF",
  "relationshipReason": "Administrative merge discovered during triage review."
}
```

Query penting untuk list relationship:

- `direction=ANCESTORS|DESCENDANTS|BOTH`
- `maxDepth`
- `relationshipType`

Catatan:

- `relationshipType` yang valid saat ini: `MERGE`, `DERIVATION`, `SPLIT`
- `direction` create yang valid: `PARENT_OF`, `CHILD_OF`
- Jika edge membentuk cycle transitif, API akan mengembalikan `409 CASE_RELATIONSHIP_CYCLE`

### 4.7 Transition Case dan Audit Trail

Gunakan flow ini bila Anda ingin menguji perubahan state case atau melihat audit event pada case tertentu.

1. Login sebagai actor yang berwenang terhadap case tersebut
2. Jalankan `Cases / Transition Case`
3. Jalankan `Cases / Get Case`
4. Jalankan `Cases / Get Case Audit Events`

Endpoint:

- `POST /api/v1/cases/{caseId}/transitions`
- `GET /api/v1/cases/{caseId}/audit-events`

Body transition:

```json
{
  "targetStatus": "UNDER_TRIAGE",
  "expectedVersion": 0,
  "reason": "Manual transition example from Postman."
}
```

Catatan:

- Gunakan `targetStatus` yang memang valid dari state case saat ini.
- Kalau salah urutan state atau `expectedVersion` stale, endpoint ini akan mengembalikan `409`.

### 4.8 Evidence Flow

1. Login sebagai `investigator-jkt`
2. Jalankan `Evidence / Create Evidence Upload Session`
3. Upload file ke `uploadUrl` yang dikembalikan response
4. Jalankan `Evidence / Finalize Evidence Version`
5. Jalankan `Evidence / Get Evidence`
6. Jalankan `Evidence / Create Evidence Download Session`

Endpoint API:

- `POST /api/v1/cases/{caseId}/evidence/upload-sessions`
- `POST /api/v1/evidence/{evidenceId}/versions/finalize`
- `GET /api/v1/evidence/{evidenceId}`
- `POST /api/v1/evidence/{evidenceId}/download-sessions`

Body create upload session:

```json
{
  "title": "Bank transfer receipt",
  "classification": "CONFIDENTIAL",
  "originalFilename": "receipt.pdf",
  "mediaType": "application/pdf",
  "sizeBytes": 1024,
  "sha256Checksum": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
}
```

Body finalize:

```json
{
  "uploadSessionId": "{{uploadSessionId}}"
}
```

Body create download session:

```json
{
  "reason": "Review evidence during investigation."
}
```

Catatan:

- Langkah upload file ke `uploadUrl` bukan API aplikasi Sentinel, melainkan upload langsung ke storage presigned URL.
- Untuk trial cepat, gunakan file kecil yang checksum SHA-256-nya Anda hitung sendiri bila ingin finalize benar-benar sukses.
- Variable `sampleEvidenceSha256` di collection hanyalah placeholder.

### 4.9 Recommendation Flow

1. Login sebagai `investigator-jkt`
2. Jalankan `Recommendations / Create Recommendation`
3. Jalankan `Recommendations / Submit Recommendation`
4. Login sebagai `reviewer-jkt`
5. Jalankan `Recommendations / Review Recommendation`

Endpoint:

- `POST /api/v1/cases/{caseId}/recommendations`
- `POST /api/v1/recommendations/{recommendationId}/submit`
- `POST /api/v1/recommendations/{recommendationId}/reviews`

Body review:

```json
{
  "reviewSummary": "Recommendation approved and ready for decision."
}
```

Policy penting:

- Maker-checker berlaku. User yang membuat recommendation tidak boleh menjadi approver final.

### 4.10 Decision Flow

1. Login sebagai `decision-jkt`
2. Jalankan `Decisions / Create Decision`
3. Jalankan `Decisions / Approve Decision`
4. Jalankan `Decisions / Publish Decision`

Endpoint:

- `POST /api/v1/cases/{caseId}/decisions`
- `POST /api/v1/decisions/{decisionId}/approve`
- `POST /api/v1/decisions/{decisionId}/publish`

Body create decision:

```json
{
  "title": "Decision to sanction",
  "summary": "Decision prepared for publication.",
  "violationProven": true,
  "sanctionSummary": "Administrative fine and reporting obligation.",
  "obligationTitle": "Pay administrative fine",
  "obligationDetails": "Transfer the assessed fine within the prescribed deadline.",
  "obligationDueDate": "2026-08-20",
  "appealDeadline": "2026-08-15"
}
```

Catatan:

- `appealDeadline` harus berupa tanggal valid setelah publication window yang sesuai dengan policy aplikasi.
- Setelah decision dipublish, state dan flow case bisa berubah sehingga request lama dengan `expectedVersion` lama dapat gagal.

### 4.11 Appeal Flow

1. Login sebagai `appeal-jkt`
2. Jalankan `Appeals / Create Appeal`
3. Jalankan `Appeals / Decide Appeal`

Endpoint:

- `POST /api/v1/decisions/{decisionId}/appeals`
- `POST /api/v1/appeals/{appealId}/decisions`

Body create appeal:

```json
{
  "rationale": "New mitigating evidence should be considered.",
  "submittedAt": "2026-07-16T10:00:00Z",
  "supervisorOverride": false,
  "supervisorOverrideReason": null
}
```

Body decide appeal:

```json
{
  "outcome": "DENIED",
  "summary": "Appeal denied after review."
}
```

### 4.12 Tasks Flow

Gunakan flow ini untuk melihat user task workflow aktif.

1. Login sebagai user yang relevan, misalnya `investigator-jkt`, `reviewer-jkt`, atau `decision-jkt`
2. Jalankan `Tasks / List Tasks`
3. Bila ada task, collection akan menyimpan task pertama ke `taskId`
4. Jalankan `Tasks / Claim Task`
5. Jalankan `Tasks / Complete Task`

Endpoint:

- `GET /api/v1/tasks`
- `POST /api/v1/tasks/{taskId}/claim`
- `POST /api/v1/tasks/{taskId}/complete`

Query penting pada list task:

- `q`
- `searchField`
- `searchValue`
- `caseId`
- `assigneeUserId`
- `state`
- `sortBy`
- `sortDirection`
- `limit`
- `cursor`

`POST /complete` tidak membutuhkan body dan sukses mengembalikan `204 No Content`.

### 4.13 Workflow Reconciliation

Flow ini untuk supervisor/operator saat domain state dan workflow state mismatch.

1. Login sebagai `supervisor-jkt`
2. Jalankan `Workflow Reconciliation / List Workflow Reconciliation Issues`
3. Jalankan `Workflow Reconciliation / Reconcile Workflow Case`

Endpoint:

- `GET /api/v1/workflow-reconciliation`
- `POST /api/v1/workflow-reconciliation/{caseId}/actions`

Body action:

```json
{
  "action": "AUTO_REPAIR",
  "reason": "Repair workflow mismatch from Postman."
}
```

### 4.14 Maintenance Batch Overdue Obligation

Flow ini dipakai untuk mencoba reference procedure call + table locking untuk operasi maintenance batch.

1. Login sebagai `supervisor-jkt`
2. Jalankan `Operations / Recalculate Overdue Sanction Obligations`
3. Catat `runId` yang dikembalikan response

Endpoint:

- `POST /api/v1/operations/sanction-obligations/recalculate-overdue`

Body:

```json
{
  "effectiveDate": "2026-07-16"
}
```

Response penting:

- `runId` -> disimpan ke `maintenanceRunId`
- `operationName`
- `requestedBy`
- `effectiveDate`
- `resultStatus`
- `affectedRows`

Catatan:

- Endpoint ini memang untuk operator maintenance, bukan hot path user biasa
- Role yang aman dipakai untuk trial lokal: `supervisor-jkt` atau `system-admin`
- Jika ada batch lain yang memegang lock tabel yang sama, request dapat gagal `409 MAINTENANCE_OPERATION_LOCKED`

## 5. Seluruh Endpoint yang Tercakup di Collection

- `POST /realms/sentinel/protocol/openid-connect/token`
- `GET /health`
- `POST /api/v1/reports`
- `GET /api/v1/reports/{reportId}`
- `POST /api/v1/reports/{reportId}/triage`
- `POST /api/v1/cases`
- `GET /api/v1/cases`
- `GET /api/v1/cases/{caseId}`
- `POST /api/v1/cases/{caseId}/assignments`
- `POST /api/v1/cases/{caseId}/relationships`
- `GET /api/v1/cases/{caseId}/relationships`
- `POST /api/v1/cases/{caseId}/transitions`
- `GET /api/v1/cases/{caseId}/audit-events`
- `POST /api/v1/cases/{caseId}/recommendations`
- `POST /api/v1/recommendations/{recommendationId}/submit`
- `POST /api/v1/recommendations/{recommendationId}/reviews`
- `POST /api/v1/cases/{caseId}/decisions`
- `POST /api/v1/decisions/{decisionId}/approve`
- `POST /api/v1/decisions/{decisionId}/publish`
- `POST /api/v1/decisions/{decisionId}/appeals`
- `POST /api/v1/appeals/{appealId}/decisions`
- `POST /api/v1/cases/{caseId}/evidence/upload-sessions`
- `GET /api/v1/evidence/{evidenceId}`
- `POST /api/v1/evidence/{evidenceId}/versions/finalize`
- `POST /api/v1/evidence/{evidenceId}/download-sessions`
- `GET /api/v1/tasks`
- `POST /api/v1/tasks/{taskId}/claim`
- `POST /api/v1/tasks/{taskId}/complete`
- `GET /api/v1/workflow-reconciliation`
- `POST /api/v1/workflow-reconciliation/{caseId}/actions`
- `POST /api/v1/operations/sanction-obligations/recalculate-overdue`

## 6. Troubleshooting Cepat

- `401 Unauthorized`: token belum diambil, expired, atau login ke realm/client salah.
- `403 Forbidden`: role benar belum tentu punya jurisdiction, assignment, classification clearance, atau permission yang cocok.
- `404 Not Found`: variable seperti `reportId`, `caseId`, atau `decisionId` belum terisi atau stale.
- `409 Conflict`: state tidak cocok, maker-checker melanggar policy, `expectedVersion` stale, relationship membentuk cycle, atau batch maintenance bertabrakan dengan lock aktif.
- Evidence finalize gagal: biasanya object belum benar-benar di-upload ke `uploadUrl`, checksum salah, atau metadata file tidak cocok.

Spesifikasi kontrak sumber tetap ada di `docs/api/openapi.yaml`.
