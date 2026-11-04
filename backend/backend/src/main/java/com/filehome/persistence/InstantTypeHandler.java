package com.filehome.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(Instant.class)
@MappedJdbcTypes(JdbcType.TIMESTAMP)
public class InstantTypeHandler extends BaseTypeHandler<Instant> {

    @Override
    public void setNonNullParameter(
            PreparedStatement statement,
            int index,
            Instant parameter,
            JdbcType jdbcType) throws SQLException {
        statement.setTimestamp(index, Timestamp.from(parameter));
    }

    @Override
    public Instant getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return toInstant(resultSet.getTimestamp(columnName));
    }

    @Override
    public Instant getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return toInstant(resultSet.getTimestamp(columnIndex));
    }

    @Override
    public Instant getNullableResult(CallableStatement statement, int columnIndex)
            throws SQLException {
        return toInstant(statement.getTimestamp(columnIndex));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
