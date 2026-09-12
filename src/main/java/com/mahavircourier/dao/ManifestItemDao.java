package com.mahavircourier.dao;

import com.mahavircourier.model.ManifestItem;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class ManifestItemDao {

    private final JdbcTemplate jdbcTemplate;

    public ManifestItemDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<ManifestItem> ROW_MAPPER = (rs, rowNum) -> {
        ManifestItem item = new ManifestItem();
        item.setId(rs.getLong("id"));
        item.setManifestId(rs.getLong("manifest_id"));
        item.setSerialNo(rs.getInt("serial_no"));
        item.setConsignmentNo(rs.getString("consignment_no"));
        item.setDestinationCity(rs.getString("destination_city"));
        item.setNumberOfBoxes(rs.getInt("number_of_boxes"));
        item.setWeightKg(rs.getBigDecimal("weight_kg"));
        item.setReceiverName(rs.getString("receiver_name"));
        item.setReceiverPhone(rs.getString("receiver_phone"));
        long shipmentId = rs.getLong("shipment_id");
        item.setShipmentId(rs.wasNull() ? null : shipmentId);
        try {
            long partyId = rs.getLong("shipment_party_id");
            item.setPartyId(rs.wasNull() ? null : partyId);
            item.setPartyName(rs.getString("shipment_party_name"));
        } catch (Exception ignored) {
            // optional join
        }
        try {
            item.setFreightCharge(rs.getBigDecimal("freight_charge"));
            item.setShipmentStatus(rs.getString("shipment_status"));
        } catch (Exception ignored) {
            // optional join
        }
        try {
            long invoiceId = rs.getLong("invoice_id");
            item.setInvoiceId(rs.wasNull() ? null : invoiceId);
            item.setInvoiceNumber(rs.getString("invoice_number"));
            item.setBilledBranchName(rs.getString("billed_branch_name"));
        } catch (Exception ignored) {
            // optional invoice join
        }
        return item;
    };

    public List<ManifestItem> findByManifestId(Long manifestId) {
        return jdbcTemplate.query(
                "SELECT i.*, s.freight_charge, s.status AS shipment_status, " +
                        "s.party_id AS shipment_party_id, p.party_name AS shipment_party_name, " +
                        "inv.id AS invoice_id, inv.invoice_number, " +
                        "COALESCE(b.branch_name, inv_b.branch_name) AS billed_branch_name " +
                        "FROM manifest_items i " +
                        "LEFT JOIN shipments s ON s.id = i.shipment_id " +
                        "LEFT JOIN parties p ON p.id = s.party_id " +
                        "LEFT JOIN invoices inv ON inv.shipment_id = s.id " +
                        "LEFT JOIN branches b ON b.id = s.assigned_branch_id " +
                        "LEFT JOIN branches inv_b ON inv_b.id = inv.billed_branch_id " +
                        "WHERE i.manifest_id = ? ORDER BY i.serial_no ASC",
                ROW_MAPPER, manifestId);
    }

    public Optional<ManifestItem> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM manifest_items WHERE id = ?", ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsByConsignmentNo(String consignmentNo) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manifest_items WHERE consignment_no = ?",
                Integer.class, consignmentNo);
        return count != null && count > 0;
    }

    public boolean existsByConsignmentNoExcluding(String consignmentNo, Long excludeItemId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manifest_items WHERE consignment_no = ? AND id <> ?",
                Integer.class, consignmentNo, excludeItemId);
        return count != null && count > 0;
    }

    public Long save(ManifestItem item) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO manifest_items (manifest_id, serial_no, consignment_no, destination_city, " +
                            "number_of_boxes, weight_kg, receiver_name, receiver_phone, shipment_id) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, item.getManifestId());
            ps.setInt(2, item.getSerialNo());
            ps.setString(3, item.getConsignmentNo());
            ps.setString(4, item.getDestinationCity());
            ps.setInt(5, item.getNumberOfBoxes());
            ps.setBigDecimal(6, item.getWeightKg());
            ps.setString(7, item.getReceiverName());
            ps.setString(8, item.getReceiverPhone());
            if (item.getShipmentId() != null) {
                ps.setLong(9, item.getShipmentId());
            } else {
                ps.setNull(9, Types.BIGINT);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public void update(ManifestItem item) {
        jdbcTemplate.update(
                "UPDATE manifest_items SET serial_no = ?, destination_city = ?, number_of_boxes = ?, " +
                        "weight_kg = ?, receiver_name = ?, receiver_phone = ?, shipment_id = ? WHERE id = ?",
                item.getSerialNo(),
                item.getDestinationCity(),
                item.getNumberOfBoxes(),
                item.getWeightKg(),
                item.getReceiverName(),
                item.getReceiverPhone(),
                item.getShipmentId(),
                item.getId());
    }

    public void updateShipmentId(Long itemId, Long shipmentId) {
        jdbcTemplate.update("UPDATE manifest_items SET shipment_id = ? WHERE id = ?", shipmentId, itemId);
    }

    public int nextSerial(Long manifestId) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(serial_no), 0) FROM manifest_items WHERE manifest_id = ?",
                Integer.class, manifestId);
        return (max != null ? max : 0) + 1;
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM manifest_items WHERE id = ?", id);
    }

    public List<String> findUsedConsignmentNosByParty(Long partyId) {
        return jdbcTemplate.query(
                "SELECT i.consignment_no FROM manifest_items i " +
                        "JOIN manifests m ON m.id = i.manifest_id " +
                        "WHERE m.party_id = ? ORDER BY CAST(i.consignment_no AS UNSIGNED)",
                (rs, rowNum) -> rs.getString("consignment_no"),
                partyId);
    }
}
