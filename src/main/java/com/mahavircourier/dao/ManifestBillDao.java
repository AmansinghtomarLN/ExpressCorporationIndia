package com.mahavircourier.dao;

import com.mahavircourier.model.ManifestBill;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ManifestBillDao {

    private static final String SELECT =
            "SELECT b.*, m.manifest_number, p.party_name, br.branch_name, br.city AS branch_city " +
                    "FROM manifest_bills b " +
                    "LEFT JOIN manifests m ON m.id = b.manifest_id " +
                    "LEFT JOIN parties p ON p.id = b.party_id " +
                    "LEFT JOIN branches br ON br.id = b.branch_id";

    private final JdbcTemplate jdbcTemplate;

    public ManifestBillDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<ManifestBill> ROW_MAPPER = (rs, rowNum) -> {
        ManifestBill bill = new ManifestBill();
        bill.setId(rs.getLong("id"));
        bill.setBillNumber(rs.getString("bill_number"));
        bill.setBillType(rs.getString("bill_type"));
        bill.setManifestId(rs.getLong("manifest_id"));
        long partyId = rs.getLong("party_id");
        bill.setPartyId(rs.wasNull() ? null : partyId);
        long branchId = rs.getLong("branch_id");
        bill.setBranchId(rs.wasNull() ? null : branchId);
        bill.setWeightKg(rs.getBigDecimal("weight_kg"));
        bill.setNumberOfBoxes(rs.getInt("number_of_boxes"));
        bill.setPerKgRate(rs.getBigDecimal("per_kg_rate"));
        bill.setPerBoxRate(rs.getBigDecimal("per_box_rate"));
        bill.setFreightAmount(rs.getBigDecimal("freight_amount"));
        bill.setStatus(rs.getString("status"));
        bill.setNotes(rs.getString("notes"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            bill.setCreatedAt(created.toLocalDateTime());
        }
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            bill.setUpdatedAt(updated.toLocalDateTime());
        }
        try {
            bill.setManifestNumber(rs.getString("manifest_number"));
            bill.setPartyName(rs.getString("party_name"));
            bill.setBranchName(rs.getString("branch_name"));
            bill.setBranchCity(rs.getString("branch_city"));
        } catch (Exception ignored) {
            // optional joins
        }
        return bill;
    };

    public List<ManifestBill> search(String billType, Long partyId, Long branchId, String status,
                                     LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder(SELECT + " WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (billType != null && !billType.isBlank()) {
            sql.append(" AND b.bill_type = ?");
            params.add(billType);
        }
        if (partyId != null) {
            sql.append(" AND b.party_id = ?");
            params.add(partyId);
        }
        if (branchId != null) {
            sql.append(" AND b.branch_id = ?");
            params.add(branchId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND b.status = ?");
            params.add(status);
        }
        if (from != null) {
            sql.append(" AND DATE(b.created_at) >= ?");
            params.add(Date.valueOf(from));
        }
        if (to != null) {
            sql.append(" AND DATE(b.created_at) <= ?");
            params.add(Date.valueOf(to));
        }
        sql.append(" ORDER BY b.created_at DESC, b.id DESC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public List<ManifestBill> findByManifestId(Long manifestId) {
        return jdbcTemplate.query(SELECT + " WHERE b.manifest_id = ? ORDER BY b.bill_type, b.id",
                ROW_MAPPER, manifestId);
    }

    public boolean existsForManifest(Long manifestId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manifest_bills WHERE manifest_id = ?", Integer.class, manifestId);
        return count != null && count > 0;
    }

    public Optional<ManifestBill> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    SELECT + " WHERE b.id = ?", ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Long save(ManifestBill bill) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO manifest_bills (bill_number, bill_type, manifest_id, party_id, branch_id, " +
                            "weight_kg, number_of_boxes, per_kg_rate, per_box_rate, freight_amount, status, notes) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, bill.getBillNumber());
            ps.setString(2, bill.getBillType());
            ps.setLong(3, bill.getManifestId());
            if (bill.getPartyId() != null) {
                ps.setLong(4, bill.getPartyId());
            } else {
                ps.setNull(4, Types.BIGINT);
            }
            if (bill.getBranchId() != null) {
                ps.setLong(5, bill.getBranchId());
            } else {
                ps.setNull(5, Types.BIGINT);
            }
            ps.setBigDecimal(6, bill.getWeightKg() != null ? bill.getWeightKg() : BigDecimal.ZERO);
            ps.setInt(7, bill.getNumberOfBoxes());
            ps.setBigDecimal(8, bill.getPerKgRate() != null ? bill.getPerKgRate() : BigDecimal.ZERO);
            ps.setBigDecimal(9, bill.getPerBoxRate() != null ? bill.getPerBoxRate() : BigDecimal.ZERO);
            ps.setBigDecimal(10, bill.getFreightAmount() != null ? bill.getFreightAmount() : BigDecimal.ZERO);
            ps.setString(11, bill.getStatus() != null ? bill.getStatus() : ManifestBill.STATUS_PENDING);
            ps.setString(12, bill.getNotes());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public void updateStatus(Long id, String status) {
        jdbcTemplate.update("UPDATE manifest_bills SET status = ? WHERE id = ?", status, id);
    }

    public void updateAmounts(ManifestBill bill) {
        jdbcTemplate.update(
                "UPDATE manifest_bills SET weight_kg = ?, number_of_boxes = ?, per_kg_rate = ?, " +
                        "per_box_rate = ?, freight_amount = ?, notes = ? WHERE id = ?",
                bill.getWeightKg() != null ? bill.getWeightKg() : BigDecimal.ZERO,
                bill.getNumberOfBoxes(),
                bill.getPerKgRate() != null ? bill.getPerKgRate() : BigDecimal.ZERO,
                bill.getPerBoxRate() != null ? bill.getPerBoxRate() : BigDecimal.ZERO,
                bill.getFreightAmount() != null ? bill.getFreightAmount() : BigDecimal.ZERO,
                bill.getNotes(),
                bill.getId());
    }

    public Optional<ManifestBill> findByManifestAndType(Long manifestId, String billType) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    SELECT + " WHERE b.manifest_id = ? AND b.bill_type = ? ORDER BY b.id LIMIT 1",
                    ROW_MAPPER, manifestId, billType));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<ManifestBill> findExisting(Long manifestId, String billType, Long partyId, Long branchId) {
        StringBuilder sql = new StringBuilder(SELECT + " WHERE b.manifest_id = ? AND b.bill_type = ?");
        List<Object> params = new ArrayList<>();
        params.add(manifestId);
        params.add(billType);
        if (partyId != null) {
            sql.append(" AND b.party_id = ?");
            params.add(partyId);
        } else {
            sql.append(" AND b.party_id IS NULL");
        }
        if (branchId != null) {
            sql.append(" AND b.branch_id = ?");
            params.add(branchId);
        } else {
            sql.append(" AND b.branch_id IS NULL");
        }
        sql.append(" LIMIT 1");
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql.toString(), ROW_MAPPER, params.toArray()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public long countByTypeAndStatus(String billType, String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manifest_bills WHERE bill_type = ? AND status = ?",
                Long.class, billType, status);
        return count != null ? count : 0L;
    }

    public BigDecimal sumByTypeAndStatus(String billType, String status) {
        BigDecimal sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(freight_amount), 0) FROM manifest_bills WHERE bill_type = ? AND status = ?",
                BigDecimal.class, billType, status);
        return sum != null ? sum : BigDecimal.ZERO;
    }

    public long countByTypeAndStatusBetween(String billType, String status, LocalDate from, LocalDate to) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manifest_bills WHERE bill_type = ? AND status = ? " +
                        "AND DATE(created_at) >= ? AND DATE(created_at) <= ?",
                Long.class, billType, status, Date.valueOf(from), Date.valueOf(to));
        return count != null ? count : 0L;
    }

    public BigDecimal sumByTypeAndStatusBetween(String billType, String status, LocalDate from, LocalDate to) {
        BigDecimal sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(freight_amount), 0) FROM manifest_bills WHERE bill_type = ? AND status = ? " +
                        "AND DATE(created_at) >= ? AND DATE(created_at) <= ?",
                BigDecimal.class, billType, status, Date.valueOf(from), Date.valueOf(to));
        return sum != null ? sum : BigDecimal.ZERO;
    }

    public int countToday(String prefix) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manifest_bills WHERE bill_number LIKE ?",
                Integer.class, prefix + "%");
        return count != null ? count : 0;
    }
}
