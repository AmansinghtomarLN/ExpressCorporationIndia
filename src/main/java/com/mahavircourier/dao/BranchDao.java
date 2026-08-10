package com.mahavircourier.dao;

import com.mahavircourier.model.Branch;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

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
                    "INSERT INTO branches (branch_name, city, state, pincode, phone, address) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, branch.getBranchName());
            ps.setString(2, branch.getCity());
            ps.setString(3, branch.getState());
            ps.setString(4, branch.getPincode());
            ps.setString(5, branch.getPhone());
            ps.setString(6, branch.getAddress());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public void update(Branch branch) {
        jdbcTemplate.update(
                "UPDATE branches SET branch_name = ?, city = ?, state = ?, pincode = ?, phone = ?, address = ? WHERE id = ?",
                branch.getBranchName(),
                branch.getCity(),
                branch.getState(),
                branch.getPincode(),
                branch.getPhone(),
                branch.getAddress(),
                branch.getId());
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM branches WHERE id = ?", id);
    }
}
