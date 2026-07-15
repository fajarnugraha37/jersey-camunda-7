# Dead-Letter Events

## Trigger

- Event muncul di topic `*.dlq`.
- Notification side effect tidak terbentuk walau event sudah keluar dari outbox.

## Expected behavior

- Consumer memindahkan event ke DLQ setelah retry melebihi `NOTIFICATION_MAX_RETRIES`.
- Offset source message tetap di-commit agar poison event tidak memblokir seluruh partition.

## Checks

1. Konsumsi DLQ topic yang relevan, misalnya:
   ```bash
   docker compose exec kafka bash -lc "kafka-console-consumer --bootstrap-server kafka:9092 --topic case.lifecycle.v1.dlq --from-beginning"
   ```
2. Catat `eventId`, event type, dan payload.
3. Cek apakah inbox record sudah ada:
   ```sql
   SELECT *
   FROM inbox_event
   WHERE event_id = '<event-id>'::uuid;
   ```
4. Jika tidak ada inbox row, masalah biasanya ada di mapping/validation consumer.

## Operator action

- Perbaiki bug consumer atau payload producer terlebih dahulu.
- Re-publish event secara terkontrol setelah root cause selesai.
- Jangan menghapus DLQ record tanpa recovery plan yang terdokumentasi.
