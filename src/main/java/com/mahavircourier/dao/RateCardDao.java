package com.mahavircourier.dao;

import com.mahavircourier.model.RateCard;
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
public class RateCardDao {

    private final JdbcTemplate jdbcTemplate;

    public RateCardDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<RateCard> ROW_MAPPER = (rs, rowNum) -> {
        RateCard r = new RateCard();
        r.setId(rs.getLong("id"));
        r.setServiceType(rs.getString("service_type"));
        r.setMinWeightKg(rs.getBigDecimal("min_weight_kg"));
        r.setMaxWeightKg(rs.getBigDecimal("max_weight_kg"));
        r.setBaseRate(rs.getBigDecimal("base_rate"));
        r.setPerKgRate(rs.getBigDecimal("per_kg_rate"));
        r.setActive(rs.getBoolean("active"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            r.setCreatedAt(ts.toLocalDateTime());
        }
        return r;
    };

    public List<RateCard> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM rate_cards ORDER BY service_type, min_weight_kg", ROW_MAPPER);
    }

    public Optional<RateCard> findById(Long id) {
        try {
            RateCard card = jdbcTemplate.queryForObject(
                    "SELECT * FROM rate_cards WHERE id = ?", ROW_MAPPER, id);
            return Optional.ofNullable(card);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<RateCard> findActiveFor(String serviceType, BigDecimal weight) {
        try {
            RateCard card = jdbcTemplate.queryForObject(
                    "SELECT * FROM rate_cards WHERE active = 1 AND service_type = ? " +
                            "AND min_weight_kg <= ? AND max_weight_kg >= ? " +
                            "ORDER BY min_weight_kg DESC LIMIT 1",
                    ROW_MAPPER, serviceType, weight, weight);
            return Optional.ofNullable(card);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Long save(RateCard card) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO rate_cards (service_type, min_weight_kg, max_weight_kg, base_rate, per_kg_rate, active) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, card.getServiceType());
            ps.setBigDecimal(2, card.getMinWeightKg());
            ps.setBigDecimal(3, card.getMaxWeightKg());
            ps.setBigDecimal(4, card.getBaseRate());
            ps.setBigDecimal(5, card.getPerKgRate());
            ps.setBoolean(6, card.isActive());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public void update(RateCard card) {
        jdbcTemplate.update(
                "UPDATE rate_cards SET service_type = ?, min_weight_kg = ?, max_weight_kg = ?, " +
                        "base_rate = ?, per_kg_rate = ?, active = ? WHERE id = ?",
                card.getServiceType(),
                card.getMinWeightKg(),
                card.getMaxWeightKg(),
                card.getBaseRate(),
                card.getPerKgRate(),
                card.isActive(),
                card.getId());
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM rate_cards WHERE id = ?", id);
    }
}
