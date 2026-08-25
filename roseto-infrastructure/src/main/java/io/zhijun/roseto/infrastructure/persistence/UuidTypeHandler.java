package io.zhijun.roseto.infrastructure.persistence;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import java.sql.*;
import java.util.UUID;

@MappedTypes(UUID.class)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType) throws SQLException { ps.setObject(i, parameter); }
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException { return value(rs.getObject(columnName)); }
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException { return value(rs.getObject(columnIndex)); }
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException { return value(cs.getObject(columnIndex)); }
    private UUID value(Object value) { return value == null ? null : value instanceof UUID u ? u : UUID.fromString(value.toString()); }
}
