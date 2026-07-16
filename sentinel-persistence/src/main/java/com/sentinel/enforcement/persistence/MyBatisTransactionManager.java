package com.sentinel.enforcement.persistence;

import com.sentinel.enforcement.application.messaging.ApplicationTransactionManager;
import com.sentinel.enforcement.application.messaging.TransactionIsolation;
import com.sentinel.enforcement.application.messaging.TransactionOptions;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;

public final class MyBatisTransactionManager implements ApplicationTransactionManager {
  private final SqlSessionFactory sqlSessionFactory;

  public MyBatisTransactionManager(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  @Override
  public <T> T required(Supplier<T> work) {
    return required(TransactionOptions.defaultWrite(), work);
  }

  @Override
  public <T> T required(TransactionOptions options, Supplier<T> work) {
    SqlSession currentSession = MyBatisSessionContext.currentSession();
    if (currentSession != null) {
      return work.get();
    }

    try (SqlSession session =
        sqlSessionFactory.openSession(
            ExecutorType.SIMPLE, toMyBatisIsolationLevel(options.isolation()))) {
      MyBatisSessionContext.bind(session);
      try {
        session.getConnection().setReadOnly(options.readOnly());
        T result = work.get();
        session.commit();
        return result;
      } catch (SQLException exception) {
        session.rollback();
        throw new IllegalStateException(
            "Failed to configure transaction '" + options.label() + "'.", exception);
      } catch (RuntimeException | Error exception) {
        session.rollback();
        throw exception;
      } finally {
        try {
          session.getConnection().setReadOnly(false);
        } catch (SQLException ignored) {
          // Best-effort cleanup before the session closes.
        }
        MyBatisSessionContext.clear();
      }
    }
  }

  private TransactionIsolationLevel toMyBatisIsolationLevel(TransactionIsolation isolation) {
    return switch (isolation) {
      case READ_COMMITTED -> TransactionIsolationLevel.READ_COMMITTED;
      case REPEATABLE_READ -> TransactionIsolationLevel.REPEATABLE_READ;
      case SERIALIZABLE -> TransactionIsolationLevel.SERIALIZABLE;
    };
  }
}
