package com.mahavircourier.dao;

import com.mahavircourier.model.PartyCityRate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class PartyCityRateDao {

    private final JdbcTemplate jdbcTemplate;

    public PartyCityRateDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<PartyCityRate> ROW_MAPPER = (rs, rowNum) -> {
        PartyCityRate rate = new PartyCityRate();
        rate.setId(rs.getLong("id"));
        rate.setPartyId(rs.getLong("party_id"));
        rate.setCityName(rs.getString("city_name"));
        rate.setPerKgRate(rs.getBigDecimal("per_kg_rate"));
        rate.setMinWeightKg(rs.getBigDecimal("min_weight_kg"));
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            rate.setUpdatedAt(updated.toLocalDateTime());
        }
        try {
            rate.setPartyName(rs.getString("party_name"));
        } catch (Exception ignored) {
            // optional join
        }
        return rate;
    };

    public List<PartyCityRate> findByPartyId(Long partyId) {
        return jdbcTemplate.query(
                "SELECT r.*, p.party_name FROM party_city_rates r " +
                        "JOIN parties p ON p.id = r.party_id " +
                        "WHERE r.party_id = ? ORDER BY r.city_name ASC",
                ROW_MAPPER, partyId);
    }

    public Optional<PartyCityRate> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT r.*, p.party_name FROM party_city_rates r " +
                            "JOIN parties p ON p.id = r.party_id WHERE r.id = ?",
                    ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<PartyCityRate> findByPartyAndCity(Long partyId, String cityName) {
        if (partyId == null || cityName == null || cityName.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT r.*, p.party_name FROM party_city_rates r " +
                            "JOIN parties p ON p.id = r.party_id " +
                            "WHERE r.party_id = ? AND LOWER(r.city_name) = LOWER(?) LIMIT 1",
                    ROW_MAPPER, partyId, cityName.trim()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public void upsert(PartyCityRate rate) {
        jdbcTemplate.update(
                "INSERT INTO party_city_rates (party_id, city_name, per_kg_rate, min_weight_kg) " +
                        "VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE per_kg_rate = VALUES(per_kg_rate), " +
                        "min_weight_kg = VALUES(min_weight_kg)",
                rate.getPartyId(),
                rate.getCityName(),
                rate.getPerKgRate(),
                rate.getMinWeightKg());
    }

    /** Sets the per-kg rate and leaves an existing city minimum unchanged. */
    public void upsertPerKg(PartyCityRate rate) {
        jdbcTemplate.update(
                "INSERT INTO party_city_rates (party_id, city_name, per_kg_rate, min_weight_kg) " +
                        "VALUES (?, ?, ?, NULL) " +
                        "ON DUPLICATE KEY UPDATE per_kg_rate = VALUES(per_kg_rate)",
                rate.getPartyId(),
                rate.getCityName(),
                rate.getPerKgRate());
    }

    public java.util.Map<Long, Integer> countByParty() {
        return jdbcTemplate.query("SELECT party_id, COUNT(*) AS n FROM party_city_rates GROUP BY party_id",
                rs -> {
                    java.util.Map<Long, Integer> counts = new java.util.HashMap<>();
                    while (rs.next()) {
                        counts.put(rs.getLong("party_id"), rs.getInt("n"));
                    }
                    return counts;
                });
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM party_city_rates WHERE id = ?", id);
    }

    public void clearMinimums(Long partyId) {
        jdbcTemplate.update(
                "UPDATE party_city_rates SET min_weight_kg = NULL WHERE party_id = ?", partyId);
    }
}
