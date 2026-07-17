package com.sentinel.enforcement.persistence;

import java.sql.SQLException;

public final class PersistenceExceptionClassifier {
  private static final String SQL_STATE_LOCK_NOT_AVAILABLE = "55P03";
  private static final String SQL_STATE_UNIQUE_VIOLATION = "23505";

  private PersistenceExceptionClassifier() {}

  public static boolean isLockNotAvailable(Throwable throwable) {
    SQLException sqlException = findSQLException(throwable);
    return sqlException != null && SQL_STATE_LOCK_NOT_AVAILABLE.equals(sqlException.getSQLState());
  }

  public static boolean isUniqueViolation(Throwable throwable) {
    SQLException sqlException = findSQLException(throwable);
    return sqlException != null && SQL_STATE_UNIQUE_VIOLATION.equals(sqlException.getSQLState());
  }

  private static SQLException findSQLException(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof SQLException sqlException) {
        return sqlException;
      }
      current = current.getCause();
    }
    return null;
  }
}
