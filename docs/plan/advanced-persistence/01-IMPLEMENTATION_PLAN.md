# Advanced Persistence Operations Plan

## Purpose

Dokumen ini mengunci paket implementasi agar Sentinel memiliki reference yang nyata untuk:

1. CTE query di MyBatis
2. writable CTE untuk update/insert di MyBatis
3. recursive CTE query di MyBatis
4. transaction, isolation, dan row/table locking via MyBatis
5. pemanggilan PostgreSQL function dan procedure dari MyBatis

Targetnya bukan demo SQL terpisah, tetapi use case yang benar-benar menempel ke API bisnis Sentinel.

## Assumptions

Asumsi yang dipakai di dokumen ini:

1. kita mempertahankan boundary repo saat ini:
   - `sentinel-api` untuk contract dan resource
   - `sentinel-application` untuk orchestration dan authorization
   - `sentinel-persistence` untuk MyBatis, transaction manager, Liquibase
2. style persistence tetap annotation-first seperti mapper yang ada sekarang, kecuali procedure call memerlukan bentuk `CALLABLE` yang lebih jelas
3. kita tidak menaruh orchestration workflow utama ke stored procedure
4. table lock tidak boleh dipakai di hot path operator normal; table lock hanya boleh dipakai pada endpoint operator/admin yang memang bersifat maintenance batch

## Reference Operation Matrix

| Capability | API surface | Use case | Status |
| --- | --- | --- | --- |
| CTE query | `GET /api/v1/workflow-reconciliation` | membaca mismatch domain-workflow dengan projection SQL yang lebih kaya dan paginasi di database | upgrade existing |
| writable CTE update/insert | `POST /api/v1/cases/{caseId}/assignments` | menutup assignment aktif lama, membuat assignment aktif baru, dan meng-update snapshot assignment case secara atomik | upgrade existing |
| recursive CTE query | `GET /api/v1/cases/{caseId}/relationships` | membaca lineage kasus hasil merge/derivation secara transitif | new |
| relationship write support | `POST /api/v1/cases/{caseId}/relationships` | mendaftarkan edge hubungan antar kasus agar recursive read punya data nyata | new |
| transaction + isolation + row locking | `POST /api/v1/decisions/{decisionId}/approve` | mencegah dua approver memproses approval yang sama secara bersamaan | upgrade existing |
| function call via MyBatis | `POST /api/v1/cases` | menghasilkan nomor kasus secara concurrency-safe dengan `generate_case_number(...)` | existing baseline, harden and document |
| transaction + isolation + table locking + procedure call | `POST /api/v1/operations/sanction-obligations/recalculate-overdue` | operasi maintenance batch untuk menandai obligation yang overdue secara konsisten | new |

## Why These Use Cases

Pemetaan ini dipilih karena tiap capability punya alasan bisnis yang nyata:

1. `workflow-reconciliation` memang query-heavy dan sekarang masih menarik kandidat lalu mengolah sebagian logic di application layer.
2. `assignments` adalah tempat paling natural untuk writable CTE karena ia menyentuh history row dan snapshot current assignee sekaligus.
3. recursive CTE baru masuk akal bila ada lineage kasus yang benar-benar dibaca operator, bukan query buatan.
4. `approve decision` adalah titik race condition yang paling mudah dipahami dan paling bernilai untuk contoh row locking.
5. `create case` sudah punya function call yang relevan dan memang domain-critical.
6. batch overdue obligation adalah tempat yang tepat untuk menunjukkan procedure call dan table locking tanpa merusak hot path user-facing.

## Scope

### In Scope

1. perubahan OpenAPI untuk endpoint baru dan response tambahan yang dibutuhkan
2. perubahan application service dan command untuk operasi baru
3. perubahan Liquibase untuk tabel, index, constraint, function/procedure baru
4. perubahan MyBatis mapper dan adapter
5. perluasan `ApplicationTransactionManager` agar isolation level bisa dikontrol secara eksplisit
6. integration test dan concurrency test untuk seluruh capability
7. postman collection dan flow docs setelah kontrak final

### Out Of Scope

1. memindahkan orchestration Camunda ke database
2. generalized graph engine untuk semua relationship arbitrer
3. penggunaan table lock pada endpoint harian seperti claim task, transition case, atau publish decision
4. mengganti seluruh mapper annotation ke XML hanya demi satu dua query kompleks

## Required Design Decisions

Keputusan ini dikunci sebelum implementasi:

1. `workflow-reconciliation` dipindahkan menjadi SQL-driven page, bukan load-all-then-filter di Java
2. `case_assignment` diubah dari append-only sederhana menjadi punya konsep assignment aktif saat ini
3. recursive relationship memakai directed edge agar traversal deterministik
4. row lock approval memakai explicit transaction boundary di persistence layer, bukan bergantung pada retry acak
5. batch maintenance overdue memakai endpoint operator baru, role terbatas, dan run summary yang auditable

## Delivery Model

Semua operasi diimplementasikan dalam satu paket delivery dengan urutan internal berikut.

### 1. Persistence Foundation

Kerja:

1. tambah transaction options yang mendukung isolation
2. tambah exception translation untuk lock conflict PostgreSQL
3. tambah helper mapper/callable support yang dibutuhkan function/procedure call

Success criteria:

1. application layer bisa meminta transaction dengan isolation eksplisit
2. `55P03` dan conflict sejenis bisa dipetakan ke `409`

### 2. CTE Query Upgrade

Kerja:

1. redesign `GET /api/v1/workflow-reconciliation`
2. pindahkan filtering, sorting, issue projection, dan pagination utama ke SQL

Success criteria:

1. query tidak lagi membutuhkan full load kandidat ke memory
2. response tetap kompatibel secara fungsional, tetapi lebih kaya bila perlu

### 3. Writable CTE Assignment Upgrade

Kerja:

1. ubah model assignment aktif
2. implement atomic assignment rotation di satu statement utama

Success criteria:

1. hanya ada satu assignment aktif per case
2. case snapshot dan assignment history selalu sinkron

### 4. Case Relationship Lineage

Kerja:

1. tambah tabel `case_relationship`
2. tambah write endpoint
3. tambah recursive read endpoint

Success criteria:

1. operator bisa membaca ancestor/descendant lineage
2. recursive query aman dari loop/cycle

### 5. Lock-Sensitive Decision Approval

Kerja:

1. row lock untuk approval
2. isolation eksplisit
3. conflict contract untuk resource yang sedang dikunci

Success criteria:

1. dua approver simultan tidak menghasilkan double side effect
2. approval conflict bisa direproduksi dan diverifikasi di integration test

### 6. Function and Procedure Reference

Kerja:

1. dokumentasikan dan harden function path `generate_case_number(...)`
2. tambah procedure batch overdue obligation
3. expose operator endpoint untuk memicu procedure tersebut

Success criteria:

1. repo memiliki contoh function call dan procedure call yang sama-sama aktif
2. batch procedure punya auditability dan operator evidence

## Cross-Module Change Map

### `sentinel-api`

Perlu:

1. endpoint baru untuk case relationships
2. endpoint operator baru untuk overdue recalculation
3. kemungkinan enrichment response workflow reconciliation
4. error mapping baru untuk lock conflict

### `sentinel-application`

Perlu:

1. `CaseApplicationService.assignCase(...)` diubah agar selaras dengan writable CTE assignment model
2. service baru atau perluasan service untuk case relationship
3. `DecisionApplicationService.approveDecision(...)` memakai transaction options baru
4. service operator untuk overdue obligation recalculation

### `sentinel-persistence`

Perlu:

1. Liquibase schema baru
2. mapper baru dan perubahan mapper existing
3. `MyBatisTransactionManager` diperluas untuk isolation-aware session
4. repository adapter baru untuk relationships dan maintenance operation

### `sentinel-integration-tests`

Perlu:

1. query behavior tests
2. writable CTE atomicity tests
3. recursive CTE lineage tests
4. concurrent approval tests
5. procedure execution tests

### `docs/api` and Postman

Perlu:

1. OpenAPI update
2. Postman collection update
3. flow guide update untuk relationship dan operator batch flow

## Test And Verification Matrix

| Area | Verification |
| --- | --- |
| function call | integration test case number generation concurrency |
| CTE query | integration test filter, cursor, issue type, authorization visibility |
| writable CTE | integration test assignment rotation + current assignment uniqueness |
| recursive CTE | integration test ancestors, descendants, depth limit, cycle protection |
| row locking | concurrent integration test dua approver pada decision yang sama |
| table locking + procedure | integration test operator batch run, duplicate run, lock contention |
| API contract | OpenAPI validation + smoke call per endpoint |

## Definition Of Done

Paket ini baru dianggap selesai jika:

1. semua capability yang diminta punya API use case yang benar-benar hidup
2. minimal satu function call dan satu procedure call aktif di aplikasi
3. row locking dan table locking keduanya terbukti lewat automated test
4. recursive CTE bukan hanya query contoh, tetapi membaca data lineage yang bisa dibuat lewat API
5. postman collection dan markdown flow sudah diperbarui
6. end-to-end verification dijalankan untuk flow yang terpengaruh

## Recommended Implementation Order

Urutan implementasi yang direkomendasikan:

1. transaction foundation
2. workflow reconciliation CTE query
3. assignment writable CTE
4. case relationship schema + recursive read/write
5. decision approval locking
6. procedure batch overdue
7. OpenAPI/Postman/docs refresh
8. full integration and e2e verification

Alasannya:

1. isolation-aware transaction manager menjadi fondasi untuk locking dan procedure batch
2. query-heavy reconciliation paling rendah risiko terhadap business mutation
3. assignment rotation perlu schema change yang akan memengaruhi API existing
4. recursive CTE butuh tabel baru sehingga lebih aman setelah fondasi transaction jelas
5. approval locking lebih aman dilakukan setelah conflict translation tersedia
6. procedure batch paling cocok ditutup belakangan karena ia memakai capability lock paling berat
