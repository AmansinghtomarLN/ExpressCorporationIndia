package com.mahavircourier.dao;

import com.mahavircourier.model.Manifest;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ManifestDao {

    private static final String SELECT_WITH_PARTY =
            "SELECT m.*, p.party_name, p.phone AS party_phone, p.city AS party_city, " +
                    "p.address AS party_address, p.gstin AS party_gstin " +
                    "FROM manifests m JOIN parties p ON p.id = m.party_id";

    private final JdbcTemplate jdbcTemplate;

    public ManifestDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Manifest> ROW_MAPPER = (rs, rowNum) -> {
        Manifest m = new Manifest();
        m.setId(rs.getLong("id"));
        m.setManifestNumber(rs.getString("manifest_number"));
        m.setPartyId(rs.getLong("party_id"));
        Date date = rs.getDate("manifest_date");
        if (date != null) {
            m.setManifestDate(date.toLocalDate());
        }
        m.setThroughName(rs.getString("through_name"));
        m.setOriginCity(rs.getString("origin_city"));
        m.setServiceType(rs.getString("service_type"));
        m.setRemarks(rs.getString("remarks"));
        m.setTotalBoxes(rs.getInt("total_boxes"));
        BigDecimal weight = rs.getBigDecimal("total_weight");
        m.setTotalWeight(weight != null ? weight : BigDecimal.ZERO);
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            m.setCreatedAt(created.toLocalDateTime());
        }
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            m.setUpdatedAt(updated.toLocalDateTime());
        }
        try {
            m.setPartyName(rs.getString("party_name"));
            m.setPartyPhone(rs.getString("party_phone"));
            m.setPartyCity(rs.getString("party_city"));
            m.setPartyAddress(rs.getString("party_address"));
            m.setPartyGstin(rs.getString("party_gstin"));
        } catch (Exception ignored) {
            // optional join columns
        }
        return m;
    };

    public List<Manifest> search(String query, Long partyId, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder(SELECT_WITH_PARTY + " WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim() + "%";
            sql.append(" AND (m.manifest_number LIKE ? OR m.through_name LIKE ? OR p.party_name LIKE ?")
                    .append(" OR EXISTS (SELECT 1 FROM manifest_items i WHERE i.manifest_id = m.id AND i.consignment_no LIKE ?))");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (partyId != null) {
            sql.append(" AND m.party_id = ?");
            params.add(partyId);
        }
        if (from != null) {
            sql.append(" AND m.manifest_date >= ?");
            params.add(Date.valueOf(from));
        }
        if (to != null) {
            sql.append(" AND m.manifest_date <= ?");
            params.add(Date.valueOf(to));
        }
        sql.append(" ORDER BY m.manifest_date DESC, m.id DESC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public Optional<Manifest> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    SELECT_WITH_PARTY + " WHERE m.id = ?", ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Manifest> findByNumber(String manifestNumber) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    SELECT_WITH_PARTY + " WHERE m.manifest_number = ?", ROW_MAPPER, manifestNumber));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsByNumber(String manifestNumber) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manifests WHERE manifest_number = ?", Integer.class, manifestNumber);
        return count != null && count > 0;
    }

    public String nextManifestNumber() {
        Long max = jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(manifest_number AS UNSIGNED)) FROM manifests WHERE manifest_number REGEXP '^[0-9]+$'",
                Long.class);
        long next = (max != null ? max : 11000L) + 1;
        return String.valueOf(next);
    }

    public Long save(Manifest manifest) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO manifests (manifest_number, party_id, manifest_date, through_name, " +
                            "origin_city, service_type, remarks, total_boxes, total_weight) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, manifest.getManifestNumber());
            ps.setLong(2, manifest.getPartyId());
            ps.setDate(3, Date.valueOf(manifest.getManifestDate()));
            ps.setString(4, manifest.getThroughName());
            ps.setString(5, manifest.getOriginCity());
            ps.setString(6, manifest.getServiceType());
            ps.setString(7, manifest.getRemarks());
            ps.setInt(8, manifest.getTotalBoxes());
            ps.setBigDecimal(9, manifest.getTotalWeight() != null ? manifest.getTotalWeight() : BigDecimal.ZERO);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public void update(Manifest manifest) {
        jdbcTemplate.update(
                "UPDATE manifests SET party_id = ?, manifest_date = ?, through_name = ?, origin_city = ?, " +
                        "service_type = ?, remarks = ?, total_boxes = ?, total_weight = ? WHERE id = ?",
                manifest.getPartyId(),
                Date.valueOf(manifest.getManifestDate()),
                manifest.getThroughName(),
                manifest.getOriginCity(),
                manifest.getServiceType(),
                manifest.getRemarks(),
                manifest.getTotalBoxes(),
                manifest.getTotalWeight() != null ? manifest.getTotalWeight() : BigDecimal.ZERO,
                manifest.getId());
    }

    public void updateTotals(Long id, int boxes, BigDecimal weight) {
        jdbcTemplate.update(
                "UPDATE manifests SET total_boxes = ?, total_weight = ? WHERE id = ?",
                boxes, weight != null ? weight : BigDecimal.ZERO, id);
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM manifests WHERE id = ?", id);
    }

    public long countByPartyId(Long partyId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manifests WHERE party_id = ?", Long.class, partyId);
        return count != null ? count : 0L;
    }
}
