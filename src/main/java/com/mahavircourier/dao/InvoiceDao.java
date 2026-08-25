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
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class InvoiceDao {

    private final JdbcTemplate jdbcTemplate;

    public InvoiceDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String SELECT_WITH_JOINS =
            "SELECT i.*, s.tracking_id, s.destination_city, " +
                    "b.branch_name AS billed_branch_name, b.city AS billed_branch_city " +
                    "FROM invoices i " +
                    "LEFT JOIN shipments s ON s.id = i.shipment_id " +
                    "LEFT JOIN branches b ON b.id = COALESCE(i.billed_branch_id, s.assigned_branch_id)";

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
        try {
            long branchId = rs.getLong("billed_branch_id");
            inv.setBilledBranchId(rs.wasNull() ? null : branchId);
            inv.setWeightKg(rs.getBigDecimal("weight_kg"));
            int boxes = rs.getInt("number_of_boxes");
            inv.setNumberOfBoxes(rs.wasNull() ? null : boxes);
            inv.setPerKgRate(rs.getBigDecimal("per_kg_rate"));
            inv.setPerBoxRate(rs.getBigDecimal("per_box_rate"));
        } catch (Exception ignored) {
            // older invoice rows before branch billing columns
        }
        try {
            inv.setBilledBranchName(rs.getString("billed_branch_name"));
            inv.setBilledBranchCity(rs.getString("billed_branch_city"));
            inv.setDestinationCity(rs.getString("destination_city"));
        } catch (Exception ignored) {
            // optional join columns
        }
        return inv;
    };

    public List<Invoice> findAll() {
        return jdbcTemplate.query(SELECT_WITH_JOINS + " ORDER BY i.created_at DESC", ROW_MAPPER);
    }

    public Optional<Invoice> findById(Long id) {
        try {
            Invoice invoice = jdbcTemplate.queryForObject(
                    SELECT_WITH_JOINS + " WHERE i.id = ?",
                    ROW_MAPPER, id);
            return Optional.ofNullable(invoice);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Invoice> findByShipmentId(Long shipmentId) {
        try {
            Invoice invoice = jdbcTemplate.queryForObject(
                    SELECT_WITH_JOINS + " WHERE i.shipment_id = ? ORDER BY i.created_at DESC LIMIT 1",
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
                            "total_amount, status, notes, billed_branch_id, weight_kg, number_of_boxes, " +
                            "per_kg_rate, per_box_rate) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, invoice.getShipmentId());
            ps.setString(2, invoice.getInvoiceNumber());
            ps.setBigDecimal(3, invoice.getFreightAmount() != null ? invoice.getFreightAmount() : BigDecimal.ZERO);
            ps.setBigDecimal(4, invoice.getCodAmount() != null ? invoice.getCodAmount() : BigDecimal.ZERO);
            ps.setBigDecimal(5, invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO);
            ps.setString(6, invoice.getStatus());
            ps.setString(7, invoice.getNotes());
            if (invoice.getBilledBranchId() != null) {
                ps.setLong(8, invoice.getBilledBranchId());
            } else {
                ps.setNull(8, Types.BIGINT);
            }
            ps.setBigDecimal(9, invoice.getWeightKg());
            if (invoice.getNumberOfBoxes() != null) {
                ps.setInt(10, invoice.getNumberOfBoxes());
            } else {
                ps.setNull(10, Types.INTEGER);
            }
            ps.setBigDecimal(11, invoice.getPerKgRate());
            ps.setBigDecimal(12, invoice.getPerBoxRate());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public void updateAmounts(Invoice invoice) {
        jdbcTemplate.update(
                "UPDATE invoices SET freight_amount = ?, cod_amount = ?, total_amount = ?, notes = ?, " +
                        "billed_branch_id = ?, weight_kg = ?, number_of_boxes = ?, per_kg_rate = ?, per_box_rate = ? " +
                        "WHERE id = ?",
                invoice.getFreightAmount() != null ? invoice.getFreightAmount() : BigDecimal.ZERO,
                invoice.getCodAmount() != null ? invoice.getCodAmount() : BigDecimal.ZERO,
                invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO,
                invoice.getNotes(),
                invoice.getBilledBranchId(),
                invoice.getWeightKg(),
                invoice.getNumberOfBoxes(),
                invoice.getPerKgRate(),
                invoice.getPerBoxRate(),
                invoice.getId());
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
