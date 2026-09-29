package com.mahavircourier.dao;

import com.mahavircourier.model.BranchLaneRate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class BranchLaneRateDao {

    private final JdbcTemplate jdbcTemplate;

    public BranchLaneRateDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<BranchLaneRate> ROW_MAPPER = (rs, rowNum) -> {
        BranchLaneRate rate = new BranchLaneRate();
        rate.setId(rs.getLong("id"));
        rate.setOriginBranchId(rs.getLong("origin_branch_id"));
        rate.setDestBranchId(rs.getLong("dest_branch_id"));
        rate.setPerKgRate(rs.getBigDecimal("per_kg_rate"));
        return rate;
    };

    public List<BranchLaneRate> findByOrigin(Long originBranchId) {
        return jdbcTemplate.query(
                "SELECT * FROM branch_lane_rates WHERE origin_branch_id = ? ORDER BY dest_branch_id",
                ROW_MAPPER, originBranchId);
    }

    public Optional<BranchLaneRate> findByOriginAndDest(Long originBranchId, Long destBranchId) {
        if (originBranchId == null || destBranchId == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM branch_lane_rates WHERE origin_branch_id = ? AND dest_branch_id = ?",
                    ROW_MAPPER, originBranchId, destBranchId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<BranchLaneRate> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM branch_lane_rates WHERE id = ?", ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public void upsert(BranchLaneRate rate) {
        jdbcTemplate.update(
                "INSERT INTO branch_lane_rates (origin_branch_id, dest_branch_id, per_kg_rate) " +
                        "VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE per_kg_rate = VALUES(per_kg_rate)",
                rate.getOriginBranchId(),
                rate.getDestBranchId(),
                rate.getPerKgRate());
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM branch_lane_rates WHERE id = ?", id);
    }

    public Map<Long, Integer> countByOrigin() {
        return jdbcTemplate.query(
                "SELECT origin_branch_id, COUNT(*) AS n FROM branch_lane_rates GROUP BY origin_branch_id",
                rs -> {
                    Map<Long, Integer> counts = new HashMap<>();
                    while (rs.next()) {
                        counts.put(rs.getLong("origin_branch_id"), rs.getInt("n"));
                    }
                    return counts;
                });
    }
}
