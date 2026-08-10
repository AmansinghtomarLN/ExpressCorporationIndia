package com.mahavircourier.dao;

import com.mahavircourier.model.Invoice;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class InvoiceDao {

    private final JdbcTemplate jdbcTemplate;

    public InvoiceDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Invoice> ROW_MAPPER = (rs, rowNum) -> {
        Invoice inv = new Invoice();
        inv.setId(rs.getLong("id"));
        inv.setShipmentId(rs.getLong("shipment_id"));
        inv.setInvoiceNumber(rs.getString("invoice_number"));
        inv.setFreightAmount(rs.getBigDecimal("freight_amount"));
        inv.setCodAmount(rs.getBigDecimal("cod_amount"));
        inv.setTotalAmount(rs.getBigDecimal("total_amount"));
        inv.setStatus(rs.getString("status"));
        inv.setNotes(rs.getString("notes"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            inv.setCreatedAt(created.toLocalDateTime());
        }
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            inv.setUpdatedAt(updated.toLocalDateTime());
        }
        try {
            inv.setTrackingId(rs.getString("tracking_id"));
        } catch (Exception ignored) {
            // optional join column
        }
        return inv;
    };

    public List<Invoice> findAll() {
        return jdbcTemplate.query(
                "SELECT i.*, s.tracking_id FROM invoices i " +
                        "LEFT JOIN shipments s ON s.id = i.shipment_id ORDER BY i.created_at DESC",
                ROW_MAPPER);
    }

    public Optional<Invoice> findById(Long id) {
        try {
            Invoice invoice = jdbcTemplate.queryForObject(
                    "SELECT i.*, s.tracking_id FROM invoices i " +
                            "LEFT JOIN shipments s ON s.id = i.shipment_id WHERE i.id = ?",
                    ROW_MAPPER, id);
            return Optional.ofNullable(invoice);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Invoice> findByShipmentId(Long shipmentId) {
        try {
            Invoice invoice = jdbcTemplate.queryForObject(
                    "SELECT i.*, s.tracking_id FROM invoices i " +
                            "LEFT JOIN shipments s ON s.id = i.shipment_id WHERE i.shipment_id = ? " +
                            "ORDER BY i.created_at DESC LIMIT 1",
                    ROW_MAPPER, shipmentId);
            return Optional.ofNullable(invoice);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Long save(Invoice invoice) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO invoices (shipment_id, invoice_number, freight_amount, cod_amount, " +
                            "total_amount, status, notes) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, invoice.getShipmentId());
            ps.setString(2, invoice.getInvoiceNumber());
            ps.setBigDecimal(3, invoice.getFreightAmount() != null ? invoice.getFreightAmount() : BigDecimal.ZERO);
            ps.setBigDecimal(4, invoice.getCodAmount() != null ? invoice.getCodAmount() : BigDecimal.ZERO);
            ps.setBigDecimal(5, invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO);
            ps.setString(6, invoice.getStatus());
            ps.setString(7, invoice.getNotes());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public void updateStatus(Long id, String status) {
        jdbcTemplate.update("UPDATE invoices SET status = ? WHERE id = ?", status, id);
    }

    public long countByStatus(String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE status = ?", Long.class, status);
        return count != null ? count : 0L;
    }

    public int countTodayInvoices(String datePrefix) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE invoice_number LIKE ?",
                Integer.class, datePrefix + "%");
        return count != null ? count : 0;
    }
}
