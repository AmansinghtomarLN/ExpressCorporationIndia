package com.mahavircourier.dao;

import com.mahavircourier.model.User;
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
public class UserDao {

    private final JdbcTemplate jdbcTemplate;

    public UserDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<User> USER_ROW_MAPPER = (rs, rowNum) -> {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setPhone(rs.getString("phone"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(rs.getString("role"));
        u.setEnabled(rs.getBoolean("enabled"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            u.setCreatedAt(ts.toLocalDateTime());
        }
        return u;
    };

    public Optional<User> findByEmail(String email) {
        try {
            User user = jdbcTemplate.queryForObject(
                    "SELECT * FROM users WHERE email = ?",
                    USER_ROW_MAPPER, email);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<User> findById(Long id) {
        try {
            User user = jdbcTemplate.queryForObject(
                    "SELECT * FROM users WHERE id = ?",
                    USER_ROW_MAPPER, id);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsByEmail(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        return count != null && count > 0;
    }

    public List<User> findAll() {
        return jdbcTemplate.query("SELECT * FROM users ORDER BY created_at DESC", USER_ROW_MAPPER);
    }

    public Long save(User user) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO users (full_name, email, phone, password_hash, role, enabled) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setString(4, user.getPasswordHash());
            ps.setString(5, user.getRole());
            ps.setBoolean(6, user.isEnabled());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }
}
