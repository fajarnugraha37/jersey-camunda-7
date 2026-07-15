package com.sentinel.enforcement.persistence;

import java.util.function.Function;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public abstract class MyBatisRepositorySupport {
  private final SqlSessionFactory sqlSessionFactory;

  protected MyBatisRepositorySupport(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  protected final <T> T executeRead(Function<SqlSession, T> callback) {
    SqlSession currentSession = MyBatisSessionContext.currentSession();
    if (currentSession != null) {
      return callback.apply(currentSession);
    }
    try (SqlSession session = sqlSessionFactory.openSession()) {
      return callback.apply(session);
    }
  }

  protected final <T> T executeWrite(Function<SqlSession, T> callback) {
    SqlSession currentSession = MyBatisSessionContext.currentSession();
    if (currentSession != null) {
      return callback.apply(currentSession);
    }
    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      try {
        T result = callback.apply(session);
        session.commit();
        return result;
      } catch (RuntimeException | Error exception) {
        session.rollback();
        throw exception;
      }
    }
  }
}
