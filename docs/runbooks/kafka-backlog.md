# Kafka Backlog

## Trigger

- Topic utama atau retry topic tumbuh terus.
- Notification tertunda padahal outbox sudah `PUBLISHED`.

## Checks

1. Lihat topic yang aktif:
   ```bash
   make kafka-topics
   ```
2. Cek consumer group lag dengan tool Kafka yang tersedia di environment operator.
3. Pastikan aplikasi consumer masih hidup dan tidak terus-menerus melempar error pada log.
4. Jika backlog hanya terjadi pada `*.retry`, inspect error root cause dan lihat DLQ runbook.

## Expected behavior

- Backlog sementara boleh terjadi saat broker restart atau downstream pulih.
- Backlog yang menetap berarti consumer tidak mampu mengejar laju event atau ada poison event berulang.

## Operator action

- Pastikan broker sehat.
- Pastikan instance aplikasi dengan notification consumer aktif.
- Jika perlu, scale application replica dengan mempertimbangkan group consumer semantics dan shared DB pressure.
