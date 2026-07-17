# MASTER PROMPT — SENTINEL ENFORCEMENT PLATFORM

You act as **Principal Software Engineer, Solution Architect, Database Engineer, BPMN Workflow Engineer, Security Engineer, and DevOps Engineer** to build an enterprise training project named:

> **Sentinel Enforcement Platform**

Sentinel is a regulatory enforcement and complex case management platform for managing reports, triage, investigations, evidence, reviews, decisions, sanctions, appeals, and case closure.

This project must be realistic enough to serve as a learning resource for enterprise production-grade technologies, not just a CRUD demo.

---

# 1. Project Objectives

Build a modular application that demonstrates:

* Long-running business process.
* Human task and approval workflow.
* Strict state transition.
* Maker-checker control.
* Authentication and authorization.
* Resource-level authorization.
* Auditability.
* Optimistic concurrency control.
* Transactional outbox.
* Idempotent Kafka consumer.
* File lifecycle in object storage.
* Database migration.
* Contract-first API.
* Failure recovery.
* Local deployment using Docker Compose.
* Developer workflow through Makefile.

The project must run completely on a local machine.

Main target:

```bash
git clone <repository>
cd sentinel-enforcement
make bootstrap
make up
make migrate
make seed
make smoke-test
```

After these commands complete, all local dependencies and the application should be ready to use.

---

# 2. Required Technology Stack

Use the following technologies.

## Backend

* Java 17 or newer.
* Jakarta RESTful Web Services.
* Jersey.
* MyBatis.
* HikariCP
* Liquibase.
* MapStruct.
* Jackson FasterXML.
* Hibernate Validator.
* OpenAPI Generator.
* Maven.
* SLF4J and Logback.

## Infrastructure

* PostgreSQL. (image: postgres:18.3-alpine)
* PL/pgSQL.
* Camunda 7 BPMN Engine.
* Apache Kafka. (Image: confluentinc/cp-kafka:7.8.1)
* Redis. (Image: redis7.2.7-alpine)
* MinIO. (Image: quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z)
* Keycloak untuk local identity provider. (Image: quay.io/keycloak/keycloak:26.6)

## Deployment dan development

* Docker.
* Docker Compose.
* Makefile.
* Testcontainers untuk integration test.

Jangan mengganti teknologi wajib tersebut tanpa alasan teknis yang kuat dan dokumentasi Architecture Decision Record.

---

# 3. Prinsip arsitektur

Gunakan pendekatan:

> **Modular monolith dengan event-driven integration boundaries**

Jangan langsung memecah aplikasi menjadi banyak microservice.

Aplikasi awal harus berupa satu deployable Java application dengan module dan boundary yang eksplisit.

Pisahkan domain menjadi module berikut:

```text
identity-access
intake
case-management
evidence
workflow
decision
sanction
appeal
notification
audit
```

Dependency harus mengarah ke dalam.

Struktur konseptual:

```text
REST API
   |
Application Services
   |
Domain Model and Policies
   |
Ports
   |
Infrastructure Adapters
```

Domain tidak boleh bergantung langsung pada:

* Jersey.
* MyBatis.
* Kafka.
* Redis.
* MinIO.
* Camunda.
* Keycloak.

Gunakan interfaces atau ports pada boundary yang relevan.

Hindari membuat abstraction yang tidak memiliki kebutuhan nyata. Jangan menerapkan pattern hanya karena pattern tersebut populer.

---

# 4. Struktur Maven

Gunakan Maven multi-module dengan struktur awal:

```text
sentinel-enforcement/
├── pom.xml
├── Makefile
├── docker-compose.yml
├── .env.example
├── README.md
├── docs/
│   ├── architecture/
│   ├── adr/
│   ├── api/
│   ├── bpmn/
│   ├── database/
│   └── runbooks/
├── deployment/
│   ├── app/
│   ├── postgres/
│   ├── kafka/
│   ├── minio/
│   └── keycloak/
├── sentinel-bootstrap/
├── sentinel-api/
├── sentinel-application/
├── sentinel-domain/
├── sentinel-persistence/
├── sentinel-workflow/
├── sentinel-messaging/
├── sentinel-storage/
├── sentinel-security/
├── sentinel-observability/
└── sentinel-integration-tests/
```

Tanggung jawab module:

## `sentinel-domain`

Berisi:

* Aggregate.
* Entity.
* Value object.
* Domain service.
* Policy.
* Domain exception.
* State transition rules.
* Tidak memiliki dependency ke infrastructure framework.

## `sentinel-application`

Berisi:

* Command.
* Query.
* Command handler.
* Application service.
* Transaction boundary.
* Authorization orchestration.
* Port interface.

## `sentinel-api`

Berisi:

* Jersey resource.
* Request dan response DTO.
* Exception mapper.
* API validation.
* MapStruct mapper.
* Authentication filter integration.

## `sentinel-persistence`

Berisi:

* MyBatis mapper.
* Repository adapter.
* Database record.
* Type handler.
* Liquibase changelog.
* PL/pgSQL functions.

## `sentinel-workflow`

Berisi:

* Camunda process deployment.
* BPMN delegate.
* Task service adapter.
* Domain-workflow correlation.
* Workflow incident handling.

## `sentinel-messaging`

Berisi:

* Kafka producer.
* Kafka consumer.
* Event serializer.
* Transactional outbox publisher.
* Inbox deduplication.
* Retry dan dead-letter handling.

## `sentinel-storage`

Berisi:

* MinIO client.
* Presigned upload.
* File metadata verification.
* Object storage adapter.

## `sentinel-security`

Berisi:

* JWT validation.
* Security context.
* Permission model.
* Authorization policies.
* Keycloak integration.

## `sentinel-observability`

Berisi:

* Structured logging.
* Correlation context.
* Health checks.
* Metrics abstraction.

## `sentinel-bootstrap`

Berisi:

* Application entry point.
* Dependency wiring.
* Configuration loading.
* Jersey server bootstrap.
* Lifecycle management.

---

# 5. Domain utama

Platform mengelola lifecycle berikut:

```text
Report
  -> Triage
  -> Case
  -> Investigation
  -> Recommendation
  -> Review
  -> Decision
  -> Sanction
  -> Appeal
  -> Enforcement Monitoring
  -> Closure
```

Entity inti:

```text
Report
CaseRecord
CaseParty
CaseAssignment
Allegation
Investigation
InvestigationActivity
Evidence
EvidenceVersion
Recommendation
Review
Decision
DecisionVersion
Sanction
SanctionObligation
Appeal
AppealDecision
WorkflowInstance
CaseStatusHistory
AuditEvent
OutboxEvent
InboxEvent
IdempotencyRecord
Notification
```

Gunakan UUID untuk identifier internal.

Gunakan nomor bisnis manusiawi untuk kasus:

```text
JKT-ENF-2026-00000001
```

Nomor kasus harus dihasilkan secara concurrency-safe.

---

# 6. Lifecycle kasus

Status kasus minimum:

```text
CREATED
UNDER_TRIAGE
UNDER_INVESTIGATION
PENDING_REVIEW
PENDING_DECISION
DECIDED
UNDER_APPEAL
ENFORCEMENT_IN_PROGRESS
CLOSED
CANCELLED
```

Jangan membuat setter bebas untuk mengubah status.

Semua perubahan status harus melewati transition policy.

Contoh:

```java
caseRecord.transitionTo(
    CaseStatus.UNDER_INVESTIGATION,
    transitionContext
);
```

Transition harus memvalidasi:

* Current state.
* Target state.
* Actor permission.
* Business prerequisites.
* Version.
* Reason.
* Timestamp.

Simpan semua perubahan lifecycle ke `case_status_history`.

---

# 7. Invariants wajib

Implementasikan dan test invariants berikut.

## Case

```text
Case CLOSED tidak boleh diubah kecuali melalui approved reopen process.
```

```text
Case tidak boleh masuk PENDING_DECISION jika investigation report belum disetujui.
```

```text
Case tidak boleh ditutup jika masih memiliki active sanction obligation.
```

## Maker-checker

```text
User yang membuat recommendation tidak boleh menjadi final approver.
```

```text
User yang mengubah sanction tidak boleh menyetujui perubahan yang sama.
```

## Evidence

```text
Evidence yang telah direferensikan oleh published decision tidak boleh dihapus.
```

```text
Setiap evidence version memiliki immutable SHA-256 checksum.
```

```text
Setiap download evidence sensitif harus menghasilkan audit event.
```

## Decision

```text
Published decision bersifat immutable.
```

```text
Perubahan setelah publication harus melalui correction atau appeal process.
```

## Appeal

```text
Satu decision hanya boleh memiliki satu active appeal.
```

```text
Appeal yang melewati deadline harus memerlukan explicit supervisor override.
```

## Messaging

```text
Satu eventId hanya boleh menghasilkan satu business side effect untuk satu consumer.
```

## Authorization

```text
Memiliki role tidak otomatis memberikan akses terhadap semua kasus.
```

Akses kasus harus mempertimbangkan:

* Permission.
* Jurisdiction.
* Assigned unit.
* Direct assignment.
* Case classification.
* Case state.
* Conflict of interest.

---

# 8. Database

Gunakan PostgreSQL sebagai authoritative source of truth.

Jangan menggunakan H2 untuk integration test.

Schema awal harus mencakup:

```text
report
case_record
case_party
case_assignment
case_relationship
allegation
investigation
investigation_activity
evidence
evidence_version
case_note
recommendation
review
decision
decision_version
sanction
sanction_obligation
appeal
appeal_decision
workflow_instance
case_status_history
audit_event
outbox_event
inbox_event
idempotency_record
notification
business_calendar
jurisdiction
```

Setiap tabel transaksional minimal memiliki:

```text
id
created_at
created_by
updated_at
updated_by
version
```

Pengecualian diperbolehkan untuk immutable atau append-only table seperti audit event.

Gunakan:

* `TIMESTAMPTZ`, bukan `TIMESTAMP` biasa.
* UTC pada database dan application.
* Unique constraint.
* Foreign key.
* Check constraint.
* Partial index jika relevan.
* Explicit index untuk query penting.

Jangan mengandalkan validasi Java sebagai satu-satunya proteksi invariant yang dapat dijaga database.

---

# 9. Optimistic locking

Gunakan optimistic locking pada aggregate mutable.

Contoh:

```sql
UPDATE case_record
SET
    status = #{status},
    version = version + 1,
    updated_at = now(),
    updated_by = #{updatedBy}
WHERE id = #{id}
  AND version = #{expectedVersion};
```

Jika affected row adalah nol, hasilkan:

```text
409 CONCURRENT_MODIFICATION
```

Jangan melakukan silent overwrite.

Buat integration test untuk dua concurrent update pada kasus yang sama.

---

# 10. Liquibase

Gunakan Liquibase XML, YAML, atau formatted SQL secara konsisten.

Changelog harus:

* Memiliki identifier stabil.
* Memiliki rollback apabila masuk akal.
* Tidak mengubah changeset yang sudah dianggap released.
* Memisahkan schema, reference data, function, dan index.
* Dapat dijalankan pada database kosong.
* Dapat diuji melalui integration test.

Struktur yang direkomendasikan:

```text
db/changelog/
├── db.changelog-master.yaml
├── releases/
│   ├── 0001-foundation.yaml
│   ├── 0002-case-management.yaml
│   ├── 0003-workflow.yaml
│   └── 0004-messaging.yaml
├── functions/
├── reference-data/
└── test-data/
```

---

# 11. PL/pgSQL

Gunakan PL/pgSQL hanya untuk kebutuhan yang lebih aman atau efisien di database.

Implementasikan:

## Case number generation

Function concurrency-safe yang menghasilkan:

```text
{JURISDICTION}-{TYPE}-{YEAR}-{SEQUENCE}
```

## Business deadline calculation

Function yang menghitung due date berdasarkan:

* Priority.
* Jurisdiction.
* Weekend.
* Holiday calendar.
* Pause period jika kelak dibutuhkan.

## Operational reporting

Gunakan view atau materialized view untuk:

* Investigator workload.
* SLA breach.
* Open cases by status.
* Pending approvals.
* Active sanction obligations.

Jangan menaruh orchestration workflow utama dalam trigger atau stored procedure.

---

# 12. REST API

Gunakan contract-first OpenAPI.

File sumber OpenAPI harus berada di:

```text
docs/api/openapi.yaml
```

OpenAPI Generator digunakan untuk menghasilkan API interface atau model yang sesuai.

Jangan mengedit file hasil generate secara manual.

Endpoint minimum:

```http
POST   /api/v1/reports
GET    /api/v1/reports/{reportId}
POST   /api/v1/reports/{reportId}/triage

POST   /api/v1/cases
GET    /api/v1/cases/{caseId}
GET    /api/v1/cases
POST   /api/v1/cases/{caseId}/assignments
POST   /api/v1/cases/{caseId}/transitions

POST   /api/v1/cases/{caseId}/evidence/upload-sessions
POST   /api/v1/evidence/{evidenceId}/versions/finalize
GET    /api/v1/evidence/{evidenceId}
POST   /api/v1/evidence/{evidenceId}/download-sessions

POST   /api/v1/cases/{caseId}/recommendations
POST   /api/v1/recommendations/{recommendationId}/submit
POST   /api/v1/recommendations/{recommendationId}/reviews

POST   /api/v1/cases/{caseId}/decisions
POST   /api/v1/decisions/{decisionId}/approve
POST   /api/v1/decisions/{decisionId}/publish

POST   /api/v1/decisions/{decisionId}/appeals

GET    /api/v1/tasks
POST   /api/v1/tasks/{taskId}/claim
POST   /api/v1/tasks/{taskId}/complete

GET    /api/v1/cases/{caseId}/audit-events
```

Gunakan pagination berbasis cursor atau keyset untuk list yang berpotensi besar.

Hindari offset pagination untuk dataset besar, kecuali endpoint administratif sederhana.

---

# 13. API error model

Gunakan error envelope konsisten, terinspirasi dari RFC 7807:

```json
{
  "type": "https://sentinel.local/errors/invalid-case-transition",
  "title": "Invalid case transition",
  "status": 409,
  "code": "CASE_TRANSITION_NOT_ALLOWED",
  "detail": "Case cannot transition from CLOSED to UNDER_INVESTIGATION",
  "instance": "/api/v1/cases/123/transitions",
  "correlationId": "1f53faab-8ee2-44cc-a45a-d9c9c02fd355",
  "violations": []
}
```

Mapping minimum:

```text
400 malformed request
401 unauthenticated
403 unauthorized
404 resource not found
409 state conflict atau optimistic locking
412 precondition failed
422 semantically invalid command
429 rate limited
500 unexpected error
503 dependency unavailable
```

Jangan mengembalikan stack trace ke client.

---

# 14. Validation

Gunakan Hibernate Validator pada API boundary dan application command.

Bedakan:

* Syntactic validation.
* Semantic validation.
* Authorization.
* State transition validation.
* Database constraint violation.

Contoh syntactic validation:

```java
@NotNull
@Size(max = 200)
private String title;
```

Contoh semantic validation:

```text
appeal submission date tidak boleh sebelum decision publication date
```

Semantic validation tidak boleh dipaksakan seluruhnya ke annotation custom apabila membutuhkan domain state kompleks.

---

# 15. MapStruct

Gunakan MapStruct untuk:

* Request DTO ke command.
* Domain projection ke response.
* Persistence record ke domain object bila sesuai.
* Event payload mapping.

Jangan menggunakan MapStruct untuk menyembunyikan business logic.

Mapping yang melibatkan keputusan bisnis harus dilakukan eksplisit di application atau domain layer.

Aktifkan compiler policy agar unmapped property terdeteksi.

---

# 16. Jackson

Konfigurasikan Jackson secara eksplisit.

Gunakan:

* ISO-8601.
* UTC.
* `Instant` untuk event timestamp.
* `LocalDate` untuk tanggal tanpa waktu.
* Strict unknown property policy sesuai kontrak.
* Enum serialization konsisten.
* Custom module bila diperlukan.

Jangan menggunakan timestamp numerik.

Jangan melakukan polymorphic deserialization yang tidak aman.

---

# 17. Authentication

Gunakan Keycloak pada local environment.

Flow:

```text
Client
  -> Keycloak
  -> JWT access token
  -> Jersey authentication filter
  -> Application security context
```

JWT validation harus memeriksa:

* Signature.
* Issuer.
* Audience.
* Expiry.
* Not-before.
* Required claims.

Jangan menerima JWT yang hanya di-decode tanpa signature verification.

Role awal:

```text
CASE_INTAKE_OFFICER
TRIAGE_OFFICER
INVESTIGATOR
CASE_REVIEWER
DECISION_MAKER
APPEAL_OFFICER
SUPERVISOR
AUDITOR
SYSTEM_ADMIN
```

Buat Keycloak realm import untuk local development.

Buat user dummy untuk setiap role.

---

# 18. Authorization

Authorization tidak boleh hanya dilakukan pada Jersey resource.

Gunakan centralized authorization service:

```java
authorizationService.requirePermission(
    actor,
    Permission.APPROVE_DECISION,
    authorizationContext
);
```

Authorization context dapat mencakup:

```text
caseId
jurisdictionCode
assignedUnitId
assigneeId
caseClassification
caseStatus
resourceOwner
createdBy
```

Buat test untuk:

* Role benar tetapi jurisdiction salah.
* Role benar tetapi bukan assignee.
* Supervisor unit A mengakses kasus unit B.
* Auditor read-only.
* User mencoba menyetujui hasil kerjanya sendiri.
* User kehilangan role setelah token lama diterbitkan.

Dokumentasikan trade-off antara claim-based authorization dan live authorization lookup.

---

# 19. Camunda 7

Gunakan Camunda 7 sebagai workflow orchestration engine.

BPMN utama:

```text
regulatory-enforcement-case.bpmn
```

Workflow minimum:

```text
Start
  -> Validate Case
  -> Triage User Task
  -> Gateway
      -> Reject
      -> Merge
      -> Open Investigation
  -> Assign Investigator
  -> Evidence Collection
  -> Investigation Review
  -> Evidence Sufficient?
      -> No: Return to Investigator
      -> Yes: Create Recommendation
  -> Legal Review
  -> Decision Approval
  -> Violation Proven?
      -> No: Close Without Sanction
      -> Yes: Issue Sanction
  -> Wait for Appeal Period
  -> Appeal Submitted?
      -> Yes: Appeal Subprocess
  -> Enforcement Monitoring
  -> Close Case
  -> End
```

Gunakan secara nyata:

* User task.
* Service task.
* Exclusive gateway.
* Parallel gateway.
* Boundary timer.
* Message event.
* Error boundary event.
* Event subprocess.
* Multi-instance task.
* Escalation.

Camunda bertanggung jawab atas orchestration.

PostgreSQL domain tables tetap menjadi source of truth business state.

Jangan menyimpan seluruh business object sebagai process variable.

Process variable hanya untuk data korelasi dan routing yang dibutuhkan workflow:

```text
caseId
jurisdictionCode
caseType
workflowVersion
```

Simpan correlation:

```text
caseId
processInstanceId
processDefinitionId
processDefinitionVersion
businessKey
status
```

---

# 20. Konsistensi domain dan workflow

Definisikan secara eksplisit ownership:

```text
Domain database:
- Legal/business state.
- Aggregate version.
- Assignment.
- Decision.
- Evidence metadata.
- Sanction.
- Appeal.

Camunda:
- Current orchestration position.
- Active human task.
- Timer.
- Retry.
- Escalation.
```

Jangan mengasumsikan domain update dan Camunda transition berada dalam distributed transaction.

Definisikan strategy untuk failure berikut:

```text
Domain commit berhasil tetapi workflow signal gagal.
Workflow task selesai tetapi domain update gagal.
Duplicate task completion request.
Camunda retry menjalankan delegate dua kali.
```

Semua delegate dan external side effect harus idempotent.

Sediakan reconciliation job atau administrative operation untuk mendeteksi mismatch domain-workflow.

---

# 21. Kafka

Topik minimum:

```text
case.lifecycle.v1
case.assignment.v1
evidence.lifecycle.v1
decision.lifecycle.v1
sanction.lifecycle.v1
appeal.lifecycle.v1
notification.command.v1
notification.result.v1
audit.integration.v1
```

Gunakan event envelope:

```json
{
  "eventId": "59696749-af30-4aee-9dc8-6440c34a1ea3",
  "eventType": "CaseOpened",
  "eventVersion": 1,
  "aggregateType": "Case",
  "aggregateId": "9d69416d-0f32-48e8-a48f-fc7864fd6cd1",
  "occurredAt": "2026-07-14T10:30:12Z",
  "correlationId": "bf89d22f-4503-4d0e-a2fb-af026036728d",
  "causationId": "078d7d2d-c974-4ee9-baa0-76057d46a645",
  "actor": {
    "type": "USER",
    "id": "a5a31fdd-7b5d-4602-a4c9-fbf7c7588fec"
  },
  "payload": {}
}
```

Event key harus mendukung ordering per aggregate.

Untuk lifecycle case gunakan:

```text
key = caseId
```

---

# 22. Transactional outbox

Jangan melakukan database commit dan Kafka publish sebagai dua operasi independen.

Dalam transaction yang sama:

```text
1. Mutasi domain.
2. Insert outbox event.
3. Commit.
```

Outbox publisher:

* Mengambil pending event.
* Melakukan publish.
* Menandai event sebagai published.
* Menangani retry.
* Aman terhadap duplicate publish.
* Memiliki observability.
* Menghindari multiple publisher mengambil row yang sama.

Gunakan strategy seperti:

```sql
FOR UPDATE SKIP LOCKED
```

atau mekanisme leasing yang setara.

Duplicate delivery tetap harus dianggap mungkin.

---

# 23. Inbox dan consumer idempotency

Setiap consumer harus menyimpan:

```text
consumer_name
event_id
processed_at
result_reference
```

Buat unique constraint:

```text
UNIQUE (consumer_name, event_id)
```

Side effect dan inbox record harus berada dalam transaction yang sama jika menggunakan database yang sama.

Test minimum:

* Event yang sama diterima dua kali.
* Consumer crash sebelum commit.
* Consumer crash setelah external call.
* Poison event.
* Schema version tidak dikenal.
* Retry melebihi batas.
* Dead-letter event.

---

# 24. Redis

Gunakan Redis hanya untuk data yang aman direkonstruksi.

Penggunaan yang diperbolehkan:

* Permission cache.
* Reference data cache.
* Rate limiting.
* Temporary upload session.
* Short-lived idempotency hint.
* Distributed coordination terbatas.
* Task claim guard.

Redis tidak boleh menjadi authoritative storage untuk:

* Case status.
* Assignment.
* Decision.
* Appeal.
* Evidence metadata.
* Sanction.

Database constraint atau optimistic locking tetap menjadi proteksi final.

---

# 25. MinIO

Gunakan MinIO untuk file evidence dan document.

Upload flow:

```text
1. Client meminta upload session.
2. Backend memvalidasi permission.
3. Backend membuat metadata pending.
4. Backend menghasilkan presigned URL.
5. Client upload langsung ke MinIO.
6. Client memanggil finalize endpoint.
7. Backend memverifikasi object.
8. Backend memvalidasi size, checksum, dan media type.
9. Backend membuat evidence version.
10. Backend menulis outbox event.
```

Object key:

```text
/{jurisdiction}/{caseId}/{evidenceId}/{version}/{generatedFileName}
```

Simpan metadata:

```text
original_filename
generated_filename
bucket
object_key
media_type
size_bytes
sha256_checksum
version
classification
uploaded_by
uploaded_at
retention_policy
storage_status
```

Jangan mempercayai filename atau media type dari client.

Cegah path traversal.

Presigned URL harus short-lived.

Download harus melalui authorization check dan menghasilkan audit event.

---

# 26. Audit

Audit log berbeda dari application log.

Audit event bersifat append-only.

Audit event minimum:

```text
event_id
event_type
actor_type
actor_id
actor_roles
action
resource_type
resource_id
case_id
timestamp
correlation_id
source_ip
result
reason
before_summary
after_summary
metadata
```

Audit untuk:

* Login atau authentication failure penting.
* Case view sensitif.
* Evidence upload.
* Evidence download.
* Assignment.
* State transition.
* Recommendation submit.
* Review.
* Decision approval.
* Decision publication.
* Appeal.
* Override.
* Administrative correction.

Jangan menyimpan seluruh binary atau secret dalam audit metadata.

---

# 27. Logging

Gunakan structured JSON logging.

Field minimum:

```text
timestamp
level
service
logger
message
correlationId
traceId
actorId
caseId
processInstanceId
taskId
eventId
topic
partition
offset
errorCode
durationMs
```

Gunakan MDC atau mechanism setara.

Correlation ID:

* Terima dari request jika valid.
* Generate jika tidak tersedia.
* Kembalikan di response header.
* Propagate ke Kafka header.
* Propagate ke workflow context bila relevan.

Jangan log:

* Password.
* Access token.
* Refresh token.
* Secret.
* Presigned URL.
* Full personal data.
* Isi evidence.
* Authorization header.

---

# 28. Configuration

Gunakan environment variable untuk configuration.

Buat `.env.example`.

Jangan commit:

* Password nyata.
* Access key nyata.
* Private key.
* Token.
* Secret.

Fail fast jika required configuration tidak tersedia.

Configuration minimum:

```text
HTTP_PORT
DB_URL
DB_USERNAME
DB_PASSWORD
KAFKA_BOOTSTRAP_SERVERS
REDIS_HOST
REDIS_PORT
MINIO_ENDPOINT
MINIO_ACCESS_KEY
MINIO_SECRET_KEY
KEYCLOAK_ISSUER
KEYCLOAK_AUDIENCE
CAMUNDA_DATABASE_URL
```

Local secret boleh berupa dummy non-production credential dan harus ditandai jelas.

---

# 29. Docker Compose

Buat Docker Compose untuk:

```text
app
postgres
kafka
redis
minio
minio-init
keycloak
mailpit
```

Boleh menambahkan:

```text
kafka-ui
redis-insight
prometheus
grafana
otel-collector
```

Semua service harus memiliki:

* Healthcheck jika tersedia.
* Named volume.
* Explicit port.
* Environment configuration.
* Dependency readiness yang benar.
* Stable local hostname.

Jangan mengandalkan `depends_on` saja sebagai readiness guarantee.

Application harus melakukan retry dengan bounded exponential backoff saat dependency belum siap.

Gunakan multi-stage Docker build.

Jalankan container aplikasi sebagai non-root user.

---

# 30. Makefile

Semua command operasional developer harus dikumpulkan dalam Makefile.

Target minimum:

```text
help
bootstrap
clean
compile
test
unit-test
integration-test
workflow-test
messaging-test
e2e-test
verify
package

openapi-generate
openapi-validate

up
down
restart
reset
ps
logs
app-logs

docker-build
docker-push-local

migrate
rollback
db-status
db-shell
db-reset

seed
smoke-test

kafka-topics
kafka-consume
kafka-produce

minio-init
keycloak-import

bpmn-validate
bpmn-deploy

format
lint
dependency-check
```

`make help` harus menampilkan deskripsi seluruh target.

Command penting harus idempotent bila memungkinkan.

---

# 31. Testing strategy

Gunakan JUnit 5.

## Unit test

Test:

* State transition policy.
* Maker-checker.
* Appeal deadline.
* Authorization policy.
* Evidence lifecycle.
* Sanction calculation.
* Mapper.
* Event generation.

## Persistence integration test

Gunakan Testcontainers PostgreSQL.

Test:

* Liquibase migration.
* MyBatis query.
* Constraint.
* Optimistic locking.
* PL/pgSQL.
* JSON serialization jika menggunakan JSONB.
* Concurrent case number generation.

## Workflow test

Test:

* Happy path.
* Reject path.
* Merge path.
* Insufficient evidence loop.
* Timer escalation.
* Decision approval.
* Appeal path.
* Cancellation.
* Delegate retry.
* Duplicate external completion.

## Kafka test

Gunakan Kafka Testcontainer.

Test:

* Outbox publish.
* Consumer processing.
* Duplicate event.
* Ordering per aggregate.
* Retry.
* Dead-letter.
* Unknown schema version.

## MinIO test

Gunakan MinIO Testcontainer atau container dalam test environment.

Test:

* Presigned upload.
* Finalize.
* Checksum mismatch.
* Missing object.
* Unauthorized download.
* Duplicate finalize.

## End-to-end test

Jalankan lifecycle:

```text
Create report
-> Triage
-> Create case
-> Assign investigator
-> Upload evidence
-> Submit recommendation
-> Review
-> Approve decision
-> Publish decision
-> Issue sanction
-> Pass appeal period
-> Complete obligation
-> Close case
```

---

# 32. Failure scenarios wajib

Buat automated test atau documented simulation untuk:

```text
1. Kafka unavailable setelah database commit.
2. Duplicate Kafka delivery.
3. Consumer crash sebelum commit.
4. Consumer crash setelah external side effect.
5. Dua user mengubah case bersamaan.
6. Dua approver mencoba approve bersamaan.
7. Camunda delegate dipanggil ulang.
8. Domain update berhasil tetapi workflow correlation gagal.
9. Workflow task selesai tetapi domain command gagal.
10. MinIO upload berhasil tetapi finalize gagal.
11. Redis unavailable.
12. JWT valid tetapi user tidak berwenang terhadap jurisdiction.
13. Appeal diajukan tepat di deadline.
14. Migration gagal.
15. BPMN versi baru di-deploy saat instance lama masih aktif.
```

Untuk setiap failure scenario, dokumentasikan:

```text
Trigger
Expected behavior
Data consistency expectation
Retry behavior
Operator action
Audit/log evidence
```

---

# 33. Dokumentasi

Buat dokumentasi berikut.

## README

README harus menjelaskan:

* Tujuan project.
* Architecture overview.
* Technology stack.
* Prerequisites.
* Local setup.
* Commands.
* Test strategy.
* Default users.
* API access.
* Troubleshooting.

## Architecture Decision Record

Buat ADR minimum:

```text
ADR-001 modular-monolith
ADR-002 domain-state-vs-workflow-state
ADR-003 mybatis-over-orm
ADR-004 transactional-outbox
ADR-005 inbox-idempotency
ADR-006 keycloak-local-authentication
ADR-007 minio-evidence-storage
ADR-008 optimistic-locking
ADR-009 api-contract-first
ADR-010 audit-log-model
```

Gunakan format:

```text
Context
Decision
Alternatives
Consequences
Status
```

## Runbook

Buat runbook untuk:

* Application gagal start.
* PostgreSQL unavailable.
* Kafka backlog meningkat.
* Outbox stuck.
* Dead-letter event.
* Camunda incident.
* Domain-workflow mismatch.
* MinIO object missing.
* Liquibase lock.
* Keycloak unavailable.

---

# 34. Working protocol untuk AI agent

Ikuti protocol ini sepanjang pengerjaan.

## Sebelum mengubah kode

1. Baca seluruh repository yang relevan.
2. Periksa `README.md`, `pom.xml`, `Makefile`, Docker Compose, dan ADR.
3. Identifikasi module yang terpengaruh.
4. Jelaskan perubahan yang akan dilakukan secara ringkas.
5. Identifikasi invariant dan transaction boundary.
6. Identifikasi failure mode.
7. Jangan mengubah file yang tidak relevan.

## Saat mengimplementasikan

1. Kerjakan dalam increment kecil.
2. Pastikan setiap increment dapat dikompilasi.
3. Tambahkan test bersamaan dengan code.
4. Jangan meninggalkan placeholder palsu.
5. Jangan menambahkan TODO tanpa penjelasan.
6. Jangan menelan exception.
7. Jangan menggunakan generic `RuntimeException` untuk expected domain error.
8. Jangan menyembunyikan query database dalam helper yang sulit dilacak.
9. Jangan melakukan network call di dalam database transaction tanpa alasan dan strategy yang jelas.
10. Jangan membuat global mutable state.

## Setelah mengimplementasikan

Jalankan minimal:

```bash
make format
make compile
make unit-test
make integration-test
make verify
```

Jika Docker dependency dibutuhkan:

```bash
make up
make migrate
make smoke-test
```

Laporkan:

```text
Files changed
Design decisions
Tests added
Commands executed
Test results
Known limitations
Next logical increment
```

Jangan mengklaim test berhasil jika command tidak dijalankan.

Jika tool atau environment tidak memungkinkan menjalankan test, nyatakan secara eksplisit.

---

# 35. Coding standards

Gunakan aturan berikut.

* Gunakan immutable object bila memungkinkan.
* Gunakan constructor injection.
* Hindari service locator.
* Hindari static mutable dependency.
* Gunakan explicit transaction boundary.
* Gunakan named domain concepts.
* Hindari boolean parameter yang ambigu.
* Hindari method dengan terlalu banyak parameter; gunakan command atau value object.
* Hindari generic utility class yang menjadi tempat semua logic.
* Gunakan exception hierarchy yang jelas.
* Jangan menggunakan Lombok kecuali diputuskan melalui ADR.
* Jangan menggunakan reflection untuk business mapping.
* Jangan menggunakan field injection.
* Jangan menyimpan entity domain langsung sebagai API DTO.
* Jangan mengekspos database record ke API.
* Jangan mengembalikan `null` collection.
* Gunakan `Optional` secara selektif pada return value, bukan field entity.
* Jangan menggunakan `Optional` sebagai parameter.
* Gunakan `Clock` injection untuk logic waktu yang perlu ditest.
* Gunakan secure random untuk token.
* Gunakan constant-time comparison bila memproses secret-derived value.

---

# 36. Query dan performance standards

Untuk setiap query list:

* Tentukan expected cardinality.
* Tentukan index.
* Batasi result.
* Gunakan pagination.
* Hindari N+1 query.
* Jangan menggunakan `SELECT *`.
* Gunakan explicit column list.
* Gunakan explain plan untuk query kritis.

Buat initial performance targets:

```text
Simple read p95 < 200 ms
Simple command p95 < 400 ms
Case search p95 < 800 ms
Task list p95 < 500 ms
Outbox publish delay normally < 5 seconds
```

Target adalah local development baseline, bukan production SLA final.

Tambahkan dataset generator agar dapat menguji:

```text
10,000 cases
100,000 evidence metadata records
1,000,000 audit events
```

---

# 37. Security standards

Terapkan:

* Parameterized query.
* Input validation.
* Output encoding yang sesuai.
* Authorization pada setiap resource access.
* Least privilege database user.
* Bucket policy terbatas.
* Short-lived presigned URL.
* No secrets in logs.
* No sensitive data in exception.
* Safe file name generation.
* Media type validation.
* File size limit.
* Rate limit untuk endpoint sensitif.
* Audit untuk privileged action.

Dokumentasikan threat model awal untuk:

```text
Broken access control
IDOR
Privilege escalation
JWT misuse
Replay request
Duplicate command
Malicious file upload
Data leakage through logs
Kafka event spoofing
Unauthorized evidence access
Workflow task manipulation
```

---

# 38. Implementasi bertahap

Jangan membangun semua fitur sekaligus.

Kerjakan fase berikut secara berurutan.

## Phase 0 — Repository foundation

Deliverable:

* Maven parent.
* Module skeleton.
* Makefile.
* Docker Compose.
* README.
* ADR skeleton.
* Basic application bootstrap.
* Health endpoint.

Acceptance criteria:

```bash
make compile
make unit-test
make up
curl http://localhost:8080/health
```

## Phase 1 — Intake foundation

Deliverable:

* Report schema.
* Liquibase.
* MyBatis.
* Create report API.
* Get report API.
* Validation.
* Error handling.
* Structured logging.

Acceptance criteria:

```text
Report dapat dibuat dan dibaca.
Invalid request menghasilkan error envelope konsisten.
Migration dapat dijalankan pada database kosong.
```

## Phase 2 — Authentication dan authorization

Deliverable:

* Keycloak realm.
* JWT validation.
* Security context.
* Role permission.
* Jurisdiction authorization.
* Dummy users.

Acceptance criteria:

```text
Request tanpa token ditolak.
Token role salah ditolak.
Token role benar tetapi jurisdiction salah ditolak.
```

## Phase 3 — Case lifecycle

Deliverable:

* Case aggregate.
* Case transition policy.
* Assignment.
* Status history.
* Optimistic locking.
* Audit.

Acceptance criteria:

```text
Invalid transition ditolak.
Concurrent update menghasilkan 409.
Status history tersimpan.
```

## Phase 4 — Camunda workflow

Deliverable:

* BPMN deployment.
* Start case process.
* Triage task.
* Investigation task.
* Review task.
* Decision task.
* Timer escalation.

Acceptance criteria:

```text
Case process dapat berjalan dari start sampai decision.
Task dapat di-query, claim, dan complete.
Duplicate completion aman.
```

## Phase 5 — Evidence dan MinIO

Deliverable:

* Evidence metadata.
* Upload session.
* Presigned URL.
* Finalization.
* Versioning.
* Checksum.
* Download authorization.

Acceptance criteria:

```text
Upload dan finalize berhasil.
Checksum mismatch ditolak.
Unauthorized download ditolak dan diaudit.
```

## Phase 6 — Kafka reliability

Deliverable:

* Event envelope.
* Outbox.
* Publisher.
* Inbox.
* Notification consumer.
* Retry.
* Dead-letter.

Acceptance criteria:

```text
Database commit tidak hilang saat Kafka unavailable.
Duplicate event tidak menghasilkan duplicate side effect.
```

## Phase 7 — Decision, sanction, appeal

Deliverable:

* Recommendation.
* Review.
* Maker-checker.
* Decision publication.
* Sanction obligation.
* Appeal deadline.
* Appeal workflow.

Acceptance criteria:

```text
Author tidak dapat menyetujui rekomendasinya sendiri.
Published decision immutable.
Late appeal membutuhkan override.
```

## Phase 8 — Hardening

Deliverable:

* Reconciliation.
* Operational runbooks.
* Load dataset.
* Performance query review.
* Failure injection tests.
* Metrics dan dashboard dasar.

---

# 39. Current task selection

Saat prompt ini pertama kali dijalankan, lakukan hal berikut:

1. Periksa apakah repository sudah ada.
2. Jika repository kosong, mulai dari Phase 0.
3. Jika repository sudah berisi code, audit kondisi saat ini terhadap master prompt.
4. Buat file:

```text
docs/PROJECT_STATUS.md
```

Isi file tersebut dengan:

```text
Current phase
Completed capabilities
Incomplete capabilities
Known defects
Architecture deviations
Test status
Infrastructure status
Next recommended task
```

5. Buat file:

```text
docs/IMPLEMENTATION_PLAN.md
```

Berisi task terurut, dependency, dan acceptance criteria.

6. Implementasikan hanya increment pertama yang dapat selesai secara utuh dan teruji.
7. Jangan menghasilkan ratusan file kosong.
8. Jangan membuat stub seolah-olah fitur sudah selesai.
9. Prioritaskan vertical slice yang berjalan.

Vertical slice pertama:

```text
Health endpoint
+
PostgreSQL connection
+
Liquibase migration
+
Create report
+
Get report
+
Validation
+
Error handling
+
Integration test
+
Docker Compose
+
Makefile commands
```

---

# 40. Definition of done

Sebuah task hanya dianggap selesai jika:

```text
Code dikompilasi.
Unit test tersedia dan berhasil.
Integration test tersedia jika menyentuh infrastructure.
Migration tersedia jika schema berubah.
OpenAPI diperbarui jika API berubah.
Authorization dipertimbangkan.
Audit dipertimbangkan.
Logging dan correlation dipertimbangkan.
Failure mode dipertimbangkan.
Dokumentasi diperbarui.
Tidak ada secret yang ter-commit.
Tidak ada placeholder palsu.
```

---

# 41. Output yang diharapkan dari AI agent

Pada awal setiap sesi, tampilkan:

```text
Repository assessment
Current phase
Task selected
Affected modules
Important invariants
Transaction boundary
Failure modes
Planned verification
```

Pada akhir setiap sesi, tampilkan:

```text
Summary
Files changed
Architecture decisions
Database changes
API changes
Tests added
Commands executed
Results
Known limitations
Next recommended increment
```

Berikan jawaban faktual berdasarkan file dan command yang benar-benar diperiksa.

Jangan mengatakan “production-ready” hanya karena aplikasi dapat dikompilasi.

Gunakan istilah:

```text
implemented
tested
locally verified
not yet verified
partially implemented
```

sesuai keadaan sebenarnya.

---

# 42. Perintah awal

Sekarang mulai bekerja.

Lakukan repository assessment terlebih dahulu.

Jika repository kosong:

1. Buat struktur Phase 0.
2. Buat parent Maven project.
3. Buat module minimum yang benar-benar dibutuhkan untuk vertical slice pertama.
4. Buat Docker Compose untuk PostgreSQL.
5. Buat Liquibase foundation.
6. Buat Jersey health endpoint.
7. Buat create dan get report API.
8. Buat MyBatis persistence.
9. Buat validation dan error model.
10. Buat integration test dengan Testcontainers.
11. Buat Makefile.
12. Jalankan seluruh verification command yang tersedia.
13. Perbarui `PROJECT_STATUS.md`.
14. Laporkan hasil secara jujur.

Jangan melanjutkan ke Camunda, Kafka, Redis, MinIO, dan Keycloak sebelum foundation vertical slice pertama dapat dijalankan dan diuji secara konsisten.

<!-- OPENWIKI:START -->

## OpenWiki

This repository uses OpenWiki for recurring code documentation. Start with `openwiki/quickstart.md`, then follow its links to architecture, workflows, domain concepts, operations, integrations, testing guidance, and source maps.

The scheduled OpenWiki GitHub Actions workflow refreshes the repository wiki. Do not hand-edit generated OpenWiki pages unless explicitly asked; prefer updating source code/docs and letting OpenWiki regenerate.

<!-- OPENWIKI:END -->
