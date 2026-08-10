package com.mahavircourier.dao;

import com.mahavircourier.model.NotificationLog;
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
public class NotificationDao {

    private final JdbcTemplate jdbcTemplate;

    public NotificationDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<NotificationLog> ROW_MAPPER = (rs, rowNum) -> {
        NotificationLog n = new NotificationLog();
        n.setId(rs.getLong("id"));
        long shipmentId = rs.getLong("shipment_id");
        n.setShipmentId(rs.wasNull() ? null : shipmentId);
        n.setChannel(rs.getString("channel"));
        n.setRecipient(rs.getString("recipient"));
        n.setSubject(rs.getString("subject"));
        n.setBody(rs.getString("body"));
        n.setStatus(rs.getString("status"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            n.setCreatedAt(ts.toLocalDateTime());
        }
        return n;
    };

    public List<NotificationLog> findAll(int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM notifications ORDER BY created_at DESC LIMIT ?",
                ROW_MAPPER, limit);
    }

    public Long save(NotificationLog log) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO notifications (shipment_id, channel, recipient, subject, body, status) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            if (log.getShipmentId() != null) {
                ps.setLong(1, log.getShipmentId());
            } else {
                ps.setNull(1, Types.BIGINT);
            }
            ps.setString(2, log.getChannel());
            ps.setString(3, log.getRecipient());
            ps.setString(4, log.getSubject());
            ps.setString(5, log.getBody());
            ps.setString(6, log.getStatus() != null ? log.getStatus() : "SENT");
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public long countByStatus(String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE status = ?", Long.class, status);
        return count != null ? count : 0L;
    }
}
