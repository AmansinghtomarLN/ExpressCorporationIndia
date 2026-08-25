package com.mahavircourier.dao;

import com.mahavircourier.model.Branch;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class BranchDao {

    private final JdbcTemplate jdbcTemplate;

    public BranchDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Branch> BRANCH_ROW_MAPPER = (rs, rowNum) -> {
        Branch b = new Branch();
        b.setId(rs.getLong("id"));
        b.setBranchName(rs.getString("branch_name"));
        b.setCity(rs.getString("city"));
        b.setState(rs.getString("state"));
        b.setPincode(rs.getString("pincode"));
        b.setPhone(rs.getString("phone"));
        b.setAddress(rs.getString("address"));
        try {
            b.setBranchCategory(rs.getString("branch_category"));
        } catch (Exception ignored) {
            b.setBranchCategory("DOMESTIC");
        }
        try {
            b.setPerKgRate(rs.getBigDecimal("per_kg_rate"));
            b.setPerBoxRate(rs.getBigDecimal("per_box_rate"));
        } catch (Exception ignored) {
            b.setPerKgRate(BigDecimal.ZERO);
            b.setPerBoxRate(BigDecimal.ZERO);
        }
        return b;
    };

    public List<Branch> findAll() {
        return jdbcTemplate.query("SELECT * FROM branches ORDER BY city ASC", BRANCH_ROW_MAPPER);
    }

    public Optional<Branch> findById(Long id) {
        try {
            Branch branch = jdbcTemplate.queryForObject(
                    "SELECT * FROM branches WHERE id = ?", BRANCH_ROW_MAPPER, id);
            return Optional.ofNullable(branch);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Long save(Branch branch) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO branches (branch_name, city, state, pincode, phone, address, branch_category, " +
                            "per_kg_rate, per_box_rate) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, branch.getBranchName());
            ps.setString(2, branch.getCity());
            ps.setString(3, branch.getState());
            ps.setString(4, branch.getPincode());
            ps.setString(5, branch.getPhone());
            ps.setString(6, branch.getAddress());
            ps.setString(7, branch.getBranchCategory() != null ? branch.getBranchCategory() : "DOMESTIC");
            ps.setBigDecimal(8, branch.getPerKgRate() != null ? branch.getPerKgRate() : BigDecimal.ZERO);
            ps.setBigDecimal(9, branch.getPerBoxRate() != null ? branch.getPerBoxRate() : BigDecimal.ZERO);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public void update(Branch branch) {
        jdbcTemplate.update(
                "UPDATE branches SET branch_name = ?, city = ?, state = ?, pincode = ?, phone = ?, address = ?, " +
                        "branch_category = ?, per_kg_rate = ?, per_box_rate = ? WHERE id = ?",
                branch.getBranchName(),
                branch.getCity(),
                branch.getState(),
                branch.getPincode(),
                branch.getPhone(),
                branch.getAddress(),
                branch.getBranchCategory() != null ? branch.getBranchCategory() : "DOMESTIC",
                branch.getPerKgRate() != null ? branch.getPerKgRate() : BigDecimal.ZERO,
                branch.getPerBoxRate() != null ? branch.getPerBoxRate() : BigDecimal.ZERO,
                branch.getId());
    }

    public void applyRatesByCategory(String category, BigDecimal perKgRate, BigDecimal perBoxRate) {
        jdbcTemplate.update(
                "UPDATE branches SET per_kg_rate = ?, per_box_rate = ? WHERE branch_category = ?",
                perKgRate != null ? perKgRate : BigDecimal.ZERO,
                perBoxRate != null ? perBoxRate : BigDecimal.ZERO,
                category);
    }

    public void updateRates(Long id, BigDecimal perKgRate, BigDecimal perBoxRate) {
        jdbcTemplate.update(
                "UPDATE branches SET per_kg_rate = ?, per_box_rate = ? WHERE id = ?",
                perKgRate != null ? perKgRate : BigDecimal.ZERO,
                perBoxRate != null ? perBoxRate : BigDecimal.ZERO,
                id);
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM branches WHERE id = ?", id);
    }

    public Optional<Branch> findByCityIgnoreCase(String city) {
        if (city == null || city.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM branches WHERE LOWER(city) = LOWER(?) LIMIT 1",
                    BRANCH_ROW_MAPPER, city.trim()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
