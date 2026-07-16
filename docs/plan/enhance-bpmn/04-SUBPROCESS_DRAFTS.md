# Subprocess Drafts

## Purpose

Dokumen ini merinci setiap subprocess yang sudah disetujui di:

- [ENHANCE_BPMN_PLAN.md](C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/plan/enhance-bpmn/ENHANCE_BPMN_PLAN.md)
- [02-BPMN_BLUEPRINT.md](C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/plan/enhance-bpmn/02-BPMN_BLUEPRINT.md)
- [03-MAIN_PROCESS_DRAFT.md](C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/plan/enhance-bpmn/03-MAIN_PROCESS_DRAFT.md)

Tujuannya adalah membuat setiap subprocess cukup konkret untuk segera diterjemahkan menjadi BPMN final, tanpa langsung terkunci ke detail XML.

## Locked Baseline

Baseline yang dipakai di dokumen ini:

1. `Appeal Handling` adalah subprocess di dalam lifecycle utama.
2. `Business Rule Task` masuk nyata dan boleh dimulai lewat mock adapter / DMN-ready port.
3. `Decision & Sanction Publication` memakai transaction subprocess.
4. `NotificationResultReceived` dimodelkan eksplisit sebagai message/receive interaction.
5. `GlobalHoldSignal` hidup sebagai signal operasional nyata.
6. Outcome appeal `revoke decision` kembali ke corrective closure path.
7. `No Active Sanction Path` langsung close kecuali secara hukum tetap appealable.
8. `Global Hold` bisa terjadi saat investigation maupun pasca-decision.
9. `Legal Advisory` tetap berada di subprocess, tidak menjadi node utama terpisah.

## A. Investigation & Evidence Collection

### Goal

Subprocess ini menangani:

- planning investigasi
- permintaan dan penerimaan evidence
- loop kekurangan evidence
- escalation saat SLA investigation melambat

### BPMN elements covered

- `Parallel Gateway`
- `Send Task`
- `Receive Task`
- `User Task`
- `Script Task`
- `Business Rule Task`
- `Conditional Intermediate Event`
- `Timer Boundary Event`
- `Escalation Event`

### Proposed flow

1. `Start`
2. `Parallel Gateway - Launch Investigation Tracks`
3. Branch 1: `User Task - Assign Investigator`
4. Branch 2: `Send Task - Request External Evidence`
5. Branch 3: `User Task - Prepare Internal Evidence Checklist`
6. Branch 4: `User Task - Request Legal Advisory`
7. `Parallel Gateway - Join Investigation Setup`
8. `Receive Task - Await External Evidence Package`
9. `User Task - Investigate Case`
10. non-interrupting `Timer Boundary Event - Investigation SLA Warning`
11. `Service Task - Record Investigation Escalation`
12. `Escalation Throw Event - Supervisor Review Needed`
13. `Script Task - Compute Evidence Completeness Flags`
14. `Business Rule Task - Evaluate Evidence Sufficiency`
15. `Conditional Intermediate Event - Wait For Sufficient Evidence`
16. `Exclusive Gateway - Evidence Sufficient?`
17. `No` -> `Send Task - Request Additional Evidence` -> back to `Receive Task - Await External Evidence Package`
18. `Yes` -> `End`

### Process variables used

- `caseId`
- `jurisdictionCode`
- `requiresExternalEvidence`
- `evidenceChecklistComplete`
- `evidenceSufficient`
- `investigationEscalated`
- `holdRequested`

### External interaction

- `External Evidence Provider` receives request
- `ExternalEvidenceDelivered` message is correlated back to the receive task

### Failure semantics

- jika evidence tidak pernah datang, timer/escalation path tetap menyalakan supervisor visibility
- jika investigation command gagal saat task completion, workflow tidak boleh diam-diam maju
- duplicate message `ExternalEvidenceDelivered` harus idempotent di adapter

### Test scenarios

1. investigation happy path
2. evidence kurang lalu loop sekali
3. evidence provider timeout memicu escalation
4. duplicate evidence delivery message tidak menggandakan side effect
5. `GlobalHoldSignal` saat subprocess aktif

## B. Recommendation & Multi-Party Review

### Goal

Subprocess ini memodelkan preparation dan review recommendation secara realistis tanpa langsung lompat ke decision.

### BPMN elements covered

- `User Task`
- `Parallel Gateway`
- `Inclusive Gateway`
- `Exclusive Gateway`

### Proposed flow

1. `Start`
2. `User Task - Draft Recommendation`
3. `Parallel Gateway - Launch Reviews`
4. Branch 1: `User Task - Legal Review`
5. Branch 2: `User Task - Compliance Review`
6. `Parallel Gateway - Join Mandatory Reviews`
7. `Inclusive Gateway - Additional Review Needed?`
8. optional branch: `User Task - Supervisor Review`
9. `Exclusive Gateway - Review Outcome`
10. `Revise` -> back to `User Task - Draft Recommendation`
11. `Approved` -> `End`

### Process variables used

- `caseId`
- `riskScore`
- `requiresSupervisorReview`
- `reviewOutcome`

### Failure semantics

- maker-checker tetap ditegakkan di command/domain layer
- workflow hanya mengorkestrasi review sequence
- jika review completion gagal karena domain invariant, task tetap dianggap belum selesai

### Test scenarios

1. recommendation approved without supervisor review
2. high-risk case triggers supervisor review
3. revise loop once then approve
4. maker-checker denial does not advance workflow

## C. Decision & Sanction Publication

### Goal

Subprocess ini menjadi tempat utama untuk elemen publication, transaction behavior, cancel, compensation, dan message acknowledgment.

### BPMN elements covered

- `User Task`
- `Business Rule Task`
- `Exclusive Gateway`
- `Transaction Subprocess`
- `Send Task`
- `Receive Task`
- `Error Boundary Event`
- `Cancel End Event`
- `Compensation Event`
- `Parallel Gateway`

### Proposed outer flow

1. `Start`
2. `User Task - Approve Decision`
3. `Exclusive Gateway - Violation Proven?`
4. `No` -> `End - No Active Sanction`
5. `Yes` -> `Business Rule Task - Determine Sanction Package`
6. `Transaction Subprocess - Publish Sanction`
7. `End - Sanction Active`

### Transaction subprocess - Publish Sanction

1. `Start`
2. `Service Task - Create Publication Package`
3. `Send Task - Send Sanction To Registry`
4. `Receive Task - Await Registry Acknowledgment`
5. `Parallel Gateway - Launch Post-Publication Actions`
6. Branch 1: `Send Task - Send Notification Command`
7. Branch 2: `Service Task - Create Obligation Schedule`
8. `Parallel Gateway - Join Post-Publication Actions`
9. `Receive Task - Await Notification Result`
10. `Cancel End Event - Publication Cancelled` for controlled abort
11. `End`

### Boundary and compensation behavior

Boundary on transaction subprocess:

- `Error Boundary Event - Publication Failed`

Compensation candidates:

1. reverse registry publication flag
2. emit correction notification
3. mark obligation schedule as voided before enforcement starts

### Process variables used

- `caseId`
- `decisionId`
- `sanctionPackageReady`
- `registryPublicationStatus`
- `notificationResultStatus`
- `obligationScheduleCreated`

### External interaction

- `Sanction Registry` mock returns `SanctionRegistryAcknowledged`
- `Notification Service` mock returns `NotificationResultReceived`

### Failure semantics

- registry ack missing keeps flow waiting
- registry failure hits error boundary path
- notification result is explicitly modeled, so missing notification outcome is visible in workflow state
- compensation only handles reversible side effects, not full database rollback

### Test scenarios

1. decision no-violation path
2. sanction publication happy path
3. registry failure triggers error boundary
4. notification result delayed but eventually arrives
5. notification result failure triggers corrective handling
6. duplicate notification result message is idempotent

## D. Appeal Handling

### Goal

Subprocess ini memodelkan post-decision appeal lifecycle sebagai bagian dari case instance yang sama.

### BPMN elements covered

- `Message-driven entry`
- `User Task`
- `Business Rule Task`
- `Exclusive Gateway`
- `Signal Throw Event`

### Proposed flow

1. `Start`
2. `User Task - Review Appeal Admissibility`
3. `Business Rule Task - Evaluate Appeal Grounds`
4. `Exclusive Gateway - Appeal Outcome`
5. `Reject Appeal` -> `End - Appeal Rejected`
6. `Amend Decision` -> `Service Task - Prepare Corrective Decision Update` -> `End - Corrective Update Required`
7. `Revoke Decision` -> `Service Task - Prepare Revocation Package` -> `Signal Throw/Event Or Output Mapping To Corrective Closure` -> `End - Revoke To Main Flow`
8. optional `Signal Throw Event - Global Hold` if systemic concern discovered

### Process variables used

- `caseId`
- `appealId`
- `appealGroundsValid`
- `appealOutcome`
- `globalHoldRequested`

### Failure semantics

- appeal admissibility and deadline rules stay in domain/application
- workflow only models branching and follow-up orchestration
- revoke decision does not kill the whole instance immediately

### Test scenarios

1. appeal rejected
2. appeal amends decision
3. appeal revokes decision and returns to corrective closure path
4. appeal triggers `GlobalHoldSignal`

## E. Enforcement Monitoring

### Goal

Subprocess ini menangani obligation follow-up dan closure readiness.

### BPMN elements covered

- `Parallel Gateway`
- `User Task`
- `Timer Boundary Event`
- `Conditional Intermediate Event`
- `Exclusive Gateway`

### Proposed flow

1. `Start`
2. `Parallel Gateway - Launch Monitoring Tracks`
3. Branch 1: `User Task - Monitor Payment Obligation`
4. Branch 2: `User Task - Monitor Corrective Action`
5. Branch 3: `User Task - Monitor Reporting Obligation`
6. `Parallel Gateway - Join Monitoring State`
7. non-interrupting `Timer Boundary Event - Obligation Reminder`
8. `Service Task - Record Monitoring Reminder`
9. `Conditional Intermediate Event - All Obligations Complete`
10. `Exclusive Gateway - Obligation Breach?`
11. `Yes` -> `User Task - Escalate Additional Enforcement Action`
12. `No` -> `End - Ready For Closure`

### Process variables used

- `caseId`
- `activeObligationCount`
- `allObligationsComplete`
- `obligationBreachDetected`

### Failure semantics

- reminder path should not interrupt normal monitoring
- closure only happens after domain confirms no active obligation remains

### Test scenarios

1. all obligations complete cleanly
2. reminder timer fires while obligations still open
3. breach path routes to additional enforcement action

## F. Event Subprocess - Urgent Escalation / Supervisor Override

### Goal

Subprocess ini menjadi rumah untuk escalation lintas tahap dan signal-based hold.

### BPMN elements covered

- `Event Subprocess`
- `Escalation Start`
- `Signal Start`
- `User Task`
- `Exclusive Gateway`

### Trigger candidates

1. escalation dari triage timer
2. escalation dari investigation timer
3. `GlobalHoldSignal`

### Proposed flow

1. `Start Event`
2. `User Task - Supervisor Override Review`
3. `Exclusive Gateway - Override Outcome`
4. `Continue Process`
5. `Suspend Case`
6. `Cancel Case`

### Process variables used

- `caseId`
- `overrideReason`
- `overrideOutcome`
- `suspendRequested`
- `cancelRequested`

### Failure semantics

- event subprocess harus jelas interrupting vs non-interrupting per trigger
- rekomendasi awal:
  - timer escalation = non-interrupting
  - `GlobalHoldSignal` = interrupting

### Test scenarios

1. investigation escalation creates supervisor task without killing active path
2. triage escalation path works the same way
3. `GlobalHoldSignal` interrupts active path and moves to override flow

## Suggested Message Inventory

### Message names

1. `CaseCreatedMessage`
2. `ExternalEvidenceDelivered`
3. `SanctionRegistryAcknowledged`
4. `NotificationResultReceived`
5. `AppealFiled`

### Correlation keys

Recommended:

- `caseId` as primary business correlation
- `decisionId` where sanction publication specifics matter
- `appealId` for appeal-specific follow-up if needed

## Suggested Signal Inventory

1. `GlobalHoldSignal`
2. `ResumeCasesSignal` optional future extension

## Draft Cross-Subprocess Contracts

### Investigation -> Recommendation

Exit contract:

- `evidenceSufficient == true`
- mandatory review inputs available

### Recommendation -> Decision

Exit contract:

- recommendation approved
- reviewer-required path completed

### Decision -> Appeal Window

Exit contract:

- either `noActiveSanction`
- or `sanctionActive`
- and publication side effects in a coherent state

### Appeal -> Corrective Closure / Enforcement

Exit contract:

- `reject` -> normal enforcement path
- `amend` -> corrective update path
- `revoke` -> corrective closure path

## Remaining Design Decisions

Masih tersisa keputusan yang layak dibahas sebelum BPMN XML:

1. apakah `Legal Advisory` tetap user task manual atau service/mock assisted review
2. apakah notification failure perlu loop retry eksplisit di BPMN atau cukup di messaging/runtime layer
3. apakah `ResumeCasesSignal` ingin disiapkan sejak awal atau ditunda

## Recommended Next Step

Langkah diskusi paling efektif setelah ini:

1. kunci behavior `notification failure`
2. kunci `Legal Advisory` manual vs assisted
3. lalu saya turunkan menjadi `05-BPMN_MESSAGE_AND_EVENT_MATRIX.md`
4. setelah itu baru mulai draft diagram atau implementasi `.bpmn`
