package com.mahavircourier.dao;

import com.mahavircourier.model.BillingTariff;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class BillingTariffDao {

    private final JdbcTemplate jdbcTemplate;

    public BillingTariffDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<BillingTariff> ROW_MAPPER = (rs, rowNum) -> {
        BillingTariff t = new BillingTariff();
        t.setId(rs.getLong("id"));
        t.setLaneType(rs.getString("lane_type"));
        t.setMinCharge(rs.getBigDecimal("min_charge"));
        t.setBaseRate(rs.getBigDecimal("base_rate"));
        t.setPerKgRate(rs.getBigDecimal("per_kg_rate"));
        t.setPerBoxRate(rs.getBigDecimal("per_box_rate"));
        t.setActive(rs.getBoolean("active"));
        t.setNotes(rs.getString("notes"));
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            t.setUpdatedAt(updated.toLocalDateTime());
        }
        return t;
    };

    public List<BillingTariff> findAll() {
        return jdbcTemplate.query("SELECT * FROM billing_tariffs ORDER BY lane_type", ROW_MAPPER);
    }

    public Optional<BillingTariff> findByLane(String laneType) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM billing_tariffs WHERE lane_type = ?", ROW_MAPPER, laneType));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void upsert(BillingTariff tariff) {
        Optional<BillingTariff> existing = findByLane(tariff.getLaneType());
        if (existing.isPresent()) {
            jdbcTemplate.update(
                    "UPDATE billing_tariffs SET min_charge = ?, base_rate = ?, per_kg_rate = ?, " +
                            "per_box_rate = ?, active = ?, notes = ? WHERE lane_type = ?",
                    tariff.getMinCharge(),
                    tariff.getBaseRate(),
                    tariff.getPerKgRate(),
                    tariff.getPerBoxRate(),
                    tariff.isActive(),
                    tariff.getNotes(),
                    tariff.getLaneType());
        } else {
            jdbcTemplate.update(
                    "INSERT INTO billing_tariffs (lane_type, min_charge, base_rate, per_kg_rate, per_box_rate, active, notes) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    tariff.getLaneType(),
                    tariff.getMinCharge(),
                    tariff.getBaseRate(),
                    tariff.getPerKgRate(),
                    tariff.getPerBoxRate(),
                    tariff.isActive(),
                    tariff.getNotes());
        }
    }
}
