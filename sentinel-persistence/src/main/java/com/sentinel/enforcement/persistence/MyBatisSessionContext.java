package com.sentinel.enforcement.persistence;

import org.apache.ibatis.session.SqlSession;

final class MyBatisSessionContext {
  private static final ThreadLocal<SqlSession> CURRENT_SESSION = new ThreadLocal<>();

  private MyBatisSessionContext() {}

  static SqlSession currentSession() {
    return CURRENT_SESSION.get();
  }

  static void bind(SqlSession session) {
    CURRENT_SESSION.set(session);
  }

  static void clear() {
    CURRENT_SESSION.remove();
  }
}
