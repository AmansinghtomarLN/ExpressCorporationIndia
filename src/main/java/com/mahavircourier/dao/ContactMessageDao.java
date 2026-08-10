package com.mahavircourier.dao;

import com.mahavircourier.dto.ContactForm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContactMessageDao {

    private final JdbcTemplate jdbcTemplate;

    public ContactMessageDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(ContactForm form) {
        jdbcTemplate.update(
                "INSERT INTO contact_messages (name, email, phone, subject, message) VALUES (?, ?, ?, ?, ?)",
                form.getName(), form.getEmail(), form.getPhone(), form.getSubject(), form.getMessage());
    }
}
