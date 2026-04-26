package com.ai.agent.infrastructure.persistent.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;

/**
 * Long数组类型处理器 — Java Long[] ↔ PostgreSQL BIGINT[]
 */
@MappedTypes(Long[].class)
@MappedJdbcTypes(JdbcType.ARRAY)
public class LongArrayTypeHandler extends BaseTypeHandler<Long[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Long[] parameter, JdbcType jdbcType) throws SQLException {
        Array array = ps.getConnection().createArrayOf("BIGINT", parameter);
        ps.setArray(i, array);
    }

    @Override
    public Long[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toLongArray(rs.getArray(columnName));
    }

    @Override
    public Long[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toLongArray(rs.getArray(columnIndex));
    }

    @Override
    public Long[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toLongArray(cs.getArray(columnIndex));
    }

    private Long[] toLongArray(Array array) throws SQLException {
        if (array == null) return new Long[0];
        Object[] objArray = (Object[]) array.getArray();
        Long[] result = new Long[objArray.length];
        for (int i = 0; i < objArray.length; i++) {
            result[i] = objArray[i] != null ? ((Number) objArray[i]).longValue() : null;
        }
        return result;
    }
}
