package com.sentinel.enforcement.persistence.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public final class UuidTypeHandler extends BaseTypeHandler<UUID> {

  @Override
  public void setNonNullParameter(
      PreparedStatement preparedStatement, int index, UUID parameter, JdbcType jdbcType)
      throws SQLException {
    preparedStatement.setObject(index, parameter);
  }

  @Override
  public UUID getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
    return resultSet.getObject(columnName, UUID.class);
  }

  @Override
  public UUID getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
    return resultSet.getObject(columnIndex, UUID.class);
  }

  @Override
  public UUID getNullableResult(CallableStatement callableStatement, int columnIndex)
      throws SQLException {
    return callableStatement.getObject(columnIndex, UUID.class);
  }
}
