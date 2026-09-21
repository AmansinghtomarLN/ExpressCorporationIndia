package com.mahavircourier.dao;

import com.mahavircourier.model.City;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class CityDao {

    private static final RowMapper<City> ROW_MAPPER = (rs, rowNum) -> {
        City city = new City();
        city.setId(rs.getLong("id"));
        city.setCityName(rs.getString("city_name"));
        city.setState(rs.getString("state"));
        city.setCategory(rs.getString("category"));
        return city;
    };

    private final JdbcTemplate jdbcTemplate;

    public CityDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<City> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM cities ORDER BY category ASC, state ASC, city_name ASC",
                ROW_MAPPER);
    }

    public List<City> search(String query, String state, String category) {
        StringBuilder sql = new StringBuilder("SELECT * FROM cities WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(query)) {
            String like = "%" + query.trim() + "%";
            sql.append(" AND (city_name LIKE ? OR state LIKE ?)");
            params.add(like);
            params.add(like);
        }
        if (StringUtils.hasText(state)) {
            sql.append(" AND state = ?");
            params.add(state.trim());
        }
        if (StringUtils.hasText(category)) {
            sql.append(" AND category = ?");
            params.add(category.trim());
        }
        sql.append(" ORDER BY category ASC, state ASC, city_name ASC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public Optional<City> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM cities WHERE id = ?", ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<City> findByNameIgnoreCase(String cityName) {
        if (!StringUtils.hasText(cityName)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM cities WHERE LOWER(city_name) = LOWER(?) LIMIT 1",
                    ROW_MAPPER, cityName.trim()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean exists(String cityName, String state, Long excludeId) {
        if (!StringUtils.hasText(cityName) || !StringUtils.hasText(state)) {
            return false;
        }
        Integer count;
        if (excludeId == null) {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM cities WHERE LOWER(city_name) = LOWER(?) AND LOWER(state) = LOWER(?)",
                    Integer.class, cityName.trim(), state.trim());
        } else {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM cities WHERE LOWER(city_name) = LOWER(?) AND LOWER(state) = LOWER(?) AND id <> ?",
                    Integer.class, cityName.trim(), state.trim(), excludeId);
        }
        return count != null && count > 0;
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cities", Long.class);
        return count != null ? count : 0L;
    }

    public Long save(City city) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO cities (city_name, state, category) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, city.getCityName());
            ps.setString(2, city.getState());
            ps.setString(3, city.getCategory());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public void update(City city) {
        jdbcTemplate.update(
                "UPDATE cities SET city_name = ?, state = ?, category = ? WHERE id = ?",
                city.getCityName(), city.getState(), city.getCategory(), city.getId());
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM cities WHERE id = ?", id);
    }
}
