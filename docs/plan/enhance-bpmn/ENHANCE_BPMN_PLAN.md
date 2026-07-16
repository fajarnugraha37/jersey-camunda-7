# Enhance BPMN Plan

## Purpose

Dokumen ini menjadi working plan untuk memperkaya BPMN Sentinel dari model yang saat ini masih linear menjadi orchestration yang lebih realistis dan cukup kaya untuk melatih elemen BPMN enterprise-grade.

Target diskusi saat ini:

- mempertahankan arsitektur **Opsi 2: main process + subprocess spesialis**
- memasukkan elemen BPMN yang diminta tanpa menjadikannya dekoratif
- menjaga boundary tetap sehat:
  - Camunda untuk orchestration
  - database domain tetap source of truth business state
  - external interaction boleh dimock

## Current State

### Active BPMN today

`sentinel-workflow/src/main/resources/bpmn/regulatory-enforcement-case.bpmn`

Current shape:

- `Start Event`
- `User Task` triage
- `User Task` investigation
- `Boundary Timer Event` pada investigation
- `Service Task` record escalation
- `User Task` review
- `User Task` decision approval
- `End Event`

`sentinel-workflow/src/main/resources/bpmn/decision-appeal-review.bpmn`

Current shape:

- `Start Event`
- `User Task` appeal review
- `End Event`

### Gap against requested BPMN coverage

Belum tercakup atau belum tercakup secara memadai:

- Gateway:
  - exclusive
  - inclusive
  - parallel
  - event-based
- Task:
  - service task yang lebih dari satu kasus
  - script task
  - send task
  - receive task
  - business rule task
- Event:
  - message
  - error
  - signal
  - terminate
  - cancel
  - escalation yang lebih kaya
  - compensation
  - conditional
- subprocess
- data object reference
- data store reference
- pool / participant

## Selected Direction

Kita memilih **Opsi 2**:

> Main process tetap readable, lalu kompleksitas BPMN diletakkan pada subprocess yang memang punya alasan bisnis.

Alasan memilih arah ini:

- lebih maintainable daripada satu diagram raksasa
- lebih realistis untuk modular monolith ini
- tetap memungkinkan coverage elemen BPMN luas
- lebih mudah diuji per area bisnis

## Proposed Target Process Shape

### Main process

Nama sementara:

- `regulatory-enforcement-case`

Main process diusulkan memegang alur tingkat atas:

1. case intake accepted
2. pre-triage validation
3. triage
4. investigation orchestration
5. recommendation and review
6. decision and sanction publication
7. appeal window
8. enforcement monitoring
9. closure or cancellation

### Proposed subprocesses

Subprocess yang diusulkan:

1. `Investigation & Evidence Collection`
2. `Recommendation & Multi-Party Review`
3. `Decision & Sanction Publication`
4. `Appeal Handling`
5. `Enforcement Monitoring`
6. `Urgent Escalation / Supervisor Override` as event subprocess

## BPMN Element Coverage Plan

### Gateways

#### Exclusive Gateway

Use cases:

- triage result: reject vs proceed
- review result: revise vs approve
- decision result: sanction vs close-without-sanction
- appeal result: uphold vs amend vs revoke

#### Inclusive Gateway

Use cases:

- a case may require one or more of:
  - financial analysis
  - field inspection
  - external evidence request
  - legal advisory

Inclusive gateway paling pas dipakai setelah triage atau saat investigation planning.

#### Parallel Gateway

Use cases:

- investigator assignment, legal prep, and evidence collection berjalan paralel
- setelah sanction issuance:
  - notification
  - registry publication
  - obligation schedule creation

#### Event-Based Gateway

Use cases:

- during appeal window, process waits for:
  - appeal filed message
  - deadline timer expires
- during external evidence wait:
  - evidence delivered message
  - timeout
  - cancellation signal

### Task types

#### User Task

Candidate tasks:

- triage case
- assign investigation plan
- collect evidence
- review recommendation
- approve decision
- review appeal
- confirm obligation completion

#### Service Task

Candidate tasks:

- persist escalation audit
- create obligation schedule
- publish sanction to external registry mock
- sync notification command
- reconcile workflow/domain mismatch marker

#### Script Task

Candidate tasks:

- derive routing flags from process variables
- normalize evidence sufficiency score
- compute simple branch flags from already persisted domain data

Constraint:

- script task hanya untuk transform ringan
- business rules utama tetap di application/domain layer

#### Send Task

Candidate tasks:

- send sanction publication request to external registry mock
- send evidence request to external provider mock
- send notification command to notification participant mock

#### Receive Task

Candidate tasks:

- wait for registry acknowledgment
- wait for external evidence delivery confirmation
- wait for notification delivery result if we want explicit callback

#### Business Rule Task

Candidate tasks:

- sanction matrix evaluation
- review-routing matrix
- appeal deadline override policy

Preferred implementation:

- DMN-backed if we want the element to be meaningful
- mockable decision table is acceptable for first increment

### Event types

#### Message Event

Use cases:

- appeal filed
- evidence delivered
- registry acknowledgment
- external cancellation / withdrawal

#### Timer Event

Use cases:

- investigation SLA escalation
- appeal window expiry
- obligation due-date reminder
- retry wait before external follow-up

#### Error Event

Use cases:

- external publication failed
- invalid domain command during workflow completion
- business precondition not met after a human task completion

#### Signal Event

Use cases:

- global policy freeze
- supervisor emergency hold
- mass resume after dependency recovery

Signal sebaiknya dipakai untuk cross-instance or broadcast-style semantics, bukan message biasa.

#### Terminate Event

Use cases:

- hard case rejection
- fraud-confirmed abort
- process ended due to invalidated legal basis

#### Cancel Event

Use cases:

- cancellation inside a transaction subprocess for sanction publication

Catatan:

- cancel event paling natural jika kita benar-benar memakai transaction subprocess

#### Escalation Event

Use cases:

- supervisor override needed
- investigation overdue but not yet failed
- appeal deadline passed and explicit override requested

#### Compensation Event

Use cases:

- revoke registry publication marker
- reverse notification side effects
- revert temporary workflow-owned publication artifact

Constraint:

- compensation tidak dipakai sebagai rollback seluruh database transaction

#### Conditional Event

Use cases:

- proceed when `evidenceSufficient == true`
- trigger special review when `caseRiskScore >= threshold`
- auto-route when all mandatory evidence types are present

### Subprocess usage

#### Embedded subprocess

Good for:

- investigation cycle
- recommendation and review cycle

#### Event subprocess

Good for:

- urgent escalation
- incident handling
- supervisor override path

#### Transaction subprocess

Good for:

- sanction publication and reversible side effects

### Data elements

#### Data Object Reference

Candidate objects:

- investigation plan
- evidence checklist
- recommendation draft
- sanction package
- appeal dossier

#### Data Store Reference

Candidate stores:

- case management database
- evidence object store
- sanction registry mock
- notification delivery store

### Pool / Participant

Participants proposed:

1. `Sentinel Case Team`
2. `External Evidence Provider` mock
3. `Sanction Registry` mock
4. `Appellant / External Party`
5. `Notification Service`

Guideline:

- participant dipakai untuk menegaskan message flow
- tidak semua participant harus menjadi engine runtime terpisah
- untuk increment awal, external participants boleh dimock melalui service task + message correlation

## Recommended Flow Blueprint

### Main process blueprint

1. `Start Event` - case opened
2. `Service Task` - pre-triage validation and routing enrichment
3. `Exclusive Gateway` - valid intake?
   - no -> `Terminate End Event`
   - yes -> continue
4. `User Task` - triage case
5. `Inclusive Gateway` - determine required investigation tracks
6. `Embedded Subprocess` - `Investigation & Evidence Collection`
7. `Embedded Subprocess` - `Recommendation & Multi-Party Review`
8. `Embedded/Transaction Subprocess` - `Decision & Sanction Publication`
9. `Event-Based Gateway` - wait appeal filed or appeal period expired
10. `Embedded Subprocess` - `Appeal Handling` when needed
11. `Embedded Subprocess` - `Enforcement Monitoring`
12. `End Event` - case closed

### Investigation & Evidence Collection subprocess

Candidate flow:

- `Parallel Gateway` split:
  - assign investigator
  - request evidence
  - request legal advisory
- `Receive Task` wait for evidence package
- `User Task` investigate
- `Script Task` compute derived completeness flags
- `Business Rule Task` evaluate sufficiency
- `Conditional Intermediate Event` when sufficiency threshold met
- `Exclusive Gateway` enough evidence?
  - no -> loop for more evidence
  - yes -> proceed
- non-interrupting `Timer Boundary Event` -> escalation service task
- `Escalation Event` -> supervisor review event subprocess

### Recommendation & Multi-Party Review subprocess

Candidate flow:

- `User Task` draft recommendation
- `Parallel Gateway` run:
  - legal review
  - compliance review
- `Inclusive Gateway` optional additional reviewer if classification high-risk
- `Exclusive Gateway` approve / revise
- if revise -> loop back to draft recommendation

### Decision & Sanction Publication subprocess

Candidate flow:

- `User Task` approve decision
- `Exclusive Gateway` violation proven?
  - no -> close without sanction
  - yes -> continue
- `Business Rule Task` determine sanction package
- `Transaction Subprocess`:
  - `Send Task` publish sanction to registry mock
  - `Receive Task` wait acknowledgment
  - `Error Boundary Event` on publication failure
  - `Cancel End Event` if publication transaction cancelled
  - `Compensation Event` reverse reversible side effects

### Appeal Handling subprocess

Candidate flow:

- `Message Start Event` or message catch after event-based gateway
- `User Task` review appeal
- `Business Rule Task` evaluate grounds
- `Exclusive Gateway` uphold / amend / revoke
- optional `Signal Event` if policy-wide hold must be broadcast

### Enforcement Monitoring subprocess

Candidate flow:

- `Parallel Gateway`:
  - monitor payment
  - monitor corrective action
  - monitor reporting obligation
- `Timer Event` for due-date reminders
- `Conditional Event` when all obligations complete
- `Exclusive Gateway` obligation breached?
  - yes -> reopen enforcement action
  - no -> close case

## Mocks and External Interaction

Mock external services are acceptable and recommended for this enhancement.

Proposed mocks:

1. `External Evidence Provider Mock`
   - sends evidence-ready message
2. `Sanction Registry Mock`
   - receives publication request
   - returns success or failure acknowledgment
3. `Notification Service Mock`
   - receives notification command
   - optionally returns result message

Mocking strategy:

- keep interaction explicit in BPMN via send/receive or message catch
- keep adapter implementation simple in Java
- do not hide these interactions behind undocumented shortcuts

## Implementation Strategy

### Phase A - Blueprint and BPMN ownership

Deliverables:

- finalized flow blueprint
- element-to-use-case matrix confirmed
- ownership between domain state and workflow state re-stated

### Phase B - BPMN model expansion

Deliverables:

- expanded `regulatory-enforcement-case.bpmn`
- expanded `decision-appeal-review.bpmn` or merged appeal subprocess
- updated BPMN validation tests

### Phase C - Runtime wiring

Deliverables:

- delegates / handlers for new service tasks
- message correlation adapters
- mock external service adapters
- task query/complete behavior updated if task surface changes

### Phase D - Verification

Deliverables:

- workflow tests for happy path and alternate branches
- escalation, timeout, error, compensation, and duplicate-completion coverage
- updated docs and runbooks

## Testing Expectations

Minimum workflow scenarios after enhancement:

1. intake rejected at exclusive gateway
2. inclusive gateway activates only selected investigation branches
3. parallel review branches join correctly
4. event-based gateway follows message path
5. event-based gateway follows timer path
6. evidence insufficiency loops correctly
7. escalation timer triggers non-interrupting escalation
8. sanction publication transaction fails and compensation path executes
9. appeal filed during window starts appeal subprocess
10. obligations complete and case closes
11. signal-based hold affects active process as designed
12. terminate path ends the instance immediately

## Design Constraints

We should keep these constraints explicit:

- do not move business source of truth into process variables
- do not use script task for core business rules
- do not use compensation as fake database rollback
- do not introduce BPMN elements only for checklist vanity
- do not couple every participant to a real external runtime before needed

## Open Decisions For Next Discussion

1. Apakah appeal tetap dipertahankan sebagai BPMN terpisah, atau dipindahkan menjadi subprocess di main process?
2. Apakah kita ingin benar-benar menambah DMN untuk `Business Rule Task`, atau cukup mock adapter dahulu?
3. Apakah `Notification Service` perlu dimodelkan sebagai participant eksplisit, atau cukup tetap di messaging runtime dan hanya muncul sebagai data/store concern?
4. Apakah `Sanction Publication` perlu transaction subprocess penuh pada increment pertama, atau boleh dimulai dari send/receive + compensation sederhana?
5. Apakah `Signal Event` akan dipakai untuk use case nyata seperti global hold, atau kita cukup menundanya agar tidak menjadi elemen artifisial?

## Recommended Next Step

Next step yang paling efektif:

1. finalize target subprocess boundaries
2. decide appeal as separate process vs embedded subprocess
3. decide whether DMN is in or mocked
4. draft lane-by-lane BPMN blueprint before editing `.bpmn`

Setelah keputusan itu jelas, implementasi BPMN bisa masuk dengan lebih aman dan testable.
