package com.mahavircourier.dao;

import com.mahavircourier.dto.ContactForm;
import com.mahavircourier.model.ContactMessage;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class ContactMessageDao {

    private final JdbcTemplate jdbcTemplate;

    public ContactMessageDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<ContactMessage> ROW_MAPPER = (rs, rowNum) -> {
        ContactMessage m = new ContactMessage();
        m.setId(rs.getLong("id"));
        m.setName(rs.getString("name"));
        m.setEmail(rs.getString("email"));
        m.setPhone(rs.getString("phone"));
        m.setSubject(rs.getString("subject"));
        m.setMessage(rs.getString("message"));
        try {
            m.setStatus(rs.getString("status"));
        } catch (Exception e) {
            m.setStatus("UNREAD");
        }
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            m.setCreatedAt(ts.toLocalDateTime());
        }
        return m;
    };

    public void save(ContactForm form) {
        jdbcTemplate.update(
                "INSERT INTO contact_messages (name, email, phone, subject, message) VALUES (?, ?, ?, ?, ?)",
                form.getName(), form.getEmail(), form.getPhone(), form.getSubject(), form.getMessage());
    }

    public List<ContactMessage> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM contact_messages ORDER BY created_at DESC", ROW_MAPPER);
    }

    public Optional<ContactMessage> findById(Long id) {
        try {
            ContactMessage message = jdbcTemplate.queryForObject(
                    "SELECT * FROM contact_messages WHERE id = ?", ROW_MAPPER, id);
            return Optional.ofNullable(message);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void markStatus(Long id, String status) {
        jdbcTemplate.update("UPDATE contact_messages SET status = ? WHERE id = ?", status, id);
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM contact_messages WHERE id = ?", id);
    }

    public long countUnread() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contact_messages WHERE status = 'UNREAD'", Long.class);
        return count != null ? count : 0L;
    }

    public long countUnreadBetween(LocalDate from, LocalDate to) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contact_messages WHERE status = 'UNREAD' " +
                        "AND DATE(created_at) >= ? AND DATE(created_at) <= ?",
                Long.class, Date.valueOf(from), Date.valueOf(to));
        return count != null ? count : 0L;
    }
}
