package com.mahavircourier.dao;

import com.mahavircourier.model.TrackingEvent;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class TrackingEventDao {

    private final JdbcTemplate jdbcTemplate;

    public TrackingEventDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<TrackingEvent> EVENT_ROW_MAPPER = (rs, rowNum) -> {
        TrackingEvent e = new TrackingEvent();
        e.setId(rs.getLong("id"));
        e.setShipmentId(rs.getLong("shipment_id"));
        e.setStatus(rs.getString("status"));
        e.setLocation(rs.getString("location"));
        e.setRemarks(rs.getString("remarks"));
        Timestamp ts = rs.getTimestamp("event_time");
        if (ts != null) {
            e.setEventTime(ts.toLocalDateTime());
        }
        return e;
    };

    public List<TrackingEvent> findByShipmentId(Long shipmentId) {
        return jdbcTemplate.query(
                "SELECT * FROM tracking_events WHERE shipment_id = ? ORDER BY event_time ASC, id ASC",
                EVENT_ROW_MAPPER, shipmentId);
    }

    public Optional<TrackingEvent> findById(Long id) {
        try {
            TrackingEvent event = jdbcTemplate.queryForObject(
                    "SELECT * FROM tracking_events WHERE id = ?", EVENT_ROW_MAPPER, id);
            return Optional.ofNullable(event);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void save(TrackingEvent event) {
        jdbcTemplate.update(
                "INSERT INTO tracking_events (shipment_id, status, location, remarks) VALUES (?, ?, ?, ?)",
                event.getShipmentId(), event.getStatus(), event.getLocation(), event.getRemarks());
    }

    public void update(TrackingEvent event) {
        jdbcTemplate.update(
                "UPDATE tracking_events SET status = ?, location = ?, remarks = ? WHERE id = ?",
                event.getStatus(), event.getLocation(), event.getRemarks(), event.getId());
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM tracking_events WHERE id = ?", id);
    }
}
