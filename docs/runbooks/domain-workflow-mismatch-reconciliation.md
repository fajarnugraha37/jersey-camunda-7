# Domain Workflow Mismatch Reconciliation

Runbook ini dipakai ketika status bisnis di `case_record`, baris korelasi di `workflow_instance`, dan posisi runtime Camunda tidak lagi sinkron.

## Kapan dipakai

- Case masih aktif tetapi task workflow hilang.
- Case sudah terminal (`DECIDED`, `CLOSED`, `CANCELLED`) tetapi workflow masih aktif.
- Baris `workflow_instance` hilang atau statusnya tidak cocok dengan runtime/historic workflow.

## Endpoint operator

- `GET /api/v1/workflow-reconciliation`
- `POST /api/v1/workflow-reconciliation/{caseId}/actions`

Hanya actor `SUPERVISOR` dan `SYSTEM_ADMIN` yang boleh memakai endpoint ini. Hasil list tetap difilter oleh jurisdiction actor.

## Cara investigasi

1. Panggil `GET /api/v1/workflow-reconciliation?limit=20`.
2. Sempitkan hasil dengan `q`, `searchField/searchValue`, `issueType`, `caseStatus`, `workflowCorrelationStatus`, `sortBy`, dan `sortDirection`.
3. Cek `issueType`, `workflowCorrelationStatus`, `correlationProcessInstanceId`, `runtimeProcessInstanceId`, dan `availableActions`.

## Arti action

- `AUTO_REPAIR`
  - Dipakai saat runtime aktif adalah sumber kebenaran untuk case aktif, atau historic workflow selesai adalah sumber kebenaran untuk case terminal.
  - Action ini meng-upsert ulang `workflow_instance` melalui adapter MyBatis.
- `TERMINATE_RUNTIME`
  - Dipakai hanya saat case sudah terminal tetapi Camunda runtime masih aktif.
  - Action ini menghentikan runtime Camunda lalu mengubah korelasi menjadi `COMPLETED` atau `CANCELLED`.

## Bukti keberhasilan

- Response action mengembalikan `result=REPAIRED`.
- `workflow_instance.status` berubah ke nilai yang benar.
- Case hilang dari hasil `GET /api/v1/workflow-reconciliation`.
- Audit event `WorkflowReconciliationPerformed` bertambah untuk `case_id` terkait.

## Contoh

```bash
curl -X GET \
  "http://localhost:8080/api/v1/workflow-reconciliation?q=JKT-ENF-2026&sortBy=CASE_NUMBER&sortDirection=ASC" \
  -H "Authorization: Bearer <token>"
```

```bash
curl -X POST \
  "http://localhost:8080/api/v1/workflow-reconciliation/<caseId>/actions" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "action": "AUTO_REPAIR",
    "reason": "Restore workflow correlation from active runtime"
  }'
```

## Jangan lakukan

- Jangan mengubah `workflow_instance` manual dari SQL operasional biasa jika endpoint reconciliation masih bisa memulihkan state dengan aman.
- Jangan menjalankan `TERMINATE_RUNTIME` untuk case yang masih berada di state aktif.
- Jangan menganggap role saja cukup; actor tetap harus punya jurisdiction yang sesuai.
