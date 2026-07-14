package com.sentinel.enforcement.persistence;

import com.sentinel.enforcement.persistence.casefile.CaseMyBatisMapper;
import com.sentinel.enforcement.persistence.evidence.EvidenceMyBatisMapper;
import com.sentinel.enforcement.persistence.report.ReportMyBatisMapper;
import com.sentinel.enforcement.persistence.typehandler.UuidTypeHandler;
import com.sentinel.enforcement.persistence.workflow.WorkflowInstanceMyBatisMapper;
import com.sentinel.enforcement.persistence.workflow.WorkflowReconciliationMyBatisMapper;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.JdbcType;

public final class PersistenceModule {
  private PersistenceModule() {}

  public static SqlSessionFactory createSqlSessionFactory(DataSource dataSource) {
    TransactionFactory transactionFactory = new JdbcTransactionFactory();
    Environment environment = new Environment("sentinel", transactionFactory, dataSource);
    Configuration configuration = new Configuration(environment);
    configuration.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
    configuration
        .getTypeHandlerRegistry()
        .register(UUID.class, JdbcType.OTHER, UuidTypeHandler.class);
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.addMapper(CaseMyBatisMapper.class);
    configuration.addMapper(EvidenceMyBatisMapper.class);
    configuration.addMapper(ReportMyBatisMapper.class);
    configuration.addMapper(WorkflowInstanceMyBatisMapper.class);
    configuration.addMapper(WorkflowReconciliationMyBatisMapper.class);
    return new SqlSessionFactoryBuilder().build(configuration);
  }
}
