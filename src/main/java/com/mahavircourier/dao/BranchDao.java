package com.mahavircourier.dao;

import com.mahavircourier.model.Branch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
