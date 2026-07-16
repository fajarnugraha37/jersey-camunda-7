# BPMN Blueprint

## Working Assumptions

Dokumen ini melanjutkan [ENHANCE_BPMN_PLAN.md](C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/plan/enhance-bpmn/ENHANCE_BPMN_PLAN.md) dengan asumsi kerja berikut:

1. `Appeal Handling` dipindahkan menjadi subprocess di dalam main process, bukan process terpisah.
2. `Business Rule Task` tetap kita masukkan secara nyata, tetapi implementasi awalnya boleh melalui mock adapter atau DMN-ready port.
3. `Decision & Sanction Publication` akan memakai transaction subprocess agar `cancel` dan `compensation` punya tempat yang masuk akal.

## Target Collaboration View

### Participant / Pool

Participant yang diusulkan:

1. `Sentinel Internal Case Team`
2. `External Evidence Provider`
3. `Sanction Registry`
4. `Appellant / External Party`
5. `Notification Service`

### Internal lanes

Di dalam participant `Sentinel Internal Case Team`, lane yang diusulkan:

1. `Intake & Triage`
2. `Investigation`
3. `Legal / Review`
4. `Decision Authority`
5. `Enforcement Ops`
6. `Workflow / System`

Catatan:

- lane dipakai untuk memperjelas ownership manusia dan sistem
- tidak semua lane harus bermakna sebagai identity boundary teknis
- task claim dan authorization tetap mengikuti role dan resource-level authorization yang sudah ada

## Main Process Blueprint

### Process name

- `regulatoryEnforcementCase`

### High-level flow

1. `Message Start Event` - `Case Created`
2. `Service Task` - `Pre-Triage Validation & Routing Enrichment`
3. `Exclusive Gateway` - `Intake Valid?`
4. `User Task` - `Triage Case`
5. `Inclusive Gateway` - `Select Investigation Tracks`
6. `Embedded Subprocess` - `Investigation & Evidence Collection`
7. `Embedded Subprocess` - `Recommendation & Multi-Party Review`
8. `Embedded Subprocess` - `Decision & Sanction Publication`
9. `Event-Based Gateway` - `Wait for Appeal or Expiry`
10. `Embedded Subprocess` - `Appeal Handling` when message path chosen
11. `Embedded Subprocess` - `Enforcement Monitoring`
12. `End Event` - `Case Closed`

### Step-by-step detail

#### 1. Message Start Event - Case Created

Reason:

- secara model ini lebih kaya daripada start event polos
- cocok bila nanti case creation atau workflow start di-trigger lewat domain event / command adapter

Runtime note:

- tidak harus berarti Kafka langsung memulai BPMN
- boleh tetap dimulai dari adapter internal yang memodelkan start sebagai message semantics

#### 2. Service Task - Pre-Triage Validation & Routing Enrichment

Lane:

- `Workflow / System`

Purpose:

- ambil data routing minimal dari domain
- set process variable ringan:
  - `caseId`
  - `jurisdictionCode`
  - `caseClassification`
  - `riskScore`
  - `requiresExternalEvidence`
  - `requiresFinancialAnalysis`
  - `requiresFieldInspection`

Constraint:

- jangan memindahkan source of truth ke variable
- hanya copy data routing yang memang diperlukan

#### 3. Exclusive Gateway - Intake Valid?

Paths:

- invalid or withdrawn -> `Terminate End Event`
- valid -> `User Task: Triage Case`

Use case:

- kasus dibatalkan sebelum triage penuh
- data legal basis tidak cukup

#### 4. User Task - Triage Case

Lane:

- `Intake & Triage`

Output:

- triage decision
- selected investigation tracks
- optional urgency flag

Boundary/event add-ons:

- non-interrupting `Escalation Boundary Event` bila SLA triage mendekati breach
- `Timer Boundary Event` untuk due date triage

#### 5. Inclusive Gateway - Select Investigation Tracks

Possible outgoing branches:

1. `Field Investigation Required`
2. `Financial Analysis Required`
3. `External Evidence Request Required`
4. `Legal Advisory Required`

Why inclusive:

- satu case bisa butuh satu, dua, atau seluruh track sekaligus

#### 6. Embedded Subprocess - Investigation & Evidence Collection

Lihat detail di section subprocess.

#### 7. Embedded Subprocess - Recommendation & Multi-Party Review

Lihat detail di section subprocess.

#### 8. Embedded Subprocess - Decision & Sanction Publication

Lihat detail di section subprocess.

#### 9. Event-Based Gateway - Wait for Appeal or Expiry

Waiting events:

1. `Intermediate Message Catch Event` - `Appeal Filed`
2. `Intermediate Timer Catch Event` - `Appeal Period Expired`
3. `Signal Catch Event` - `Emergency Hold`

Why event-based:

- secara bisnis proses memang menunggu salah satu kejadian
- ini jauh lebih natural daripada exclusive gateway berbasis variable

#### 10. Embedded Subprocess - Appeal Handling

Hanya dieksekusi jika message `Appeal Filed` diterima.

#### 11. Embedded Subprocess - Enforcement Monitoring

Masuk bila:

- sanction berlaku
- appeal selesai tanpa membatalkan sanction

#### 12. End Event - Case Closed

Normal end untuk penutupan lengkap setelah obligation selesai atau case ditutup tanpa sanction.

## Subprocess Blueprint

### A. Investigation & Evidence Collection

#### Goal

Meng-cover:

- parallel gateway
- receive task
- script task
- business rule task
- conditional event
- timer event
- escalation event

#### Internal flow

1. `Parallel Gateway` split
2. Branch A - `User Task: Assign Investigator`
3. Branch B - `Send Task: Request External Evidence`
4. Branch C - `User Task: Request Legal Advisory`
5. `Parallel Gateway` join for mandatory initial preparation
6. `Receive Task: Await External Evidence Package`
7. `User Task: Investigate Case`
8. non-interrupting `Timer Boundary Event` on `Investigate Case`
9. `Service Task: Record Investigation Escalation`
10. `Escalation Throw Event` to supervisor event subprocess
11. `Script Task: Compute Evidence Completeness Flags`
12. `Business Rule Task: Evaluate Evidence Sufficiency`
13. `Conditional Intermediate Event` - wait until `evidenceSufficient == true`
14. `Exclusive Gateway: Sufficient?`
15. if no -> loop back to additional evidence request
16. if yes -> end subprocess

#### Why this is meaningful

- evidence collection memang asynchronous
- sufficiency evaluation memang cocok untuk rule task
- escalation timer punya alasan bisnis nyata

### B. Recommendation & Multi-Party Review

#### Goal

Meng-cover:

- user task collaboration
- parallel review
- optional additional review branch
- revise loop

#### Internal flow

1. `User Task: Draft Recommendation`
2. `Parallel Gateway` split:
   - `User Task: Legal Review`
   - `User Task: Compliance Review`
3. `Inclusive Gateway`:
   - if high-risk -> `User Task: Supervisor Review`
   - else skip
4. `Exclusive Gateway: Review Outcome`
5. revise -> back to draft recommendation
6. approved -> end subprocess

### C. Decision & Sanction Publication

#### Goal

Meng-cover:

- business rule task
- transaction subprocess
- send / receive
- error event
- cancel event
- compensation event

#### Internal flow

1. `User Task: Approve Decision`
2. `Exclusive Gateway: Violation Proven?`
3. if no -> path to closure without sanction
4. if yes -> continue
5. `Business Rule Task: Determine Sanction Package`
6. `Transaction Subprocess: Publish Sanction`

Inside transaction subprocess:

1. `Service Task: Create Publication Package`
2. `Send Task: Send Sanction To Registry`
3. `Receive Task: Await Registry Acknowledgment`
4. `Parallel Gateway` split:
   - `Send Task: Send Notification Command`
   - `Service Task: Create Obligation Schedule`
5. `Parallel Gateway` join
6. `Receive Task: Await Notification Result`
7. `Cancel End Event` for controlled cancellation

Boundary events on transaction subprocess:

- `Error Boundary Event` - publication failed
- `Compensation Boundary/Event` - reverse reversible side effects

Compensation candidates:

- mark registry publication as reversed
- emit correction notification command

### D. Appeal Handling

#### Goal

Meng-cover:

- message event
- user task
- business rule task
- signal event
- corrective closure path if decision fully revoked

#### Internal flow

1. enter from appeal filed message
2. `User Task: Review Appeal Admissibility`
3. `Business Rule Task: Evaluate Appeal Grounds`
4. `Exclusive Gateway: Appeal Outcome`
   - reject
   - amend decision
   - revoke decision
5. if revoke and no remaining sanction -> route back to corrective closure logic in main process
6. `Signal Throw Event` - `Global Hold` if systemic issue discovered

### E. Enforcement Monitoring

#### Goal

Meng-cover:

- parallel monitoring
- timer reminders
- conditional completion

#### Internal flow

1. `Parallel Gateway` split:
   - `User Task: Monitor Payment Obligation`
   - `User Task: Monitor Corrective Action`
   - `User Task: Monitor Reporting Obligation`
2. non-interrupting `Timer Boundary Event` for reminder/escalation
3. `Conditional Intermediate Event` - all obligations complete
4. `Exclusive Gateway: Obligation Breach?`
5. yes -> route to additional enforcement action
6. no -> close case

### F. Event Subprocess - Urgent Escalation / Supervisor Override

#### Goal

Meng-cover:

- event subprocess
- escalation event
- conditional or signal start possibility

#### Trigger options

1. escalation from investigation timer
2. signal `Global Hold`
3. conditional trigger on extreme risk flag

#### Internal flow

1. `Start Event` of event subprocess
2. `User Task: Supervisor Review Override`
3. `Exclusive Gateway`
   - continue normal path
   - suspend case
   - cancel case

## Event Inventory

### Message events

Planned message names:

1. `CaseCreatedMessage`
2. `ExternalEvidenceDelivered`
3. `SanctionRegistryAcknowledged`
4. `AppealFiled`
5. `NotificationResultReceived`

### Timer events

Planned timers:

1. triage SLA timer
2. investigation escalation timer
3. appeal expiry timer
4. obligation reminder timer

### Error events

Planned error use:

1. sanction publication failed
2. task completion command violates domain invariant
3. mock external service returns unrecoverable error

### Signal events

Planned signal use:

1. `GlobalHoldSignal`
2. `ResumeCasesSignal` optional

### Cancel / compensation

Planned scope:

- only inside `Publish Sanction` transaction subprocess

## Data Mapping

### Data Object Reference

Objects to show on diagram:

1. `InvestigationPlan`
2. `EvidenceChecklist`
3. `RecommendationDraft`
4. `SanctionPackage`
5. `AppealDossier`

### Data Store Reference

Stores to show on diagram:

1. `Case Database`
2. `Evidence Object Store`
3. `Sanction Registry Store`
4. `Notification Store`

## Mock Integration Plan

### External Evidence Provider

Behavior:

- receives request
- after delay or command trigger, sends `ExternalEvidenceDelivered`

### Sanction Registry

Behavior:

- receives publication request
- can respond success or failure
- can be deterministic in tests

### Notification Service

Behavior:

- receives notification command
- sends result acknowledgment that is correlated back to the workflow

## Coverage Matrix

### Requested BPMN element coverage

- Exclusive Gateway:
  - intake validation
  - review outcome
  - decision outcome
  - appeal outcome
- Inclusive Gateway:
  - investigation track selection
  - optional high-risk reviewer
- Parallel Gateway:
  - investigation prep branches
  - notification + obligation creation
  - enforcement monitoring branches
- Event Based Gateway:
  - appeal filed vs expiry vs hold
- User Task:
  - triage, investigate, review, approve, appeal, monitor
- Service Task:
  - routing enrichment, escalation record, obligation creation
- Script Task:
  - compute evidence completeness flags
- Send Task:
  - request evidence, publish sanction, send notification
- Receive Task:
  - await evidence, await registry ack, await notification result
- Business Rule Task:
  - evidence sufficiency, sanction package, appeal grounds
- Message Event:
  - case created, evidence delivered, appeal filed, registry ack, notification result
- Timer Event:
  - triage SLA, investigation escalation, appeal expiry, reminders
- Error Event:
  - publication failure or invalid completion
- Signal Event:
  - global hold
- Terminate Event:
  - invalid intake or revoked case hard-stop
- Cancel Event:
  - publish sanction transaction cancellation
- Escalation Event:
  - supervisor override path
- Compensation Event:
  - reverse publication side effects
- Conditional Event:
  - evidence sufficient or obligations complete
- Sub Process:
  - investigation, recommendation, decision, appeal, enforcement
- Data Object Reference:
  - investigation and sanction artifacts
- Data Store Reference:
  - case DB, evidence store, registry, notification store
- Pool / Participant:
  - internal team plus external mocks

## Open Questions To Resolve Before BPMN Editing

1. Apakah `Legal Advisory` di investigation perlu user task tersendiri, atau cukup service/mock branch agar diagram tidak terlalu padat?
2. Apakah `Supervisor Review` pada recommendation sebaiknya inclusive branch atau event-subprocess-only escalation?

## Recommended Next Discussion Slice

Urutan diskusi paling efektif berikutnya:

1. kunci participant dan lane yang benar-benar ingin terlihat di diagram
2. kunci event inventory yang benar-benar akan hidup
3. kunci appeal outcome semantics
4. baru gambar ulang main process dan subprocess satu per satu

## Locked Decisions

Keputusan yang sudah dianggap dikunci untuk blueprint ini:

1. `GlobalHoldSignal` akan diimplementasikan sebagai signal nyata, bukan placeholder.
2. `NotificationResultReceived` wajib dimodelkan sekarang sebagai message/receive interaction.
3. Outcome appeal `revoke decision` kembali ke corrective closure path, bukan terminate langsung.
