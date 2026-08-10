package com.mahavircourier.dao;

import com.mahavircourier.model.Shipment;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ShipmentDao {

    private final JdbcTemplate jdbcTemplate;

    public ShipmentDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Shipment> SHIPMENT_ROW_MAPPER = (rs, rowNum) -> {
        Shipment s = new Shipment();
        s.setId(rs.getLong("id"));
        s.setTrackingId(rs.getString("tracking_id"));
        s.setSenderName(rs.getString("sender_name"));
        s.setSenderPhone(rs.getString("sender_phone"));
        s.setSenderAddress(rs.getString("sender_address"));
        s.setReceiverName(rs.getString("receiver_name"));
        s.setReceiverPhone(rs.getString("receiver_phone"));
        s.setReceiverAddress(rs.getString("receiver_address"));
        s.setOriginCity(rs.getString("origin_city"));
        s.setDestinationCity(rs.getString("destination_city"));
        s.setWeightKg(rs.getBigDecimal("weight_kg"));
        s.setServiceType(rs.getString("service_type"));
        s.setStatus(rs.getString("status"));
        long bookedBy = rs.getLong("booked_by_user_id");
        s.setBookedByUserId(rs.wasNull() ? null : bookedBy);
        Date expected = rs.getDate("expected_delivery");
        if (expected != null) {
            s.setExpectedDelivery(expected.toLocalDate());
        }
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            s.setCreatedAt(created.toLocalDateTime());
        }
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            s.setUpdatedAt(updated.toLocalDateTime());
        }
        return s;
    };

    public Optional<Shipment> findByTrackingId(String trackingId) {
        try {
            Shipment shipment = jdbcTemplate.queryForObject(
                    "SELECT * FROM shipments WHERE tracking_id = ?",
                    SHIPMENT_ROW_MAPPER, trackingId);
            return Optional.ofNullable(shipment);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Shipment> findById(Long id) {
        try {
            Shipment shipment = jdbcTemplate.queryForObject(
                    "SELECT * FROM shipments WHERE id = ?", SHIPMENT_ROW_MAPPER, id);
            return Optional.ofNullable(shipment);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsByTrackingId(String trackingId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipments WHERE tracking_id = ?", Integer.class, trackingId);
        return count != null && count > 0;
    }

    public List<Shipment> findByBookedByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM shipments WHERE booked_by_user_id = ? ORDER BY created_at DESC",
                SHIPMENT_ROW_MAPPER, userId);
    }

    public List<Shipment> findAll() {
        return jdbcTemplate.query("SELECT * FROM shipments ORDER BY created_at DESC", SHIPMENT_ROW_MAPPER);
    }

    public List<Shipment> findRecent(int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM shipments ORDER BY created_at DESC LIMIT ?", SHIPMENT_ROW_MAPPER, limit);
    }

    /**
     * Admin search: optional free-text query (tracking id, sender/receiver name or phone)
     * and optional exact status filter, with LIMIT/OFFSET pagination.
     */
    public List<Shipment> search(String query, String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM shipments WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendSearchFilters(sql, params, query, status);
        sql.append(" ORDER BY updated_at DESC, created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql.toString(), SHIPMENT_ROW_MAPPER, params.toArray());
    }

    public long countSearch(String query, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM shipments WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendSearchFilters(sql, params, query, status);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    private void appendSearchFilters(StringBuilder sql, List<Object> params, String query, String status) {
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim() + "%";
            sql.append(" AND (tracking_id LIKE ? OR sender_name LIKE ? OR receiver_name LIKE ?")
                    .append(" OR sender_phone LIKE ? OR receiver_phone LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status.trim());
        }
    }

    public Long save(Shipment s) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO shipments (tracking_id, sender_name, sender_phone, sender_address, " +
                            "receiver_name, receiver_phone, receiver_address, origin_city, destination_city, " +
                            "weight_kg, service_type, status, booked_by_user_id, expected_delivery) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, s.getTrackingId());
            ps.setString(2, s.getSenderName());
            ps.setString(3, s.getSenderPhone());
            ps.setString(4, s.getSenderAddress());
            ps.setString(5, s.getReceiverName());
            ps.setString(6, s.getReceiverPhone());
            ps.setString(7, s.getReceiverAddress());
            ps.setString(8, s.getOriginCity());
            ps.setString(9, s.getDestinationCity());
            ps.setBigDecimal(10, s.getWeightKg());
            ps.setString(11, s.getServiceType());
            ps.setString(12, s.getStatus());
            if (s.getBookedByUserId() != null) {
                ps.setLong(13, s.getBookedByUserId());
            } else {
                ps.setNull(13, java.sql.Types.BIGINT);
            }
            if (s.getExpectedDelivery() != null) {
                ps.setDate(14, Date.valueOf(s.getExpectedDelivery()));
            } else {
                ps.setNull(14, java.sql.Types.DATE);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public void updateStatus(Long shipmentId, String status) {
        jdbcTemplate.update("UPDATE shipments SET status = ? WHERE id = ?", status, shipmentId);
    }
}
