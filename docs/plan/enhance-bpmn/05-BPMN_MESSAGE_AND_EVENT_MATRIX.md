# BPMN Message And Event Matrix

## Purpose

Dokumen ini mengunci inventory message, event, signal, timer, dan branch korektif yang akan dipakai saat BPMN final digambar dan diimplementasikan.

Dokumen ini melanjutkan:

- [ENHANCE_BPMN_PLAN.md](C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/plan/enhance-bpmn/ENHANCE_BPMN_PLAN.md)
- [02-BPMN_BLUEPRINT.md](C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/plan/enhance-bpmn/02-BPMN_BLUEPRINT.md)
- [03-MAIN_PROCESS_DRAFT.md](C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/plan/enhance-bpmn/03-MAIN_PROCESS_DRAFT.md)
- [04-SUBPROCESS_DRAFTS.md](C:/Users/nugra/workspace/project/.jax-rs/.onboard/docs/plan/enhance-bpmn/04-SUBPROCESS_DRAFTS.md)

## Locked Decisions Reflected Here

1. `Legal Advisory` tetap dimodelkan sebagai `User Task` manual.
2. `NotificationResultReceived` wajib dimodelkan eksplisit di BPMN.
3. Jika notification result gagal, BPMN harus punya branch korektif yang terlihat.
4. Retry teknis level transport atau consumer tetap berada di messaging/runtime layer, bukan digambar penuh sebagai loop teknis BPMN.

## Participant Matrix

### Participants

1. `Sentinel Internal Case Team`
2. `External Evidence Provider`
3. `Sanction Registry`
4. `Appellant / External Party`
5. `Notification Service`

### Interaction summary

- `External Evidence Provider`
  - menerima request evidence
  - mengirim `ExternalEvidenceDelivered`
- `Sanction Registry`
  - menerima publication request
  - mengirim `SanctionRegistryAcknowledged`
- `Appellant / External Party`
  - mengirim `AppealFiled`
- `Notification Service`
  - menerima command notifikasi
  - mengirim `NotificationResultReceived`

## Message Inventory

### 1. CaseCreatedMessage

Type:

- BPMN `Message Start Event`

Producer:

- internal case creation adapter

Consumer:

- main process `regulatoryEnforcementCase`

Correlation key:

- `caseId`

Purpose:

- memulai orchestration setelah case domain valid dibuat

### 2. ExternalEvidenceDelivered

Type:

- BPMN `Intermediate Message Catch` or `Receive Task` completion signal

Producer:

- `External Evidence Provider`

Consumer:

- subprocess `Investigation & Evidence Collection`

Correlation key:

- `caseId`

Payload minimum:

- `caseId`
- `evidenceBatchId`
- `receivedAt`
- `providerReference`

Expected workflow effect:

- membuka jalur lanjut dari `Receive Task - Await External Evidence Package`

Failure note:

- duplicate delivery harus idempotent di message correlation adapter

### 3. SanctionRegistryAcknowledged

Type:

- BPMN `Intermediate Message Catch` or `Receive Task` completion signal

Producer:

- `Sanction Registry`

Consumer:

- transaction subprocess `Publish Sanction`

Correlation keys:

- `caseId`
- `decisionId`

Payload minimum:

- `caseId`
- `decisionId`
- `publicationStatus`
- `registryReference`
- `acknowledgedAt`

Expected workflow effect:

- jika success, lanjut ke post-publication actions
- jika failure semantic dikirim sebagai ack negatif, branch ke path error/korektif

### 4. NotificationResultReceived

Type:

- BPMN `Intermediate Message Catch` or `Receive Task`

Producer:

- `Notification Service`

Consumer:

- transaction subprocess `Publish Sanction`

Correlation keys:

- `caseId`
- `decisionId`

Payload minimum:

- `caseId`
- `decisionId`
- `notificationStatus`
- `deliveryChannel`
- `resultCode`
- `receivedAt`

Allowed statuses:

- `SUCCESS`
- `FAILED`

Expected workflow effect:

- `SUCCESS`
  - publication subprocess can complete normally
- `FAILED`
  - BPMN must branch to visible corrective handling

### 5. AppealFiled

Type:

- BPMN `Intermediate Message Catch Event`

Producer:

- `Appellant / External Party`

Consumer:

- main process at `Event-Based Gateway - Wait for Appeal or Expiry`

Correlation keys:

- `caseId`
- optional `decisionId`

Payload minimum:

- `caseId`
- `appealId`
- `filedAt`
- `appealGroundType`

Expected workflow effect:

- routes process into `Appeal Handling`

## Signal Inventory

### 1. GlobalHoldSignal

Type:

- BPMN `Signal Catch / Signal Throw`

Producer:

- supervisor override path
- appeal handling when systemic concern discovered
- potentially administrative operation

Consumer:

- active case instances that are in hold-applicable states

Primary scope:

- investigation stage
- post-decision stage

Expected workflow effect:

- interrupts normal active path for the affected subprocess or process segment
- enters `Urgent Escalation / Supervisor Override` event subprocess

Design note:

- signal dipakai karena semantics-nya lintas-instance / broadcast-capable
- jangan diganti menjadi message jika memang use case yang diinginkan adalah policy-wide hold

### 2. ResumeCasesSignal

Type:

- future extension

Status:

- not in initial implementation

Reason:

- tidak perlu dipaksa masuk pada increment pertama

## Timer Inventory

### 1. Triage SLA Warning

Type:

- non-interrupting `Timer Boundary Event`

Attached to:

- `User Task - Triage Case`

Expected effect:

- create supervisor visibility without killing active triage

### 2. Investigation SLA Warning

Type:

- non-interrupting `Timer Boundary Event`

Attached to:

- `User Task - Investigate Case`

Expected effect:

- trigger escalation recording and supervisor review path

### 3. Appeal Period Expiry

Type:

- `Intermediate Timer Catch Event`

Attached location:

- event-based wait after decision/sanction publication

Expected effect:

- if no appeal, continue to enforcement monitoring or direct closure

### 4. Obligation Reminder

Type:

- non-interrupting `Timer Boundary Event`

Attached to:

- monitoring tasks in `Enforcement Monitoring`

Expected effect:

- create reminder/escalation without resetting existing monitoring state

## Error Inventory

### 1. Publication Failed

Type:

- `Error Boundary Event`

Attached to:

- transaction subprocess `Publish Sanction`

Raised by:

- service task or integration adapter when registry publication cannot proceed

Expected effect:

- route to correction / retry-decision / manual intervention path

### 2. Invalid Domain Completion

Type:

- semantic runtime failure surfaced on task completion path

Modeling approach:

- not every domain exception needs a visible BPMN error event
- only failures that truly alter orchestration need dedicated BPMN boundary handling

Recommendation:

- keep BPMN error handling explicit for publication/external integration paths
- keep ordinary domain validation failure as failed completion without token advancement

## Conditional Event Inventory

### 1. Evidence Sufficient

Type:

- `Conditional Intermediate Event`

Subprocess:

- `Investigation & Evidence Collection`

Condition:

- `evidenceSufficient == true`

Purpose:

- make readiness explicit in orchestration rather than hiding it as only a gateway variable

### 2. All Obligations Complete

Type:

- `Conditional Intermediate Event`

Subprocess:

- `Enforcement Monitoring`

Condition:

- `allObligationsComplete == true`

Purpose:

- open path to closure only when enforcement obligations truly finish

## Escalation Inventory

### 1. Investigation Escalation

Type:

- `Escalation Throw Event`

Triggered by:

- non-interrupting timer path during investigation

Handled by:

- event subprocess `Urgent Escalation / Supervisor Override`

Expected effect:

- supervisor task is created
- original investigation path may continue unless separately suspended

### 2. Triage Escalation

Type:

- `Escalation Boundary Event`

Triggered by:

- triage nearing SLA breach

Handled by:

- supervisor attention path

## Notification Failure Branch

### Why this is modeled

User decision already locked this:

- `NotificationResultReceived` must be explicit
- failure result must be visible in BPMN

### Proposed branch

Inside transaction subprocess `Publish Sanction`:

1. `Receive Task - Await Notification Result`
2. `Exclusive Gateway - Notification Result OK?`
3. `SUCCESS`
   - proceed to normal end of transaction subprocess
4. `FAILED`
   - `User Task - Review Notification Failure`
   - `Exclusive Gateway - Failure Action`
   - branch A: `Send Task - Resend Notification Command`
   - branch B: `Service Task - Mark Manual Notification Required`
   - branch C: `Cancel End Event - Abort Publication Finalization`

### Boundary between BPMN and runtime retry

What stays in BPMN:

- business-visible result of notification
- manual or policy-driven correction path

What stays in runtime:

- technical retry on Kafka transport
- consumer retry
- dead-letter handling
- transient broker/network recovery

This keeps BPMN meaningful and avoids drawing technical middleware loops as business flow.

## Corrective Closure Event Mapping

### Revoke decision path

When appeal outcome is `revoke`:

1. appeal subprocess exits with explicit revoke outcome
2. main process enters `Corrective Closure Path`
3. optional messages may be sent:
   - revocation notice
   - correction notice
4. process closes through explicit end event

Reason:

- closure remains auditable
- side effects remain visible
- no hidden terminate shortcut

## Suggested Event Naming Conventions

### Human-readable BPMN labels

Use readable labels such as:

- `Case Created`
- `Appeal Filed`
- `Await Notification Result`
- `Investigation SLA Warning`
- `Global Hold`

### Technical message names

Use stable names such as:

- `CaseCreatedMessage`
- `ExternalEvidenceDelivered`
- `SanctionRegistryAcknowledged`
- `NotificationResultReceived`
- `AppealFiled`

## Matrix By Subprocess

### Main process

- start message: `CaseCreatedMessage`
- event-based waits:
  - `AppealFiled`
  - appeal timer expiry
  - `GlobalHoldSignal`

### Investigation & Evidence Collection

- send: evidence request
- receive/message: `ExternalEvidenceDelivered`
- timer: investigation SLA
- escalation: investigation escalation
- conditional: evidence sufficient

### Recommendation & Multi-Party Review

- no external message required in first increment

### Decision & Sanction Publication

- send: sanction publication request
- receive/message: `SanctionRegistryAcknowledged`
- send: notification command
- receive/message: `NotificationResultReceived`
- error boundary: publication failure
- cancel/compensation path inside transaction subprocess

### Appeal Handling

- entry message already consumed in main event-based gateway
- optional signal throw: `GlobalHoldSignal`

### Enforcement Monitoring

- timer: reminder
- conditional: all obligations complete

## Minimal Implementation Guidance

When we start editing BPMN and Java:

1. each message event must have one clear correlation strategy
2. each timer must map to one explicit business expectation
3. each signal must have a justified operational use
4. failure branches should represent business-visible correction, not low-level transport retry

## Remaining Open Items

1. apakah `Notification Failure` branch perlu loop satu kali di BPMN, atau cukup one-shot decision path
2. apakah `SanctionRegistryAcknowledged` negative acknowledgment dimodelkan sebagai message result branch atau error boundary trigger
3. apakah `CaseCreatedMessage` benar-benar diwujudkan sebagai message start pada implementasi awal, atau internal adapter tetap start langsung sambil mempertahankan notation message-start di model

## Recommended Next Step

Setelah matrix ini, langkah paling pas adalah:

1. kunci `Notification Failure` behavior
2. kunci negative ack semantics untuk registry
3. lalu saya buat `06-DIAGRAM_BUILD_SEQUENCE.md`
4. setelah itu kita siap mulai implementasi BPMN secara bertahap
