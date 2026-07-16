# BPMN Implementation Remediation

## Purpose

Dokumen ini mengubah gap pada [07-IMPLEMENTATION_GAP.md](C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/plan/enhance-bpmn/07-IMPLEMENTATION_GAP.md) menjadi **satu tahap remediasi** yang dieksekusi end-to-end sampai BPMN baru benar-benar compatible dengan code Java.

Target tahap tunggal ini:

1. menyamakan arsitektur BPMN, runtime Java, dan dokumentasi plan
2. menutup gap kontrak workflow yang membuat branch BPMN baru berhenti atau ambigu
3. mengganti placeholder dengan seam runtime yang bermakna
4. menambah verifikasi behavior agar kompatibilitas tidak hanya asumsi

## Stage Definition

Nama tahap:

- `Single-Stage BPMN Compatibility Remediation`

Tujuan tahap:

- membawa perubahan BPMN yang sudah masuk pada **July 16, 2026** sampai ke kondisi executable, operable, dan testable dalam satu alur kerja terpadu

Karakter tahap:

1. tetap satu tahap
2. tidak dipecah menjadi phase formal terpisah
3. tetapi memiliki urutan kerja internal yang wajib dijaga
4. verifikasi dilakukan berulang di dalam tahap yang sama

## Locked Direction

Tahap tunggal ini memakai keputusan berikut:

1. `Appeal Handling` disatukan ke main lifecycle agar konsisten dengan plan `02` sampai `06`
2. semua receive/message path baru harus hidup melalui workflow correlation seam resmi
3. task API hanya diperluas untuk task yang memang membutuhkan outcome/operator choice
4. `BusinessRuleTask` boleh memakai adapter transisional jika DMN penuh belum siap, tetapi tidak boleh dibiarkan kosong secara runtime
5. `GlobalHoldSignal` harus punya semantics tunggal dan operasional nyata

## Internal Work Order

Walaupun hanya satu tahap, urutan kerja internalnya harus seperti ini:

### 1. Architecture Alignment

Kerja yang harus dilakukan:

1. satukan model appeal ke main BPMN lifecycle
2. hilangkan ambiguity antara main process dan process appeal terpisah
3. sesuaikan `CaseWorkflowPort`, application service, dan BPMN XML agar hanya ada satu cerita orchestration appeal
4. rapikan dokumen plan jika masih ada bagian yang menyebut model lama

Output minimum:

1. BPMN utama memuat appeal path final
2. wiring Java tidak lagi memulai orchestration appeal yang bertentangan
3. plan dan code tidak saling menyanggah

### 2. Correlation Contract

Kerja yang harus dilakukan:

1. perluas workflow port untuk correlation resmi
2. implement adapter correlation untuk:
   - `AppealFiled`
   - `ExternalEvidenceDelivered`
   - `SanctionRegistryAcknowledged`
   - `NotificationResultReceived`
3. tetapkan payload minimum, correlation key, dan variable mapping
4. pastikan duplicate correlation aman dan deterministic

Output minimum:

1. semua receive/message branch baru punya jalur lanjut resmi
2. correlation tidak lagi bergantung pada helper informal atau manipulasi manual state

### 3. Human Task Surface Alignment

Kerja yang harus dilakukan:

1. inventaris seluruh user task operator-facing dari BPMN baru
2. perluas mapping `taskDefinitionKey` di application service
3. perluas visibility dan authorization task
4. ubah contract complete-task bila task memang membutuhkan outcome
5. hubungkan outcome task ke command/domain change yang eksplisit

Output minimum:

1. task baru terlihat di surface aplikasi jika memang operator-facing
2. task completion tidak lagi payload-less untuk branch yang membutuhkan keputusan operator

### 4. Runtime Delegate Remediation

Kerja yang harus dilakukan:

1. ganti `PreTriageRoutingDelegate` default-only menjadi enrichment yang bermakna
2. pecah `MockWorkflowServiceDelegate` menjadi delegate yang spesifik per use case
3. hidupkan branch penting:
   - evidence request
   - sanction publication request
   - notification command
   - obligation schedule creation
   - manual notification marker
4. tambahkan audit/outbox jika side effect bersifat business-visible

Output minimum:

1. branch BPMN penting tidak lagi sekadar placeholder
2. side effect runtime bisa ditelusuri

### 5. Rule Task Remediation

Kerja yang harus dilakukan:

1. putuskan implementasi `BusinessRuleTask` per task:
   - DMN nyata
   - atau adapter transisional
2. hidupkan `evaluateEvidenceSufficiency`
3. hidupkan `determineSanctionPackage`
4. dokumentasikan jika ada pilihan transisional

Output minimum:

1. tidak ada rule task yang valid hanya pada level diagram
2. outcome rule benar-benar mengendalikan flow

### 6. Hold and Signal Remediation

Kerja yang harus dilakukan:

1. samakan contract `GlobalHoldSignal`
2. rapikan producer dan consumer signal
3. putuskan interrupting vs non-interrupting behavior yang nyata
4. tambahkan seam ops/admin jika signal dapat dipicu operator

Output minimum:

1. signal hold tidak lagi terpecah
2. hold path benar-benar executable dan operasional

### 7. Observability and Reconciliation

Kerja yang harus dilakukan:

1. perluas readiness probe agar definition wajib benar-benar dicek
2. tambahkan visibility untuk wait state penting:
   - awaiting appeal
   - awaiting external evidence
   - awaiting registry acknowledgment
   - awaiting notification result
   - held state
3. perluas reconciliation view untuk mismatch baru
4. tambahkan runbook untuk stuck correlation dan hold behavior

Output minimum:

1. operator bisa melihat proses sedang menunggu event apa
2. mismatch domain-workflow tidak lagi gelap

### 8. Behavioral Verification

Kerja yang harus dilakukan:

1. tambahkan workflow execution tests untuk branch utama dan korektif
2. verifikasi message path, timer path, signal path, dan failure path
3. verifikasi duplicate correlation
4. verifikasi task completion semantics baru

Output minimum:

1. kompatibilitas BPMN dibuktikan lewat behavior, bukan hanya model validation

## Cross-Module Scope

Modules yang tersentuh dalam tahap tunggal ini:

1. `sentinel-workflow`
2. `sentinel-application`
3. `sentinel-api`
4. `sentinel-persistence`
5. `sentinel-messaging`
6. `sentinel-integration-tests`
7. `docs/api`
8. `docs/runbooks`
9. `docs/plan/enhance-bpmn`

## Verification Gates Inside The Single Stage

Walaupun satu tahap, ada gate verifikasi internal yang tidak boleh dilewati begitu saja.

### Gate A

Setelah architecture alignment dan correlation contract:

1. BPMN model validation lulus
2. case workflow start tetap berjalan
3. `AppealFiled` bisa menggerakkan main lifecycle yang benar

### Gate B

Setelah task surface dan delegate remediation:

1. task baru yang operator-facing terlihat
2. outcome-driven completion berjalan
3. branch notification/registry/evidence tidak lagi placeholder-only

### Gate C

Setelah rule task, signal, dan observability remediation:

1. rule-task branch executable
2. hold semantics konsisten
3. readiness dan reconciliation mengenali topology baru

### Gate D

Final gate:

1. behavior tests utama lulus
2. branch korektif penting lulus
3. dokumentasi plan, BPMN, dan code sudah konsisten

## Recommended Execution Sequence

Urutan implementasi praktis di dalam tahap tunggal ini:

1. rapikan appeal architecture
2. tambahkan correlation seam
3. rapikan task API dan task application service
4. ganti placeholder delegate
5. hidupkan rule task
6. rapikan signal hold
7. tambah observability dan reconciliation
8. tutup dengan workflow behavior tests

Urutan ini tidak boleh dibalik tanpa alasan kuat, karena:

1. task API baru akan rancu jika correlation seam belum final
2. hold semantics akan kabur jika arsitektur appeal belum stabil
3. execution test akan mahal jika contract runtime masih bergerak

## Deliverables

Tahap tunggal ini harus menghasilkan:

1. BPMN utama yang final secara arsitektur
2. workflow port dan adapter correlation resmi
3. task surface yang sesuai dengan BPMN baru
4. runtime delegate yang tidak lagi generik
5. rule-task implementation yang executable
6. signal hold yang konsisten
7. observability dan reconciliation minimal untuk wait state baru
8. execution tests yang membuktikan compatibility

## Minimum Test Set

Test minimum yang harus ada sebelum tahap ini dianggap selesai:

1. baseline case workflow path
2. appeal filed path
3. appeal expiry path
4. external evidence delivered path
5. registry acknowledgment success path
6. notification result success path
7. notification result failed path
8. global hold signal path
9. duplicate correlation path
10. wrong correlation key path

## Verification Commands

Command minimum yang harus dijalankan selama tahap ini:

1. `mvn -q -pl sentinel-workflow -am test`
2. `mvn -q test`
3. `mvn -q -pl sentinel-integration-tests -am verify`

Jika perubahan lintas module sudah cukup besar:

1. `mvn -q verify`

Jika environment menghalangi, statusnya harus tetap disebut partial, bukan complete.

## Risks and Controls

### Risk 1

Satu tahap berubah menjadi “big bang” yang sulit diverifikasi.

Control:

- pakai gate internal A sampai D
- commit/logical checkpoint tetap dilakukan per urutan kerja internal

### Risk 2

Correlation seam selesai setengah jalan dan BPMN baru tetap menggantung di wait state.

Control:

- semua receive/message path baru wajib punya handler sebelum stage ditutup

### Risk 3

Task completion API menjadi terlalu generik dan mengaburkan domain rule.

Control:

- outcome task hanya boleh memicu command yang eksplisit dan tervalidasi

### Risk 4

Rule task tetap semu karena DMN terus ditunda.

Control:

- jika DMN belum siap, wajib ada adapter transisional resmi pada tahap yang sama

### Risk 5

Struktur BPMN dianggap compatible hanya karena test model lulus.

Control:

- behavior tests wajib menjadi gate final

## Definition Of Done

Tahap tunggal remediasi ini hanya boleh dianggap selesai jika seluruh kondisi berikut terpenuhi:

1. appeal path tidak lagi ambigu antara plan, BPMN, dan code
2. semua receive/message branch baru punya correlation seam resmi
3. task operator-facing baru terlihat dan bisa diselesaikan lewat surface aplikasi
4. placeholder delegate utama sudah diganti atau dipersempit secara sadar
5. rule-task branch sudah executable
6. `GlobalHoldSignal` konsisten dan operasional
7. readiness dan reconciliation memahami topology baru
8. workflow behavior tests menutup path utama dan branch korektif penting

## Immediate Next Step

Langkah pertama dalam tahap tunggal ini:

1. satukan appeal ke main lifecycle
2. implement `AppealFiled` correlation ke main process
3. verifikasi satu skenario end-to-end:
   - decision publication selesai
   - event-based gateway aktif
   - appeal filed masuk
   - token pindah ke appeal path yang benar

Jika langkah ini belum selesai, tahap remediasi tunggal belum benar-benar dimulai.
