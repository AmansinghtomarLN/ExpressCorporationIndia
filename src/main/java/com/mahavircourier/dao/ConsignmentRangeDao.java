package com.mahavircourier.dao;

import com.mahavircourier.model.ConsignmentRange;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class ConsignmentRangeDao {

    private final JdbcTemplate jdbcTemplate;

    public ConsignmentRangeDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<ConsignmentRange> ROW_MAPPER = (rs, rowNum) -> {
        ConsignmentRange r = new ConsignmentRange();
        r.setId(rs.getLong("id"));
        r.setPartyId(rs.getLong("party_id"));
        r.setRangeStart(rs.getLong("range_start"));
        r.setRangeEnd(rs.getLong("range_end"));
        r.setNotes(rs.getString("notes"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            r.setCreatedAt(created.toLocalDateTime());
        }
        try {
            r.setPartyName(rs.getString("party_name"));
        } catch (Exception ignored) {
            // optional
        }
        return r;
    };

    public List<ConsignmentRange> findAllWithParty() {
        return jdbcTemplate.query(
                "SELECT r.*, p.party_name FROM consignment_ranges r " +
                        "JOIN parties p ON p.id = r.party_id ORDER BY r.range_start ASC",
                ROW_MAPPER);
    }

    public List<ConsignmentRange> findByPartyId(Long partyId) {
        return jdbcTemplate.query(
                "SELECT * FROM consignment_ranges WHERE party_id = ? ORDER BY range_start ASC",
                ROW_MAPPER, partyId);
    }

    public Optional<ConsignmentRange> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM consignment_ranges WHERE id = ?", ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean covers(Long partyId, long consignmentNumber) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consignment_ranges WHERE party_id = ? AND ? BETWEEN range_start AND range_end",
                Integer.class, partyId, consignmentNumber);
        return count != null && count > 0;
    }

    public boolean overlaps(long start, long end, Long excludeId) {
        Integer count;
        if (excludeId == null) {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM consignment_ranges WHERE range_start <= ? AND range_end >= ?",
                    Integer.class, end, start);
        } else {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM consignment_ranges WHERE range_start <= ? AND range_end >= ? AND id <> ?",
                    Integer.class, end, start, excludeId);
        }
        return count != null && count > 0;
    }

    public Long save(ConsignmentRange range) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO consignment_ranges (party_id, range_start, range_end, notes) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, range.getPartyId());
            ps.setLong(2, range.getRangeStart());
            ps.setLong(3, range.getRangeEnd());
            ps.setString(4, range.getNotes());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM consignment_ranges WHERE id = ?", id);
    }

    public long countUsedInRange(long start, long end) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM manifest_items WHERE consignment_no REGEXP '^[0-9]+$' " +
                        "AND CAST(consignment_no AS UNSIGNED) BETWEEN ? AND ?",
                Long.class, start, end);
        return count != null ? count : 0L;
    }
}
