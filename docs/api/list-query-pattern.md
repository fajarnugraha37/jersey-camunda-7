# List Query Pattern

Semua endpoint list baru wajib mengikuti pattern query yang sama seperti `GET /api/v1/cases`, `GET /api/v1/cases/{caseId}/audit-events`, dan `GET /api/v1/tasks`.

## Contract minimum

- Gunakan `cursor` + `limit` untuk pagination.
- Sediakan `q` untuk quick search lintas beberapa field.
- Sediakan `searchField` + `searchValue` untuk targeted keyword search.
- Sediakan `sortBy` + `sortDirection` dengan enum yang di-whitelist, bukan raw column name dari client.
- Tambahkan filter domain spesifik hanya bila memang relevan untuk use case.
- Cursor harus opaque, dan harus mengikat sort + filter scope agar cursor lama tidak bisa dipakai di query scope yang berbeda.

## MyBatis dynamic SQL rules

- Jangan pernah gunakan `${sortBy}` atau `${orderBy}` dari input client.
- Gunakan `<choose>` untuk mapping enum `sortBy` ke column yang aman.
- Gunakan `<where>` atau `<trim>` agar optional filter tidak menghasilkan SQL cacat.
- Gunakan `<foreach>` untuk `IN (...)` list yang aman.
- Gunakan `<set>` untuk dynamic update bila ada partial update.

## Production example

Contoh real ada di [CaseMyBatisMapper.java](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/casefile/CaseMyBatisMapper.java).
Contoh orchestration-backed list yang tetap menjaga kontrak publik yang sama ada di [WorkflowTaskApplicationService.java](/C:/Users/nugra/workspace/project/.jax-rs/.onboard/sentinel-application/src/main/java/com/sentinel/enforcement/application/workflow/WorkflowTaskApplicationService.java).

- `findCasePage(...)` menunjukkan `if`, `choose`, `when`, `otherwise`, `trim`, `where`, dan `foreach`.
- `findAuditEventsPage(...)` menunjukkan pattern yang sama untuk list endpoint kedua.
- `updateCase(...)` menunjukkan penggunaan `set` di mapper update.
- `WorkflowTaskApplicationService` menunjukkan bagaimana contract list yang sama tetap dipertahankan saat source data berasal dari workflow engine, bukan dari satu query MyBatis langsung.

## Canonical snippets

`if`

```xml
<if test="status != null">
  AND status = #{status}
</if>
```

`choose`, `when`, `otherwise`

```xml
<choose>
  <when test='sortBy == "CREATED_AT"'>created_at</when>
  <when test='sortBy == "TITLE"'>LOWER(title)</when>
  <otherwise>created_at</otherwise>
</choose>
```

`trim`

```xml
<trim prefix="(" suffix=")" prefixOverrides="OR ">
  OR LOWER(title) LIKE #{quickSearchPattern}
  OR LOWER(summary) LIKE #{quickSearchPattern}
</trim>
```

`where`

```xml
<where>
  case_id = #{caseId}
  <if test="actorId != null">
    AND actor_id = #{actorId}
  </if>
</where>
```

`set`

```xml
<set>
  updated_at = #{updatedAt},
  updated_by = #{updatedBy},
  version = #{version}
</set>
```

`foreach`

```xml
<foreach collection="jurisdictionCodes" item="jurisdictionCode" open="(" separator="," close=")">
  #{jurisdictionCode}
</foreach>
```

## Checklist for future list APIs

- Tambahkan enum `SearchField` dan `SortBy` di application layer.
- Simpan query parsing di API layer, bukan string raw di service/repository.
- Simpan `cursorScope()` di query object.
- Simpan mapping SQL dinamis di MyBatis mapper dengan enum whitelist.
- Jika source data bukan MyBatis langsung, tetap pertahankan contract publik yang sama: cursor opaque, whitelist field/sort, quick search lintas field yang eksplisit, dan validasi scope mismatch sebagai `400`.
- Tambahkan test untuk quick search, targeted search, sort, dan cursor continuation.
