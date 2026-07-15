package com.sentinel.enforcement.persistence;

import com.sentinel.enforcement.application.messaging.ApplicationTransactionManager;
import java.util.function.Supplier;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public final class MyBatisTransactionManager implements ApplicationTransactionManager {
  private final SqlSessionFactory sqlSessionFactory;

  public MyBatisTransactionManager(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  @Override
  public <T> T required(Supplier<T> work) {
    SqlSession currentSession = MyBatisSessionContext.currentSession();
    if (currentSession != null) {
      return work.get();
    }

    try (SqlSession session = sqlSessionFactory.openSession(false)) {
      MyBatisSessionContext.bind(session);
      try {
        T result = work.get();
        session.commit();
        return result;
      } catch (RuntimeException | Error exception) {
        session.rollback();
        throw exception;
      } finally {
        MyBatisSessionContext.clear();
      }
    }
  }
}
