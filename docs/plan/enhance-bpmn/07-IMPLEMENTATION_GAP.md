# BPMN Implementation Gap

## Purpose

Dokumen ini merangkum gap implementasi yang muncul setelah perubahan BPMN di:

- `sentinel-workflow/src/main/resources/bpmn/regulatory-enforcement-case.bpmn`
- `sentinel-workflow/src/main/resources/bpmn/decision-appeal-review.bpmn`

Fokus dokumen ini bukan pada kualitas diagram, tetapi pada efek runtime dan perubahan code Java lintas module yang masih harus dilakukan agar BPMN baru benar-benar executable, compatible, dan operasional.

## Scan Scope

Dokumen ini disusun dari scan terhadap:

1. plan BPMN:
   - `docs/plan/enhance-bpmn/ENHANCE_BPMN_PLAN.md`
   - `docs/plan/enhance-bpmn/02-BPMN_BLUEPRINT.md`
   - `docs/plan/enhance-bpmn/03-MAIN_PROCESS_DRAFT.md`
   - `docs/plan/enhance-bpmn/04-SUBPROCESS_DRAFTS.md`
   - `docs/plan/enhance-bpmn/05-BPMN_MESSAGE_AND_EVENT_MATRIX.md`
   - `docs/plan/enhance-bpmn/06-DIAGRAM_BUILD_SEQUENCE.md`
2. runtime workflow:
   - `sentinel-workflow/src/main/java/com/sentinel/enforcement/workflow/*`
3. application and API seams:
   - `sentinel-application/src/main/java/com/sentinel/enforcement/application/workflow/*`
   - `sentinel-application/src/main/java/com/sentinel/enforcement/application/casefile/CaseApplicationService.java`
   - `sentinel-application/src/main/java/com/sentinel/enforcement/application/appeal/AppealApplicationService.java`
   - `sentinel-api/src/main/java/com/sentinel/enforcement/api/workflow/TaskResource.java`
   - `docs/api/openapi.yaml`
4. embedded Camunda integration note:
   - `docs/architecture/camunda-embedded-integration.md`

## Executive Summary

Status saat ini adalah:

- BPMN baru sudah mulai diadopsi pada level diagram dan start semantics.
- wiring Java baru menutup sebagian kecil kebutuhan:
  - message start untuk case dan appeal
  - delegate placeholder untuk beberapa service/send task
  - validasi struktur model BPMN
- tetapi implementasi masih belum setara dengan kompleksitas BPMN baru.

Kesimpulan praktis:

1. model BPMN sudah lebih maju daripada port application, API, dan runtime correlation yang tersedia
2. beberapa branch BPMN akan berhenti menunggu event yang belum punya jalur correlation
3. beberapa human task baru tidak akan terlihat atau tidak bisa diselesaikan lewat task surface saat ini
4. sebagian semantics yang dikunci di dokumen plan belum benar-benar tercermin di code dan BPMN final

## Gap Matrix

| Gap | Current state | Runtime effect | Implementation that should be done |
| --- | --- | --- | --- |
| Appeal semantics mismatch | plan `02`/`03`/`04`/`06` mengunci `Appeal Handling` sebagai subprocess di main lifecycle, tetapi implementasi tetap memakai process terpisah `decision-appeal-review.bpmn` dan `startAppealWorkflow(...)` | lifecycle case menjadi terbelah antara main process dan appeal process; event-based gateway di main process yang menunggu `AppealFiled` tidak otomatis tersambung dengan create appeal flow | pilih satu arah dan konsisten. Rekomendasi: jika tetap mengikuti plan, pindahkan appeal menjadi subprocess di main process dan hapus start process appeal terpisah. Jika tetap terpisah, revisi plan dan tambahkan bridge correlation yang eksplisit |
| Appeal filed message is not correlated to the main process | BPMN utama menunggu `Message_AppealFiled`, tetapi `AppealApplicationService` membuat appeal, mengubah status case, lalu memulai workflow appeal terpisah | main process bisa tetap parkir di appeal window walau appeal sudah dibuat di domain | tambahkan port dan adapter untuk message correlation `AppealFiled` ke main process instance berdasarkan `caseId`, atau ubah model agar create appeal memang menyelesaikan wait state yang benar |
| Message correlation layer is missing | BPMN baru menambah `ExternalEvidenceDelivered`, `SanctionRegistryAcknowledged`, `NotificationResultReceived`, dan `AppealFiled`, tetapi `CaseWorkflowPort` hanya punya start/cancel/list/claim/complete task | receive task dan message catch event baru tidak punya jalur resmi untuk resume token | tambah workflow command surface untuk message correlation. Minimal: `correlateExternalEvidenceDelivered`, `correlateSanctionRegistryAcknowledged`, `correlateNotificationResultReceived`, `correlateAppealFiled` |
| Signal semantics are inconsistent | plan mengunci `GlobalHoldSignal`, tetapi appeal BPMN memakai `Signal_AppealGlobalHold` yang berbeda dari `Signal_GlobalHold` di main process | signal dari appeal tidak akan men-trigger hold path yang ditunggu main case flow | samakan signal contract. Gunakan satu signal operasional yang konsisten untuk hold, atau tambahkan explicit bridge jika sengaja dipisah |
| Business rule task runtime is absent | BPMN memakai `camunda:decisionRef` untuk `evaluateEvidenceSufficiency` dan `determineSanctionPackage`, tetapi repo belum punya resource DMN dan belum ada decision wiring | branch rule task tidak terbukti executable; model bisa lolos parse tetapi gagal saat dijalankan | pilih implementasi nyata: tambahkan file DMN + deployment, atau ganti sementara ke service task dengan adapter yang jelas dan nanti dimigrasikan ke DMN |
| New human tasks are not mapped in task application service | `WorkflowTaskApplicationService` hanya mengenal `triageTask`, `investigationTask`, `reviewTask`, `decisionTask`, dan `appealReviewTask` | user task baru seperti legal advisory, supervisor review, override review, monitoring tasks, dan review failure tasks berisiko tidak terlihat atau tidak bisa diselesaikan melalui `/api/v1/tasks` | perluas task definition mapping, authorization role mapping, visibility rules, dan completion behavior untuk seluruh user task baru yang memang exposed ke operator |
| Task completion contract is still payload-less and linear | `POST /api/v1/tasks/{taskId}/complete` tidak menerima body, dan `WorkflowTaskApplicationService.completeTask(...)` mengasumsikan alur linear case status | gateway yang membutuhkan outcome task, branch correction, atau choice operator tidak bisa dikendalikan dari task API saat ini | desain ulang completion contract. Tambahkan request body untuk outcome dan variable bisnis yang sah, lalu map secara eksplisit ke command/domain change dan variable workflow yang diperlukan |
| Workflow progression logic still assumes the old linear case lifecycle | `advanceCaseForTask(...)` masih memetakan completion task ke transisi status linear seperti `UNDER_INVESTIGATION -> PENDING_REVIEW -> PENDING_DECISION -> DECIDED` | BPMN baru yang punya legal advisory branch, supervisor review, publication transaction, corrective path, dan enforcement monitoring belum punya kesetaraan domain progression | pisahkan orchestration concern dari simple task completion. Beberapa task baru harus memanggil application service khusus, bukan hanya `transitionCase(...)` generik |
| Service/send tasks are mostly placeholder only | `PreTriageRoutingDelegate` hanya set default boolean; `MockWorkflowServiceDelegate` hanya set beberapa variable teknis | branch BPMN tampak kaya, tetapi side effect bisnis nyata, audit, outbox, dan integration semantics belum hidup | ganti placeholder bertahap dengan adapter yang bermakna: routing enrichment dari domain, external request command, registry publication request, notification command dispatch, obligation schedule creation, manual-notification marker |
| Negative ack and failure-result semantics are not wired | plan `05` dan `06` mengunci negative ack registry sebagai branch bisnis dan `NotificationResultReceived=FAILED` sebagai path korektif, tetapi belum ada payload mapping untuk hasil itu | gateway `registryAckResultGateway` dan `notificationResultGateway` tidak punya jalur yang mengisi variable hasil secara resmi | definisikan payload contract, mapper, dan correlation adapter yang mengisi variable outcome secara deterministic dan idempotent |
| External interaction is modeled in BPMN but not surfaced in API or messaging seam | message flow ke external evidence provider, sanction registry, dan notification service sudah muncul di BPMN, tetapi belum ada command/API seam yang jelas untuk mock atau integration harness | diagram terlihat enterprise-grade, tetapi sistem belum punya titik masuk/keluar yang stabil untuk test atau simulasi external callback | tambahkan integration seam yang eksplisit. Bisa berupa internal adapter, mock callback endpoint, atau test harness service yang memanggil workflow correlation layer |
| Readiness and operational visibility still cover only part of the new topology | readiness probe hanya mengecek satu `processDefinitionKey`; snapshot/admin model utama berasumsi business key `caseId` tunggal | proses appeal terpisah dan definition baru tidak sepenuhnya tervalidasi pada readiness/admin path | perluas readiness agar memeriksa seluruh process definition yang wajib hidup. Tambahkan admin/read model yang bisa membedakan main case instance dan appeal instance bila arsitektur dual-process dipertahankan |
| Reconciliation and correlation model need expansion | `workflow_instance` sudah menyimpan correlation untuk main dan appeal start/cancel, tetapi belum ada model untuk pending receive/message checkpoints | ketika proses berhenti di receive task, operator tidak punya cara application-owned untuk melihat event apa yang ditunggu dan apakah mismatch terjadi | tambahkan observability/reconciliation projection untuk wait state penting: evidence wait, registry ack wait, notification result wait, appeal wait, hold state |
| BPMN/document plan mismatch remains unresolved | plan `06` menyatakan appeal subprocess di main process, resend notification sekali, negative ack semantics, dan corrective closure path; implementasi BPMN saat ini hanya mengambil sebagian keputusan itu | repo menjadi sulit di-review karena “plan final” dan “code final” belum satu cerita | lakukan alignment pass: update BPMN agar sesuai plan, atau revisi plan supaya mencerminkan implementasi yang benar-benar dipilih |
| Test coverage is still structural, not behavioral | `BpmnModelValidationTest` hanya memeriksa elemen ada di model; belum ada execution test untuk message path, timer path, signal path, transaction cancel, compensation, atau notification failure branch | perubahan BPMN bisa terlihat valid tetapi tetap rusak saat runtime | tambah workflow execution tests per branch penting: appeal filed path, appeal expiry path, evidence loop, registry negative ack, notification failed, global hold, override path, duplicate correlation |

## Cross-Module Impact

Perubahan yang masih perlu dilakukan tidak berhenti di `sentinel-workflow`.

### `sentinel-workflow`

Perlu:

1. message correlation adapter
2. signal handling yang konsisten
3. penggantian placeholder delegate
4. DMN atau pengganti service-task untuk rule task
5. readiness probe yang memeriksa topology workflow yang benar

### `sentinel-application`

Perlu:

1. perluasan `CaseWorkflowPort`
2. refactor `WorkflowTaskApplicationService`
3. mapping completion task yang tidak lagi linear
4. explicit application command untuk callback/result dari external interaction
5. explicit application command untuk hold / override / corrective path bila memang BPMN menuntutnya

### `sentinel-api`

Perlu:

1. endpoint atau seam callback untuk message correlation mock/external
2. request body baru untuk complete task yang butuh outcome
3. kemungkinan endpoint admin/ops untuk signal hold atau reconciliation action yang lebih kaya

### `sentinel-persistence`

Perlu:

1. penyimpanan projection/correlation tambahan untuk wait state bila dibutuhkan
2. kemungkinan tabel/event tambahan untuk callback tracking dan idempotency per workflow correlation

### `sentinel-messaging`

Perlu:

1. integration path yang jelas antara event result dan workflow correlation
2. idempotent consumer behavior yang tidak hanya menyelesaikan side effect domain, tetapi juga aman untuk correlation workflow

### `docs/api` dan runbook

Perlu:

1. pembaruan kontrak OpenAPI
2. runbook untuk stuck receive task
3. runbook untuk failed correlation
4. runbook untuk hold signal behavior

## Priority Order

Urutan implementasi yang paling aman:

1. selesaikan keputusan arsitektur appeal:
   - subprocess di main process
   - atau process terpisah dengan bridge resmi
2. tambah message correlation port dan adapter
3. selesaikan task API/completion contract untuk human task baru
4. selesaikan negative ack dan notification failure semantics
5. ganti placeholder delegate pada branch yang sudah dimodelkan
6. putuskan DMN vs service-task sementara untuk rule task
7. tambah execution tests sebelum memperluas branch lain
8. baru setelah itu rapikan observability, readiness, dan reconciliation

## Recommended First Increment

Increment paling bernilai sekarang:

1. finalisasi appeal orchestration direction
2. implement `AppealFiled` correlation ke main lifecycle yang benar
3. tambahkan message correlation API internal untuk:
   - external evidence delivered
   - sanction registry acknowledged
   - notification result received
4. tambahkan satu workflow execution test:
   - sanction published -> event-based gateway -> appeal filed path

Alasan:

- ini menutup mismatch paling besar antara BPMN baru dan runtime Java
- ini memberi fondasi untuk receive/message branch lain
- ini mencegah repo masuk lebih dalam ke diagram yang kaya tetapi orchestration-nya terputus

## Definition Of Done For Compatibility

Perubahan BPMN baru baru bisa disebut compatible jika minimal kondisi berikut terpenuhi:

1. semua message catch / receive task baru punya correlation path resmi
2. semua user task baru yang operator-facing terlihat dan bisa diselesaikan lewat task surface
3. semua gateway yang butuh outcome task atau callback punya input variable yang diisi lewat seam resmi, bukan implicit manual state
4. semua rule task punya implementasi runtime yang nyata
5. appeal path tidak lagi terpecah ambigu antara plan, BPMN, dan code
6. setidaknya ada execution tests untuk happy path dan failure branch utama
