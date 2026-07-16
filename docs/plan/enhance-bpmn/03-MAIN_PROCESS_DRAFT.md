# Main Process Draft

## Purpose

Dokumen ini menurunkan blueprint menjadi draft main process yang hampir siap digambar ke BPMN final.

Fokus dokumen ini:

- urutan node utama
- node mana yang hidup di main process
- node mana yang diturunkan ke subprocess
- event dan message mana yang harus terlihat di diagram utama

Dokumen ini sengaja belum menjadi XML BPMN. Tujuannya agar kita bisa mengunci struktur proses terlebih dahulu sebelum edit `.bpmn`.

## Draft Main Process

### Proposed process id

- `regulatoryEnforcementCase`

### Start semantics

#### Start event

- `Message Start Event`
- label: `Case Created`
- incoming semantic: case sudah lolos domain creation dan siap masuk orchestration

#### Main process variables

Main process hanya menyimpan variable korelasi/routing yang ringan:

- `caseId`
- `jurisdictionCode`
- `caseClassification`
- `riskScore`
- `workflowVersion`
- `requiresExternalEvidence`
- `requiresFinancialAnalysis`
- `requiresFieldInspection`
- `appealWindowExpiresAt`

## Node-by-Node Flow

### 1. Message Start Event - Case Created

Output:

- instance dibuat
- process variable awal diinisialisasi

### 2. Service Task - Pre-Triage Validation & Routing Enrichment

Lane:

- `Workflow / System`

Responsibility:

- validasi bahwa data case minimal tersedia
- enrich variable routing dari source of truth domain
- fail loud jika case tidak lagi valid untuk diproses

Outgoing:

- ke `Exclusive Gateway - Intake Valid?`

### 3. Exclusive Gateway - Intake Valid?

Branches:

- `No`
  - menuju `Terminate End Event - Intake Rejected`
- `Yes`
  - menuju `User Task - Triage Case`

Business meaning:

- ada jalur hard-stop untuk intake yang ditarik kembali atau terbukti tidak layak diteruskan

### 4. User Task - Triage Case

Lane:

- `Intake & Triage`

Candidate groups:

- `TRIAGE_OFFICER`
- `SUPERVISOR`

Expected outputs:

- triage disposition
- selected investigation tracks
- urgency flag
- optional hold recommendation

Attached events:

- non-interrupting `Timer Boundary Event - Triage SLA Warning`
- non-interrupting `Escalation Boundary Event - Triage Supervisor Attention`

Outgoing:

- ke `Inclusive Gateway - Select Investigation Tracks`

### 5. Inclusive Gateway - Select Investigation Tracks

Possible paths:

1. `Field Investigation Required`
2. `Financial Analysis Required`
3. `External Evidence Request Required`
4. `Legal Advisory Required`

Outgoing:

- seluruh path yang aktif dikonsolidasikan ke subprocess `Investigation & Evidence Collection`

Why this stays in main process:

- ini keputusan routing utama dan sebaiknya terlihat jelas di diagram utama

### 6. Embedded Subprocess - Investigation & Evidence Collection

Purpose:

- menangani orkestrasi investigasi asynchronous dan loop evidence

Visible contract in main process:

- masuk setelah track selection
- keluar hanya ketika evidence dianggap cukup atau case di-escalate/cancel melalui jalur lain

Exceptional interactions:

- dapat melempar escalation ke event subprocess
- dapat memicu `GlobalHoldSignal`

### 7. Embedded Subprocess - Recommendation & Multi-Party Review

Purpose:

- menghasilkan recommendation yang sudah melewati review yang dibutuhkan

Visible contract in main process:

- masuk setelah investigasi memadai
- keluar saat recommendation approved

### 8. Embedded Subprocess - Decision & Sanction Publication

Purpose:

- memutuskan ada/tidaknya sanction
- jika ada sanction, melakukan publication transaction

Visible contract in main process:

- keluar ke salah satu dari dua kondisi:
  - `No Active Sanction Path`
  - `Sanction Active Path`

Suggested modeling note:

- jika decision menyimpulkan tidak ada pelanggaran, subprocess bisa keluar ke jalur penutupan tanpa enforcement monitoring
- jika sanction diterbitkan, subprocess keluar ke appeal window

### 9. Event-Based Gateway - Wait for Appeal or Expiry

Incoming:

- dari `Decision & Sanction Publication`

Outgoing waiting events:

1. `Intermediate Message Catch Event - Appeal Filed`
2. `Intermediate Timer Catch Event - Appeal Period Expired`
3. `Intermediate Signal Catch Event - Global Hold`

Meaning:

- proses benar-benar menunggu kejadian berikutnya, bukan membaca variable statis

Branch behavior:

- `Appeal Filed` -> `Embedded Subprocess - Appeal Handling`
- `Appeal Period Expired` -> lanjut ke `Embedded Subprocess - Enforcement Monitoring` atau ke closure bila tidak ada sanction
- `Global Hold` -> ke event subprocess / hold handling

### 10. Embedded Subprocess - Appeal Handling

Purpose:

- memproses appeal sebagai bagian lifecycle case yang sama

Main-process contract:

- outcome `reject appeal` -> kembali ke jalur sanction/enforcement normal
- outcome `amend decision` -> kembali ke jalur corrective publication / enforcement update
- outcome `revoke decision` -> kembali ke corrective closure path

Important note:

- `revoke decision` tidak meng-terminate instance secara langsung
- ia harus melewati jalur closure yang eksplisit agar audit, correction, dan cleanup flow tetap terlihat

### 11. Corrective Closure Path

Purpose:

- menangani path setelah appeal membatalkan dasar decision atau sanction

Candidate structure:

1. `Service Task - Prepare Corrective Closure`
2. optional `Send Task - Send Revocation Notice`
3. optional `Receive Task - Await Revocation Ack`
4. `End Event - Case Closed Without Active Sanction`

Why this matters:

- memberi rumah yang jelas untuk outcome `revoke decision`
- menghindari terminate yang terlalu brutal untuk alur yang masih butuh side effect korektif

### 12. Embedded Subprocess - Enforcement Monitoring

Entry conditions:

- appeal period expired without overturning decision
- sanction masih aktif dan perlu monitoring

Exit conditions:

- semua obligation selesai
- atau breach memicu jalur enforcement tambahan yang pada akhirnya kembali ke monitoring/closure

### 13. End Events

End events yang diusulkan di main process:

1. `Terminate End Event - Intake Rejected`
2. `End Event - Case Closed Without Sanction`
3. `End Event - Case Closed After Enforcement`

## Main Process Event Subprocess

### Event subprocess - Urgent Escalation / Supervisor Override

Trigger candidates:

- escalation dari investigation
- escalation dari triage
- signal `Global Hold`

Internal nodes:

1. event subprocess start
2. `User Task - Supervisor Override Review`
3. `Exclusive Gateway`
   - continue process
   - suspend path
   - cancel case

Suggested effect on main process:

- interrupting or non-interrupting behavior akan kita finalkan saat menggambar BPMN
- default recommendation:
  - escalation timer = non-interrupting
  - global hold = interrupting for affected active path

## External Interaction Visible On Main Diagram

Interaction yang sebaiknya terlihat pada diagram utama:

1. `Case Created` message start
2. `Appeal Filed` message catch
3. `Global Hold` signal catch

Interaction yang cukup terlihat di subprocess:

1. `ExternalEvidenceDelivered`
2. `SanctionRegistryAcknowledged`
3. `NotificationResultReceived`

Reason:

- diagram utama tetap readable
- detail message-heavy interaction dipindah ke subprocess yang relevan

## Main Process Shape Summary

Ringkasan urutan final yang saya rekomendasikan:

1. `Message Start Event - Case Created`
2. `Service Task - Pre-Triage Validation & Routing Enrichment`
3. `Exclusive Gateway - Intake Valid?`
4. `User Task - Triage Case`
5. `Inclusive Gateway - Select Investigation Tracks`
6. `Embedded Subprocess - Investigation & Evidence Collection`
7. `Embedded Subprocess - Recommendation & Multi-Party Review`
8. `Embedded Subprocess - Decision & Sanction Publication`
9. `Event-Based Gateway - Wait for Appeal or Expiry`
10. `Embedded Subprocess - Appeal Handling`
11. `Corrective Closure Path` when appeal revokes decision
12. `Embedded Subprocess - Enforcement Monitoring`
13. normal end events

## Questions Remaining For Diagram Draft

1. Apakah `Legal Advisory Required` perlu node eksplisit di main process, atau cukup diinvestasikan ke dalam subprocess investigasi?
2. Apakah jalur `No Active Sanction Path` keluar dari `Decision & Sanction Publication` langsung ke `Case Closed Without Sanction`, atau tetap melewati event-based gateway dengan appeal window?
3. Apakah `Global Hold` hanya berlaku setelah decision, atau juga dapat terjadi saat investigation?

## Recommendation

Rekomendasi saya untuk keputusan terakhir sebelum gambar BPMN:

1. `Legal Advisory Required` cukup tetap sebagai routing reason, bukan node utama terpisah di main process.
2. `No Active Sanction Path` tetap melewati appeal-aware logic hanya jika secara hukum masih ada objek yang bisa di-appeal; kalau tidak, langsung ke closure.
3. `Global Hold` boleh terjadi saat investigation maupun setelah decision, agar signal punya nilai operasional yang nyata.
