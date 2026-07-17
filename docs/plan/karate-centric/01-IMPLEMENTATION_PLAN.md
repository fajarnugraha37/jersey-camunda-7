# Karate-Centric Automation Plan

## Purpose

Dokumen ini menetapkan pendekatan **Karate-centric** sebagai jalur utama automation testing untuk Sentinel terhadap **application yang benar-benar running**, bukan hanya white-box integration test di dalam JVM.

Target plan ini:

1. menjadikan Karate sebagai primary black-box flow runner
2. mencakup seluruh flow aplikasi yang tersedia saat ini, bukan hanya slice perubahan terakhir
3. tetap mempertahankan test Java existing untuk area yang memang low-level dan race-sensitive
4. menghasilkan automation yang bisa dipakai untuk local validation, regression, dan CI

## Decision

Keputusan yang dikunci:

1. **Karate menjadi primary runner** untuk skenario end-to-end API dan async business flow
2. **`sentinel-integration-tests` tetap dipakai** sebagai module rumah untuk automation ini
3. **application under test tetap aplikasi running** di `http://localhost:8080` sebagai mode utama
4. **JUnit + Testcontainers existing tidak dibuang**
   - tetap dipakai untuk white-box, container orchestration, dan race helper yang terlalu teknis untuk murni DSL
5. skenario lock-contention dan DB/Kafka assertion boleh memakai **Java helper kecil** yang dipanggil dari Karate

Ini sengaja **Karate-centric**, bukan Karate-only.

## Why This Direction

Alasan memilih model ini untuk Sentinel:

1. flow aplikasi Sentinel panjang, stateful, dan lebih mudah dibaca dalam DSL bisnis
2. repo sudah punya banyak API flow yang stabil untuk dijalankan black-box
3. user butuh validasi ke aplikasi yang benar-benar running
4. beberapa flow async seperti notification, outbox, workflow reconciliation, dan maintenance lebih cocok diuji dengan polling/assertion lintas boundary
5. concurrency dan table/row lock tetap bisa di-cover tanpa memaksa seluruh orchestration turun ke Java test class lagi

## Scope

### In Scope

1. test runner Karate di `sentinel-integration-tests`
2. struktur feature file, env config, dan helper Java minimum
3. smoke suite, regression suite, dan full suite
4. API flow utama, negative path penting, authorization path penting, dan async path penting
5. DB assertion, Mailpit assertion, dan bila perlu Kafka assertion helper
6. Makefile target baru untuk Karate
7. CI-friendly execution mode

### Out Of Scope

1. mengganti seluruh test Java existing sekaligus
2. UI browser automation
3. performance/load test sebagai deliverable utama
4. memindahkan seluruh infra bootstrap ke Karate

## Execution Model

Karate akan dijalankan dalam dua mode.

### Mode A: Running App Validation

Mode default untuk dev dan smoke:

1. `make up`
2. `make migrate`
3. aplikasi running di `localhost:8080`
4. Karate menembak app dan dependency yang sudah hidup

Ini mode utama untuk membuktikan flow benar-benar jalan pada runtime lokal.

### Mode B: Controlled CI Validation

Mode untuk pipeline:

1. dependency dan app disiapkan dulu oleh job CI
2. Karate hanya menjadi black-box runner
3. helper Java dipakai untuk setup data teknis yang tidak exposed via API

## Proposed Repository Layout

Lokasi yang direkomendasikan:

```text
sentinel-integration-tests/
├── src/test/java/com/sentinel/enforcement/integration/karate/
│   ├── KarateSmokeIT.java
│   ├── KarateRegressionIT.java
│   ├── KarateFullIT.java
│   ├── db/
│   ├── lock/
│   ├── kafka/
│   └── mailpit/
└── src/test/resources/karate/
    ├── karate-config.js
    ├── common/
    │   ├── auth.feature
    │   ├── data-setup.feature
    │   ├── db.feature
    │   ├── polling.feature
    │   └── assertions.feature
    ├── smoke/
    ├── regression/
    └── full/
```

## Test Taxonomy

### 1. Smoke

Tujuan:

1. memberi sinyal cepat bahwa stack lokal hidup
2. memvalidasi login, health, dan 1-2 flow inti

Target waktu:

1. cepat
2. aman dijalankan sebelum demo atau manual QA

### 2. Regression

Tujuan:

1. mencakup seluruh endpoint dan flow bisnis utama
2. menjadi suite default sebelum merge besar

### 3. Full

Tujuan:

1. mencakup regression ditambah branch failure, authorization denial, async verification, reconciliation, dan maintenance
2. menjadi suite malam atau pre-release

## Tagged Execution Strategy

Setiap feature harus diberi tag eksplisit.

Tag minimum:

1. `@smoke`
2. `@regression`
3. `@full`
4. `@negative`
5. `@security`
6. `@workflow`
7. `@evidence`
8. `@messaging`
9. `@maintenance`
10. `@reconciliation`
11. `@locking`

## Helper Strategy

Karate cukup kuat untuk HTTP flow, tetapi Sentinel butuh helper teknis kecil.

### Allowed Java Helpers

Helper Java boleh dipakai hanya untuk hal berikut:

1. membuka row lock atau table lock sementara
2. query database untuk assertion
3. publish Kafka event mentah bila perlu
4. baca Mailpit inbox
5. setup/cleanup data teknis yang tidak tersedia di API

### Forbidden Direction

Helper Java tidak boleh mengambil alih flow bisnis utama.

Flow bisnis tetap harus terbaca di feature file, bukan disembunyikan di helper besar.

## Internal Work Order

### 1. Foundation

Kerja:

1. tambah dependency Karate ke `sentinel-integration-tests`
2. tambah JUnit runner Karate
3. tambah `karate-config.js`
4. tambah env model untuk `baseUrl`, Keycloak, DB, Mailpit, dan helper toggles

Success criteria:

1. satu feature smoke bisa jalan terhadap app lokal yang running

### 2. Common Reusable Building Blocks

Kerja:

1. reusable login flow
2. reusable report-case bootstrap flow
3. reusable polling helper untuk async completion
4. reusable DB assertion bridge

Success criteria:

1. feature baru tidak perlu copy-paste login dan setup panjang terus-menerus

### 3. Core Business Flow Coverage

Kerja:

1. implement seluruh flow utama sesuai coverage matrix
2. pisahkan success path, negative path, dan security path

Success criteria:

1. seluruh endpoint dan business flow utama punya scenario aktif

### 4. Async and Integration Boundary Coverage

Kerja:

1. outbox/notification visible behavior
2. workflow reconciliation
3. maintenance operation
4. evidence upload/finalize behavior

Success criteria:

1. async flow tidak lagi hanya bergantung pada white-box test lama

### 5. Locking and Race Coverage

Kerja:

1. row lock decision approval
2. table lock maintenance operation
3. no-op assignment conflict

Success criteria:

1. critical concurrency contract terlihat dari live API result

### 6. Makefile and CI Integration

Kerja:

1. tambah target `karate-smoke`
2. tambah target `karate-regression`
3. tambah target `karate-full`
4. pastikan parameter environment bisa di-override

Success criteria:

1. suite bisa dijalankan konsisten secara lokal dan CI

## Verification Model

Setiap scenario Karate harus sebisa mungkin punya **lebih dari satu lapis bukti**:

1. response HTTP
2. read-back API
3. DB assertion atau observable side effect

Sentinel tidak boleh puas hanya dengan `200 OK`.

## Relationship With Existing Test Suite

Test existing yang sekarang tetap punya peran:

1. `JUnit + Testcontainers` tetap menjadi baseline infra-sensitive verification
2. Karate menjadi primary business-flow regression surface
3. area yang benar-benar race-heavy boleh tetap punya Java test pendamping

Model target:

1. business readable regression -> Karate
2. low-level deterministic infra/concurrency -> Java existing

## Makefile Target Plan

Target baru yang direkomendasikan:

1. `karate-smoke`
2. `karate-regression`
3. `karate-full`

Command shape yang direkomendasikan:

1. `mvn -q -pl sentinel-integration-tests -am "-Dtest=KarateSmokeIT" test`
2. `mvn -q -pl sentinel-integration-tests -am "-Dtest=KarateRegressionIT" test`
3. `mvn -q -pl sentinel-integration-tests -am "-Dtest=KarateFullIT" test`

Nama runner final boleh menyesuaikan, tetapi konsep pembagiannya harus tetap.

## Risks

### Risk 1

Karate feature menjadi terlalu gemuk dan sulit dirawat.

Control:

1. pakai reusable common feature
2. batasi helper per domain
3. pisahkan smoke/regression/full

### Risk 2

Concurrency test dipaksa terlalu murni DSL lalu menjadi rapuh.

Control:

1. pakai helper Java kecil untuk lock orchestration
2. tetap simpan Java integration test existing sebagai safety net

### Risk 3

Evidence upload ke MinIO dan async notification menjadi flaky.

Control:

1. gunakan polling dengan timeout eksplisit
2. validasi observable outcome, bukan timing kebetulan

### Risk 4

Suite terlalu lambat untuk dev loop.

Control:

1. ada smoke suite kecil
2. regression dibatasi untuk happy path + core negative path
3. full suite dijalankan terpisah

## Definition Of Done

Plan ini baru dianggap berhasil dieksekusi jika:

1. Karate runner hidup di repo
2. ada smoke, regression, dan full suite
3. seluruh flow pada coverage matrix sudah punya scenario aktif
4. minimal satu live run terhadap app running lulus untuk smoke dan regression
5. Makefile target baru tersedia
6. README/testing docs diperbarui

## Immediate Next Step

Langkah implementasi pertama setelah plan ini:

1. tambahkan dependency Karate dan runner dasar di `sentinel-integration-tests`
2. bangun `karate-config.js`
3. implement smoke pack paling kecil:
   - health
   - login
   - report create/triage
   - case create/get
