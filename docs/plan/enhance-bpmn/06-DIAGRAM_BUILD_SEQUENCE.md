# Diagram Build Sequence

## Purpose

Dokumen ini mengubah hasil diskusi menjadi urutan build BPMN yang langsung bisa dipakai saat mulai menggambar dan mengimplementasikan diagram.

Tujuan utamanya:

- mengurangi bolak-balik diskusi saat edit BPMN dimulai
- memastikan elemen yang kompleks ditambahkan dengan urutan yang aman
- membuat review BPMN lebih cepat karena setiap langkah punya outcome yang jelas

## Locked Recommendations

Rekomendasi yang dipakai tanpa dibuka ulang:

1. `Appeal Handling` tetap subprocess di main process.
2. `Business Rule Task` masuk nyata dengan mock adapter / DMN-ready port.
3. `Decision & Sanction Publication` memakai transaction subprocess.
4. `GlobalHoldSignal` hidup sebagai signal nyata.
5. `NotificationResultReceived` dimodelkan eksplisit sebagai receive/message.
6. `NotificationResultReceived = FAILED` punya branch korektif BPMN yang terlihat.
7. `Notification Failure` boleh satu kali resend di BPMN, lalu pindah ke manual path jika gagal lagi.
8. `SanctionRegistryAcknowledged` negatif dimodelkan sebagai message-result branch, bukan error boundary.
9. `Legal Advisory` tetap `User Task` manual.
10. `revoke decision` kembali ke corrective closure path.

## Build Order

### Step 1 - Rebuild main process skeleton

Target:

- bentuk ulang `regulatory-enforcement-case.bpmn` agar tidak lagi linear

Node yang harus ada dulu:

1. `Message Start Event - Case Created`
2. `Service Task - Pre-Triage Validation & Routing Enrichment`
3. `Exclusive Gateway - Intake Valid?`
4. `User Task - Triage Case`
5. `Inclusive Gateway - Select Investigation Tracks`
6. placeholder subprocess:
   - `Investigation & Evidence Collection`
   - `Recommendation & Multi-Party Review`
   - `Decision & Sanction Publication`
   - `Appeal Handling`
   - `Enforcement Monitoring`
7. `Event-Based Gateway - Wait for Appeal or Expiry`
8. normal end events
9. `Terminate End Event - Intake Rejected`

Outcome:

- main flow besar sudah terbaca walau detail subprocess belum dimasukkan

### Step 2 - Add event subprocess for escalation and hold

Target:

- tambahkan `Urgent Escalation / Supervisor Override` event subprocess

Node minimum:

1. escalation start
2. signal start `Global Hold`
3. `User Task - Supervisor Override Review`
4. `Exclusive Gateway - Override Outcome`

Outcome:

- struktur escalation lintas tahap sudah punya rumah

### Step 3 - Detail subprocess Investigation & Evidence Collection

Target:

- masukkan async evidence flow dan sufficiency loop

Node minimum:

1. `Parallel Gateway`
2. `Send Task - Request External Evidence`
3. `Receive Task - Await External Evidence Package`
4. `User Task - Investigate Case`
5. `User Task - Request Legal Advisory`
6. `Script Task - Compute Evidence Completeness Flags`
7. `Business Rule Task - Evaluate Evidence Sufficiency`
8. `Conditional Intermediate Event - Evidence Sufficient`
9. `Timer Boundary Event - Investigation SLA Warning`
10. `Escalation Throw Event`

Outcome:

- subprocess paling kaya event dasar sudah hidup

### Step 4 - Detail subprocess Recommendation & Multi-Party Review

Target:

- masukkan jalur maker-review-revise yang rapi

Node minimum:

1. `User Task - Draft Recommendation`
2. `Parallel Gateway`
3. `User Task - Legal Review`
4. `User Task - Compliance Review`
5. `Inclusive Gateway - Additional Review Needed?`
6. optional `User Task - Supervisor Review`
7. `Exclusive Gateway - Review Outcome`

Outcome:

- review collaboration dan revise loop sudah jelas

### Step 5 - Detail subprocess Decision & Sanction Publication

Target:

- bangun subprocess yang paling sensitif lebih hati-hati karena di sini ada transaction, send/receive, cancel, compensation, dan notification result

Outer nodes:

1. `User Task - Approve Decision`
2. `Exclusive Gateway - Violation Proven?`
3. `Business Rule Task - Determine Sanction Package`
4. `Transaction Subprocess - Publish Sanction`

Inside transaction subprocess:

1. `Service Task - Create Publication Package`
2. `Send Task - Send Sanction To Registry`
3. `Receive Task - Await Registry Acknowledgment`
4. `Parallel Gateway`
5. `Send Task - Send Notification Command`
6. `Service Task - Create Obligation Schedule`
7. `Receive Task - Await Notification Result`
8. `Exclusive Gateway - Notification Result OK?`
9. `Send Task - Resend Notification Command`
10. `User Task - Review Notification Failure`
11. `Service Task - Mark Manual Notification Required`
12. `Cancel End Event`

Boundary events:

1. `Error Boundary Event - Publication Failed`
2. compensation path for reversible side effects

Outcome:

- elemen BPMN paling advanced masuk dengan alasan bisnis yang kuat

### Step 6 - Detail appeal-aware part on main flow

Target:

- rapikan jalur setelah decision

Node minimum:

1. `Event-Based Gateway - Wait for Appeal or Expiry`
2. `Intermediate Message Catch Event - Appeal Filed`
3. `Intermediate Timer Catch Event - Appeal Period Expired`
4. `Intermediate Signal Catch Event - Global Hold`
5. `Appeal Handling` subprocess entry
6. `Corrective Closure Path`

Outcome:

- appeal dan expiry semantics selesai di level main process

### Step 7 - Detail subprocess Appeal Handling

Target:

- masukkan branching hasil appeal dengan benar

Node minimum:

1. `User Task - Review Appeal Admissibility`
2. `Business Rule Task - Evaluate Appeal Grounds`
3. `Exclusive Gateway - Appeal Outcome`
4. amend branch
5. revoke branch
6. `Signal Throw Event - Global Hold` optional but real

Outcome:

- appeal tidak lagi sekadar satu user task

### Step 8 - Detail subprocess Enforcement Monitoring

Target:

- tutup lifecycle sanction secara operational

Node minimum:

1. `Parallel Gateway`
2. `User Task - Monitor Payment Obligation`
3. `User Task - Monitor Corrective Action`
4. `User Task - Monitor Reporting Obligation`
5. `Timer Boundary Event - Obligation Reminder`
6. `Conditional Intermediate Event - All Obligations Complete`
7. `Exclusive Gateway - Obligation Breach?`

Outcome:

- closure case punya jalur monitoring yang masuk akal

### Step 9 - Add pool / participant and message flows

Target:

- pindahkan diagram dari process-only menjadi collaboration-aware

Participants:

1. `Sentinel Internal Case Team`
2. `External Evidence Provider`
3. `Sanction Registry`
4. `Appellant / External Party`
5. `Notification Service`

Message flows to draw:

1. case created
2. evidence request / evidence delivered
3. sanction publication / registry acknowledgment
4. notification command / notification result
5. appeal filed

Outcome:

- interaksi external service terlihat eksplisit dan siap direview

### Step 10 - Add data object/store references

Target:

- tambahkan data artifacts yang membantu pembacaan diagram tanpa membuatnya penuh

Data objects:

1. `InvestigationPlan`
2. `EvidenceChecklist`
3. `RecommendationDraft`
4. `SanctionPackage`
5. `AppealDossier`

Data stores:

1. `Case Database`
2. `Evidence Object Store`
3. `Sanction Registry Store`
4. `Notification Store`

Outcome:

- diagram lebih enterprise-grade dan informatif

## Implementation Slices

### Slice 1

- rebuild main process skeleton
- add event subprocess
- keep subprocesses as placeholders first

### Slice 2

- fully detail `Investigation & Evidence Collection`
- fully detail `Recommendation & Multi-Party Review`

### Slice 3

- fully detail `Decision & Sanction Publication`
- especially notification failure branch

### Slice 4

- fully detail `Appeal Handling`
- add corrective closure path

### Slice 5

- fully detail `Enforcement Monitoring`
- add participants and message flows
- add data objects/stores

## Review Checklist For BPMN

Saat BPMN selesai digambar, review minimum harus memeriksa:

1. apakah semua gateway punya alasan bisnis, bukan sekadar dekorasi
2. apakah `NotificationResultReceived` terlihat jelas sebagai receive/message
3. apakah branch notification failure terbaca dan tidak hanya diserahkan ke Kafka runtime
4. apakah `GlobalHoldSignal` punya start/throw/catch yang konsisten
5. apakah `revoke decision` memang kembali ke corrective closure path
6. apakah `Cancel` dan `Compensation` hanya dipakai di area yang masuk akal
7. apakah diagram utama masih terbaca tanpa zoom ekstrem
8. apakah subprocess boundaries jelas
9. apakah participant external tidak terlalu ramai tetapi tetap eksplisit

## What Not To Do

1. jangan masukkan semua retry teknis Kafka ke BPMN
2. jangan jadikan process variable sebagai source of truth domain
3. jangan pakai compensation untuk pura-pura rollback database global
4. jangan paksa semua participant menjadi runtime nyata pada increment pertama
5. jangan biarkan diagram utama menjadi dump semua detail subprocess

## Ready State Before BPMN Editing

Kita siap mulai edit `.bpmn` jika kondisi ini terpenuhi:

1. build order ini diterima
2. review checklist ini diterima
3. tidak ada keputusan desain besar yang masih menggantung

Status saat ini:

- condition 1: ready
- condition 2: ready
- condition 3: ready enough to start implementation
