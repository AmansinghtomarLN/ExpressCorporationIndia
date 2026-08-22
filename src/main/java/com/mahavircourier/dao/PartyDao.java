package com.mahavircourier.dao;

import com.mahavircourier.model.Party;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class PartyDao {

    private final JdbcTemplate jdbcTemplate;

    public PartyDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Party> ROW_MAPPER = (rs, rowNum) -> {
        Party p = new Party();
        p.setId(rs.getLong("id"));
        p.setPartyName(rs.getString("party_name"));
        p.setContactPerson(rs.getString("contact_person"));
        p.setPhone(rs.getString("phone"));
        p.setEmail(rs.getString("email"));
        p.setGstin(rs.getString("gstin"));
        p.setAddress(rs.getString("address"));
        p.setCity(rs.getString("city"));
        p.setState(rs.getString("state"));
        p.setPincode(rs.getString("pincode"));
        p.setNotes(rs.getString("notes"));
        p.setEnabled(rs.getBoolean("enabled"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            p.setCreatedAt(created.toLocalDateTime());
        }
        try {
            p.setRangeCount(rs.getInt("range_count"));
        } catch (Exception ignored) {
            // column not selected
        }
        try {
            p.setManifestCount(rs.getInt("manifest_count"));
        } catch (Exception ignored) {
            // column not selected
        }
        return p;
    };

    public List<Party> search(String query) {
        StringBuilder sql = new StringBuilder(
                "SELECT p.*, " +
                        "(SELECT COUNT(*) FROM consignment_ranges r WHERE r.party_id = p.id) AS range_count, " +
                        "(SELECT COUNT(*) FROM manifests m WHERE m.party_id = p.id) AS manifest_count " +
                        "FROM parties p WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim() + "%";
            sql.append(" AND (p.party_name LIKE ? OR p.contact_person LIKE ? OR p.phone LIKE ?")
                    .append(" OR p.city LIKE ? OR p.gstin LIKE ? OR p.email LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY p.party_name ASC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public List<Party> findEnabled() {
        return jdbcTemplate.query(
                "SELECT * FROM parties WHERE enabled = 1 ORDER BY party_name ASC", ROW_MAPPER);
    }

    public Optional<Party> findById(Long id) {
        try {
            Party party = jdbcTemplate.queryForObject(
                    "SELECT p.*, " +
                            "(SELECT COUNT(*) FROM consignment_ranges r WHERE r.party_id = p.id) AS range_count, " +
                            "(SELECT COUNT(*) FROM manifests m WHERE m.party_id = p.id) AS manifest_count " +
                            "FROM parties p WHERE p.id = ?",
                    ROW_MAPPER, id);
            return Optional.ofNullable(party);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Long save(Party party) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO parties (party_name, contact_person, phone, email, gstin, address, " +
                            "city, state, pincode, notes, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, party.getPartyName());
            ps.setString(2, party.getContactPerson());
            ps.setString(3, party.getPhone());
            ps.setString(4, party.getEmail());
            ps.setString(5, party.getGstin());
            ps.setString(6, party.getAddress());
            ps.setString(7, party.getCity());
            ps.setString(8, party.getState());
            ps.setString(9, party.getPincode());
            ps.setString(10, party.getNotes());
            ps.setBoolean(11, party.isEnabled());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public void update(Party party) {
        jdbcTemplate.update(
                "UPDATE parties SET party_name = ?, contact_person = ?, phone = ?, email = ?, gstin = ?, " +
                        "address = ?, city = ?, state = ?, pincode = ?, notes = ?, enabled = ? WHERE id = ?",
                party.getPartyName(),
                party.getContactPerson(),
                party.getPhone(),
                party.getEmail(),
                party.getGstin(),
                party.getAddress(),
                party.getCity(),
                party.getState(),
                party.getPincode(),
                party.getNotes(),
                party.isEnabled(),
                party.getId());
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM parties WHERE id = ?", id);
    }
}
