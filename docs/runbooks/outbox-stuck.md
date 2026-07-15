# Outbox Stuck

## Trigger

- `outbox_event` rows remain `PENDING` for longer than expected.
- Notifications stop appearing even though case/evidence writes still succeed.

## Expected behavior

- Business writes tetap committed.
- Publisher background terus mencoba publish ulang berdasarkan `available_at`.

## Checks

1. Jalankan `make kafka-topics` untuk memastikan broker masih reachable.
2. Cek backlog:
   ```sql
   SELECT topic, status, publish_attempts, available_at, last_error
   FROM outbox_event
   WHERE status = 'PENDING'
   ORDER BY available_at, occurred_at;
   ```
3. Jika `last_error` menunjukkan konektivitas Kafka, pastikan `KAFKA_BOOTSTRAP_SERVERS` cocok dengan environment runtime.
4. Pastikan lease tidak tersangkut:
   ```sql
   SELECT event_id, lease_owner, lease_expires_at
   FROM outbox_event
   WHERE status = 'PENDING'
     AND lease_expires_at > now();
   ```

## Operator action

- Pulihkan broker atau konektivitas jaringan.
- Tunggu sampai `available_at` berikutnya terlewati; publisher akan mencoba ulang otomatis.
- Restart aplikasi hanya bila publisher thread diduga mati total; row outbox tidak perlu diubah manual.

## Data consistency expectation

- Tidak ada business commit yang hilang.
- Row outbox tidak boleh dihapus manual kecuali setelah analisis insiden yang jelas.
