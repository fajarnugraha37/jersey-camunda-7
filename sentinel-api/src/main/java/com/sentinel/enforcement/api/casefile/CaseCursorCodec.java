package com.sentinel.enforcement.api.casefile;

import com.sentinel.enforcement.application.casefile.CasePage;
import com.sentinel.enforcement.application.casefile.ListCasesQuery;
import jakarta.ws.rs.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

final class CaseCursorCodec {
  private CaseCursorCodec() {}

  static ListCasesQuery decode(String cursor, int limit) {
    if (cursor == null || cursor.isBlank()) {
      return new ListCasesQuery(null, null, limit);
    }
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = decoded.split("\\|", -1);
      if (parts.length != 2) {
        throw new IllegalArgumentException("Cursor payload must have exactly two parts.");
      }
      return new ListCasesQuery(Instant.parse(parts[0]), UUID.fromString(parts[1]), limit);
    } catch (RuntimeException exception) {
      throw new BadRequestException("Cursor is invalid.", exception);
    }
  }

  static String encode(CasePage casePage) {
    if (!casePage.hasNextPage()) {
      return null;
    }
    String raw = casePage.nextCursorCreatedAt() + "|" + casePage.nextCursorId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }
}
