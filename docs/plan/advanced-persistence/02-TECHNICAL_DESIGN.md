# Advanced Persistence Operations Technical Design

## Purpose

Dokumen ini menerjemahkan plan pada [01-IMPLEMENTATION_PLAN.md](C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/plan/advanced-persistence/01-IMPLEMENTATION_PLAN.md) menjadi desain implementasi detail.

## Common Design Rules

### 1. Transaction API

`ApplicationTransactionManager` saat ini hanya punya `required(Supplier<T>)`. Itu tidak cukup untuk isolation-sensitive flow.

Perubahan desain:

1. tambah overload:
   - `required(TransactionOptions options, Supplier<T> work)`
2. tambah value object `TransactionOptions`
3. field minimum:
   - `isolation`
   - `readOnly`
   - `label`

Default:

1. operasi biasa tetap `READ_COMMITTED`
2. operasi lock-sensitive memilih isolation secara eksplisit

### 2. Persistence Style

Kita pertahankan gaya repo saat ini:

1. multi-line annotation SQL untuk query utama
2. `StatementType.CALLABLE` untuk procedure call bila diperlukan
3. repository adapter tetap menjadi pemilik mapping domain-to-record

### 3. Conflict Handling

Perlu translator untuk SQLState penting:

1. `55P03` lock not available -> `409 RESOURCE_LOCKED`
2. optimistic locking zero-row update -> `409 CONCURRENT_MODIFICATION`
3. constraint violation unik pada assignment aktif atau relationship duplikat -> `409 STATE_CONFLICT`

### 4. Auditability

Setiap operasi baru yang mutating wajib menghasilkan:

1. audit event
2. outbox event bila perubahan business-visible
3. operator evidence untuk batch maintenance

## Operation 1: CTE Query For Workflow Reconciliation

### API

- existing: `GET /api/v1/workflow-reconciliation`

### Use Case

Operator perlu melihat mismatch domain-workflow tanpa menarik semua kandidat lalu memfilter di Java.

### Current Problem

Saat ini `WorkflowReconciliationApplicationService`:

1. load seluruh kandidat dari database
2. mengambil runtime snapshot
3. menghitung issue di memory
4. baru setelah itu filter, sort, dan cursor slicing

Ini bukan reference yang bagus untuk query-heavy read model.

### Target Design

Pindahkan sebagian besar projection ke query CTE bertingkat:

1. `candidate_cases`
   - memuat case dan correlation row dasar
2. `runtime_flags`
   - memetakan status runtime aktif/selesai
3. `issue_projection`
   - menurunkan `issue_type`, `issue_summary`, `available_actions`, dan sort fields

Query shape konseptual:

```sql
WITH candidate_cases AS (...),
runtime_flags AS (...),
issue_projection AS (...)
SELECT ...
FROM issue_projection
WHERE ...
ORDER BY ...
LIMIT ...
```

### Persistence Changes

1. redesign `WorkflowReconciliationMyBatisMapper.findCandidates()` menjadi page-aware query
2. tambahkan request data object khusus untuk filter/cursor/sort
3. repository adapter mengembalikan page hasil SQL, bukan list penuh

### API Contract Impact

Kontrak response dapat tetap kompatibel, tetapi boleh ditambah field read-only berikut bila berguna:

1. `runtimeState`
2. `correlationState`
3. `waitingFor`

### Test Focus

1. pagination di SQL, bukan memory slicing
2. filter `issueType`, search, dan sort tetap benar
3. hasil visible hanya untuk actor yang authorized

## Operation 2: Writable CTE For Case Assignment Rotation

### API

- existing: `POST /api/v1/cases/{caseId}/assignments`

### Use Case

Saat case di-reassign, sistem harus:

1. menutup assignment aktif lama
2. membuat assignment aktif baru
3. memperbarui `case_record.assigned_unit_id` dan `assignee_user_id`

Semua harus atomik.

### Required Schema Changes

Tabel `case_assignment` perlu konsep current assignment.

Kolom baru:

1. `released_at TIMESTAMPTZ NULL`
2. `released_by VARCHAR(...) NULL`
3. `superseded_by_assignment_id UUID NULL`
4. `is_active BOOLEAN NOT NULL DEFAULT TRUE`

Constraint baru:

1. partial unique index:
   - `UNIQUE (case_id) WHERE is_active = TRUE`

### Target SQL Design

Gunakan writable CTE:

```sql
WITH locked_case AS (
  SELECT id, version
  FROM case_record
  WHERE id = #{caseId}
  FOR UPDATE
),
closed_previous_assignment AS (
  UPDATE case_assignment
  SET is_active = FALSE,
      released_at = #{now},
      released_by = #{actor},
      superseded_by_assignment_id = #{newAssignmentId}
  WHERE case_id = #{caseId}
    AND is_active = TRUE
  RETURNING id
),
inserted_assignment AS (
  INSERT INTO case_assignment (...)
  VALUES (...)
  RETURNING id
)
UPDATE case_record
SET assigned_unit_id = #{assignedUnitId},
    assignee_user_id = #{assigneeUserId},
    updated_at = #{now},
    updated_by = #{actor},
    version = version + 1
WHERE id = #{caseId}
  AND version = #{expectedVersion}
RETURNING ...;
```

### Application Behavior

`CaseApplicationService.assignCase(...)` tetap menjadi owner business validation, tetapi persistence path-nya berubah menjadi satu atomic mutation utama.

### Failure Rules

1. jika case version mismatch -> `409 CONCURRENT_MODIFICATION`
2. jika ada assignment aktif ganda karena race -> unique partial index menolak commit
3. jika actor assign ke target yang sama persis -> diperlakukan sebagai no-op atau conflict; keputusan yang direkomendasikan adalah `409 NO_EFFECT_ASSIGNMENT`

### Test Focus

1. tepat satu assignment aktif per case setelah repeated reassignment
2. history assignment lama tetap utuh
3. race dua assigner pada case yang sama menghasilkan satu winner

## Operation 3: Recursive CTE For Case Lineage

### API

- new: `POST /api/v1/cases/{caseId}/relationships`
- new: `GET /api/v1/cases/{caseId}/relationships`

### Use Case

Operator perlu melihat case lineage untuk:

1. duplicate cases yang digabung
2. case turunan dari triage/investigation lain
3. hubungan parent-child akibat split atau merge administrasi

### Required Schema

Tabel baru `case_relationship`:

1. `id`
2. `parent_case_id`
3. `child_case_id`
4. `relationship_type`
5. `relationship_reason`
6. `created_at`
7. `created_by`
8. `updated_at`
9. `updated_by`
10. `version`

Constraint:

1. `parent_case_id <> child_case_id`
2. unique `(parent_case_id, child_case_id, relationship_type)`

Index:

1. `idx_case_relationship_parent`
2. `idx_case_relationship_child`

### Read Contract

`GET /api/v1/cases/{caseId}/relationships` menerima parameter:

1. `direction=ANCESTORS|DESCENDANTS|BOTH`
2. `maxDepth`
3. `relationshipType`

Response item minimum:

1. `caseId`
2. `relatedCaseId`
3. `depth`
4. `direction`
5. `relationshipType`
6. `path`

### Recursive SQL Design

```sql
WITH RECURSIVE lineage AS (
  SELECT
      parent_case_id,
      child_case_id,
      relationship_type,
      1 AS depth,
      ARRAY[parent_case_id, child_case_id] AS path
  FROM case_relationship
  WHERE parent_case_id = #{seedCaseId}

  UNION ALL

  SELECT
      cr.parent_case_id,
      cr.child_case_id,
      cr.relationship_type,
      lineage.depth + 1,
      lineage.path || cr.child_case_id
  FROM case_relationship cr
  JOIN lineage ON cr.parent_case_id = lineage.child_case_id
  WHERE lineage.depth < #{maxDepth}
    AND NOT cr.child_case_id = ANY(lineage.path)
)
SELECT ...
FROM lineage;
```

Untuk `ANCESTORS`, traversal dibalik. Untuk `BOTH`, gabungkan dua traversal lalu normalisasi output.

### Write Behavior

`POST /api/v1/cases/{caseId}/relationships`:

1. validasi kedua case ada
2. validasi actor punya akses ke kedua case
3. cegah self-loop langsung
4. insert edge baru

Cycle transitive tidak harus dilarang di write path jika bisnis mengizinkan, tetapi read path harus tetap aman. Rekomendasi desain adalah menolak cycle transitive pada write path bila feasible.

### Test Focus

1. ancestor traversal
2. descendant traversal
3. `maxDepth`
4. cycle protection
5. duplicate edge rejection

## Operation 4: Transaction, Isolation, And Row Locking For Decision Approval

### API

- existing: `POST /api/v1/decisions/{decisionId}/approve`

### Use Case

Mencegah dua approver melakukan approval terhadap decision yang sama pada waktu hampir bersamaan.

### Current Problem

Saat ini approval bergantung pada:

1. load current record
2. domain mutate in memory
3. optimistic update by version

Ini sudah cukup untuk banyak kasus, tetapi belum menjadi reference eksplisit untuk row lock + isolation.

### Target Design

Flow approval baru:

1. buka transaction `READ_COMMITTED`
2. lock row decision target dengan `FOR UPDATE NOWAIT`
3. re-load state decision di dalam transaction
4. validasi maker-checker dan status
5. update decision
6. append audit/outbox
7. commit

Mapper tambahan:

1. `lockDecisionForApproval(UUID decisionId)`
2. opsional `lockCaseForDecision(UUID caseId)` jika diperlukan untuk cross-row invariant

### Why `READ_COMMITTED`

Untuk kasus ini, `READ_COMMITTED + FOR UPDATE NOWAIT` cukup karena:

1. resource yang dijaga adalah satu row approval utama
2. kita ingin cepat fail saat row sedang diproses pihak lain
3. `SERIALIZABLE` akan memberi biaya lebih tinggi tanpa kebutuhan nyata di flow ini

### Error Contract

Jika row tidak bisa dikunci:

1. balikan `409`
2. code yang direkomendasikan: `DECISION_LOCKED`
3. detail: decision sedang diproses approver lain

### Test Focus

1. dua approver paralel pada decision yang sama
2. satu request berhasil, satu request gagal dengan `409`
3. audit dan outbox hanya dibuat sekali

## Operation 5: Function Call Via MyBatis For Case Creation

### API

- existing: `POST /api/v1/cases`

### Use Case

Nomor kasus harus:

1. human-readable
2. concurrency-safe
3. diproduksi di database

### Current State

Repo sudah punya:

1. function `generate_case_number(...)`
2. MyBatis call di `CaseMyBatisMapper.nextCaseNumber(...)`

### Design Decision

Operasi ini tidak diganti, tetapi dijadikan baseline reference resmi untuk function call.

Hardening yang perlu:

1. tambahkan integration test concurrency yang lebih eksplisit
2. dokumentasikan sebagai contoh canonical `@Select` function call
3. pastikan flow create case end-to-end tetap melewati function ini

### Test Focus

1. beberapa create case paralel menghasilkan nomor unik
2. format nomor tetap sesuai kontrak

## Operation 6: Procedure Call And Table Lock For Overdue Obligation Batch

### API

- new: `POST /api/v1/operations/sanction-obligations/recalculate-overdue`

### Use Case

Operator perlu menjalankan batch yang:

1. mencari sanction obligation aktif yang melewati due date
2. menandai statusnya menjadi `OVERDUE`
3. menghasilkan bukti operasional yang bisa diaudit

Ini bukan hot path user normal, sehingga cocok untuk contoh procedure + table lock.

### Required Schema

Tambahan yang direkomendasikan:

1. tabel `maintenance_operation_run`
   - `id`
   - `operation_name`
   - `requested_by`
   - `requested_at`
   - `effective_date`
   - `result_status`
   - `affected_rows`
   - `details_json`

### Procedure Design

Nama yang direkomendasikan:

- `recalculate_overdue_sanction_obligations(p_effective_date DATE, p_actor VARCHAR, p_run_id UUID)`

Tanggung jawab procedure:

1. update obligation aktif yang overdue
2. hitung affected rows
3. simpan summary ke `maintenance_operation_run`

Procedure tidak menjadi owner orchestration aplikasi lain.

### Table Lock Design

Di transaction operator:

1. buka transaction `REPEATABLE_READ`
2. jalankan `LOCK TABLE sanction_obligation IN SHARE ROW EXCLUSIVE MODE`
3. panggil procedure
4. commit

Alasan:

1. mencegah batch overdue tumpang tindih dengan batch yang sama
2. menghindari partial view selama maintenance run

### MyBatis Invocation Design

Gunakan mapper khusus maintenance operation:

1. `lockSanctionObligationTable()`
2. `callRecalculateOverdueSanctionObligations(...)`
3. `findMaintenanceRunById(...)`

Procedure call direkomendasikan memakai statement callable yang eksplisit.

### Authorization

Hanya role operator/admin tertentu yang boleh menjalankan endpoint ini. Rekomendasi:

1. `SUPERVISOR`
2. `SYSTEM_ADMIN`

### API Response

Response minimum:

1. `runId`
2. `operationName`
3. `effectiveDate`
4. `affectedRows`
5. `resultStatus`

### Test Focus

1. single batch run berhasil
2. dua batch simultan tidak sama-sama jalan
3. affected row count akurat
4. rerun idempotency behavior jelas dan terdokumentasi

## OpenAPI Additions

Endpoint baru yang harus ditambahkan:

1. `POST /api/v1/cases/{caseId}/relationships`
2. `GET /api/v1/cases/{caseId}/relationships`
3. `POST /api/v1/operations/sanction-obligations/recalculate-overdue`

Contract existing yang harus diperbarui:

1. `GET /api/v1/workflow-reconciliation`
2. `POST /api/v1/cases/{caseId}/assignments`
3. `POST /api/v1/decisions/{decisionId}/approve`

## Required Persistence Files

File/area yang diperkirakan berubah:

1. `sentinel-persistence/src/main/resources/db/changelog/releases/*.yaml`
2. `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/MyBatisTransactionManager.java`
3. `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/casefile/CaseMyBatisMapper.java`
4. `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/workflow/WorkflowReconciliationMyBatisMapper.java`
5. mapper/repository baru untuk case relationships
6. mapper/repository baru untuk maintenance operations
7. `sentinel-application` services yang terkait
8. `sentinel-api` resources dan mapper API

## Verification Strategy

### Unit

1. authorization edge cases untuk relationship write dan operator batch
2. conflict mapping logic

### Integration

1. CTE query reconciliation
2. writable CTE assignment
3. recursive CTE lineage
4. function call create case
5. procedure batch overdue
6. lock conflict approval

### End-to-End

1. create case tetap sukses setelah function path dipertahankan
2. assign case lalu baca assignment/relationship flow berjalan
3. approve decision under concurrent pressure terverifikasi
4. operator batch overdue bisa dipanggil dan hasilnya terlihat di API/read model yang relevan

## Design Risks

1. writable CTE assignment akan mengubah semantik `case_assignment`, sehingga migration dan compatibility test harus ketat
2. recursive relationship mudah melebar jika relationship type dibuat terlalu generik
3. table lock yang salah mode atau salah endpoint akan merusak throughput
4. procedure yang terlalu pintar akan mulai menyedot business logic dari application layer

## Guardrails

1. gunakan row lock untuk request bisnis, table lock hanya untuk maintenance batch
2. recursive traversal dibatasi `maxDepth`
3. semua mutation baru tetap melewati authorization service
4. audit/outbox tetap application-owned kecuali summary maintenance yang memang local ke procedure
