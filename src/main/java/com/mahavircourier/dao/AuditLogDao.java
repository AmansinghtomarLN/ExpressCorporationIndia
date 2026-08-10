package com.mahavircourier.dao;

import com.mahavircourier.model.AuditLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

@Repository
public class AuditLogDao {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AuditLog> ROW_MAPPER = (rs, rowNum) -> {
        AuditLog a = new AuditLog();
        a.setId(rs.getLong("id"));
        long actorId = rs.getLong("actor_user_id");
        a.setActorUserId(rs.wasNull() ? null : actorId);
        a.setActorEmail(rs.getString("actor_email"));
        a.setAction(rs.getString("action"));
        a.setEntityType(rs.getString("entity_type"));
        a.setEntityId(rs.getString("entity_id"));
        a.setDetails(rs.getString("details"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            a.setCreatedAt(ts.toLocalDateTime());
        }
        return a;
    };

    public List<AuditLog> findAll(int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT ?",
                ROW_MAPPER, limit);
    }

    public Long save(AuditLog log) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO audit_logs (actor_user_id, actor_email, action, entity_type, entity_id, details) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            if (log.getActorUserId() != null) {
                ps.setLong(1, log.getActorUserId());
            } else {
                ps.setNull(1, Types.BIGINT);
            }
            ps.setString(2, log.getActorEmail());
            ps.setString(3, log.getAction());
            ps.setString(4, log.getEntityType());
            ps.setString(5, log.getEntityId());
            ps.setString(6, log.getDetails());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }
}
