# Karate Flow Coverage Matrix

## Purpose

Dokumen ini mendefinisikan **seluruh flow** yang akan dicakup oleh automation testing Karate-centric untuk Sentinel.

Prinsipnya:

1. semua flow bisnis utama harus punya scenario
2. endpoint saja tidak cukup; branch penting dan side effect penting juga harus diuji
3. running application tetap menjadi target utama

## Coverage Groups

## 1. Platform Readiness

### Flow 1.1 Health

Coverage:

1. `GET /health` returns `UP`
2. dependency surface tampil
3. negative observation bila dependency down bisa ditambahkan kemudian sebagai ops test

Priority:

1. smoke
2. regression

## 2. Authentication

### Flow 2.1 Keycloak Login

Coverage:

1. login tiap role utama
2. token valid dapat dipakai ke API
3. token missing -> `401`

Roles minimum:

1. `intake-jkt`
2. `triage-jkt`
3. `investigator-jkt`
4. `reviewer-jkt`
5. `decision-jkt`
6. `appeal-jkt`
7. `supervisor-jkt`
8. `auditor-jkt`
9. `system-admin`

Priority:

1. smoke
2. regression

## 3. Report Intake

### Flow 3.1 Create Report

Coverage:

1. create report happy path
2. read-back by `reportId`
3. invalid payload basic validation

### Flow 3.2 Triage Report

Coverage:

1. triage happy path
2. stale `expectedVersion` -> conflict
3. unauthorized actor -> forbidden

Priority:

1. smoke
2. regression

## 4. Case Core Flow

### Flow 4.1 Create Case

Coverage:

1. create case from triaged report
2. case number format
3. read-back by `caseId`

### Flow 4.2 List Cases

Coverage:

1. quick search `q`
2. field search
3. sorting
4. cursor pagination
5. authorization-filtered visibility

### Flow 4.3 Get Case

Coverage:

1. authorized actor can read
2. unauthorized actor gets `403`
3. non-existing case gets `404`

Priority:

1. smoke for create/get
2. regression for list/filter

## 5. Case Assignment

### Flow 5.1 Initial Assignment

Coverage:

1. assign case happy path
2. case snapshot updated

### Flow 5.2 Assignment Rotation

Coverage:

1. reassign case to another assignee
2. previous assignment released
3. exactly one active assignment remains

### Flow 5.3 No-Effect Assignment Conflict

Coverage:

1. assign to same unit and same assignee
2. `409 NO_EFFECT_ASSIGNMENT`

Priority:

1. regression
2. full for DB assertion

## 6. Case Relationship Lineage

### Flow 6.1 Create Relationship

Coverage:

1. parent-child merge link
2. derivation link

### Flow 6.2 List Descendants

Coverage:

1. recursive descendants
2. `maxDepth`
3. type filter

### Flow 6.3 List Ancestors

Coverage:

1. recursive ancestors

### Flow 6.4 Relationship Conflict Paths

Coverage:

1. duplicate relationship -> `CASE_RELATIONSHIP_ALREADY_EXISTS`
2. transitive cycle -> `CASE_RELATIONSHIP_CYCLE`

Priority:

1. regression
2. full

## 7. Case Transition and Audit

### Flow 7.1 Valid Transition

Coverage:

1. valid state transition
2. case version increments
3. audit trail visible

### Flow 7.2 Invalid Transition

Coverage:

1. invalid state jump -> `CASE_TRANSITION_NOT_ALLOWED`
2. stale version -> `CONCURRENT_MODIFICATION`

Priority:

1. regression

## 8. Evidence Lifecycle

### Flow 8.1 Create Upload Session

Coverage:

1. create upload session
2. evidence metadata pending
3. upload URL returned

### Flow 8.2 Direct Binary Upload

Coverage:

1. upload binary to presigned URL

### Flow 8.3 Finalize Evidence Version

Coverage:

1. finalize success
2. evidence version visible by API

### Flow 8.4 Get Evidence

Coverage:

1. read evidence metadata

### Flow 8.5 Download Session

Coverage:

1. create download session
2. download URL returned
3. audit event emitted

### Flow 8.6 Negative Paths

Coverage:

1. checksum mismatch
2. unauthorized download
3. duplicate finalize

Priority:

1. regression for happy path
2. full for negative and audit assertions

## 9. Recommendation Flow

### Flow 9.1 Create Recommendation

Coverage:

1. create recommendation during investigation

### Flow 9.2 Submit Recommendation

Coverage:

1. submit success

### Flow 9.3 Review Recommendation

Coverage:

1. reviewer approves
2. maker-checker denial where relevant

Priority:

1. regression

## 10. Decision Flow

### Flow 10.1 Create Decision

Coverage:

1. create decision with and without sanction

### Flow 10.2 Approve Decision

Coverage:

1. normal approval
2. maker-checker guard

### Flow 10.3 Publish Decision

Coverage:

1. publish success
2. post-publish state visible

### Flow 10.4 Decision Lock Conflict

Coverage:

1. hold row lock externally
2. approve returns `409 DECISION_LOCKED`
3. retry after lock release succeeds

Priority:

1. regression for normal path
2. full for lock conflict

## 11. Appeal Flow

### Flow 11.1 Create Appeal

Coverage:

1. normal appeal submission
2. case moves to `UNDER_APPEAL`

### Flow 11.2 Late Appeal Override

Coverage:

1. late appeal without override -> conflict
2. late appeal with supervisor override -> success

### Flow 11.3 Decide Appeal

Coverage:

1. denied appeal
2. granted appeal
3. sanction cancellation effect for granted appeal

Priority:

1. regression
2. full

## 12. Workflow Task Flow

### Flow 12.1 List Tasks

Coverage:

1. task visibility by role
2. query/filter/sort/cursor

### Flow 12.2 Claim Task

Coverage:

1. claim success
2. duplicate claim semantics if relevant

### Flow 12.3 Complete Task

Coverage:

1. complete success
2. duplicate completion safety

Priority:

1. regression

## 13. Workflow Reconciliation

### Flow 13.1 List Issues

Coverage:

1. quick search
2. field search
3. sorting
4. cursor pagination
5. supervisor-only access

### Flow 13.2 Auto Repair Missing Correlation

Coverage:

1. case with missing active correlation
2. `AUTO_REPAIR`
3. repaired case disappears from issue list

### Flow 13.3 Repair Terminal Correlation

Coverage:

1. terminal case missing correlation
2. repaired from history

### Flow 13.4 Terminate Active Runtime For Terminal Case

Coverage:

1. force domain terminal
2. terminate runtime
3. task list becomes empty

Priority:

1. regression
2. full

## 14. Maintenance Operations

### Flow 14.1 Recalculate Overdue Obligations

Coverage:

1. published sanction decision exists
2. operation returns `COMPLETED`
3. affected row count visible
4. obligation becomes `OVERDUE`

### Flow 14.2 Closure Guard After Overdue

Coverage:

1. close case while overdue obligation remains
2. `CASE_TRANSITION_NOT_ALLOWED`

### Flow 14.3 Table Lock Conflict

Coverage:

1. hold table lock externally
2. operation returns `MAINTENANCE_OPERATION_LOCKED`

Priority:

1. regression for normal batch
2. full for lock conflict

## 15. Authorization and Security

### Flow 15.1 Jurisdiction Mismatch

Coverage:

1. role benar tapi jurisdiction salah -> `403`

### Flow 15.2 Assignment Visibility Restriction

Coverage:

1. investigator hanya lihat case assignment langsung

### Flow 15.3 Classification Restriction

Coverage:

1. reviewer dengan clearance terbatas ditolak untuk case rahasia

### Flow 15.4 Conflict Of Interest

Coverage:

1. conflicted reviewer denied

### Flow 15.5 Auditor Read-Only

Coverage:

1. auditor dapat baca audit/case yang diizinkan
2. auditor tidak dapat mutate

Priority:

1. regression
2. full

## 16. Messaging and Async Observable Behavior

### Flow 16.1 Outbox-Driven Notification Behavior

Coverage:

1. business action memicu event/outbox
2. notification projection atau downstream visible effect bisa diobservasi
3. Mailpit receive email jika flow memang mengirim email

### Flow 16.2 Duplicate Delivery Safety

Coverage:

1. duplicate event tidak membuat duplicate business side effect

Catatan:

Bagian ini boleh memakai helper Java/Kafka/DB karena public API-nya tidak lengkap untuk semua observability.

Priority:

1. full

## 17. Smoke Pack Minimum

Smoke pack minimum yang direkomendasikan:

1. health
2. login
3. create report
4. triage report
5. create case
6. get case
7. assign case
8. create relationship basic
9. workflow reconciliation list basic
10. maintenance operation basic

## 18. Regression Pack Minimum

Regression pack minimum:

1. seluruh smoke
2. list/search/cursor case
3. evidence happy path
4. recommendation-review-decision-publish
5. appeal happy path
6. task list/claim/complete
7. workflow reconciliation repair
8. assignment rotation
9. relationship recursion
10. maintenance overdue success

## 19. Full Pack Additions

Full pack menambah:

1. stale version conflict
2. invalid transition conflict
3. unauthorized/jurisdiction/classification/conflict-of-interest denial
4. checksum mismatch evidence
5. decision row lock conflict
6. maintenance table lock conflict
7. duplicate relationship
8. cycle relationship
9. late appeal override path
10. async notification/outbox observable assertions

## Definition Of Done

Coverage plan ini dianggap terpenuhi jika:

1. setiap coverage group di atas punya minimal satu feature aktif
2. smoke, regression, dan full masing-masing bisa dijalankan terpisah
3. flow sukses utama dan failure path penting sudah terwakili
4. scenario live run terhadap application running membuktikan seluruh group utama benar-benar executable
