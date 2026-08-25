package com.mahavircourier.dao;

import com.mahavircourier.model.CnoSettings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class CnoSettingsDao {

    private final JdbcTemplate jdbcTemplate;

    public CnoSettingsDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<CnoSettings> ROW_MAPPER = (rs, rowNum) -> {
        CnoSettings s = new CnoSettings();
        s.setId(rs.getLong("id"));
        s.setSeriesStart(rs.getLong("series_start"));
        s.setSeriesEnd(rs.getLong("series_end"));
        s.setBucketSize(rs.getInt("bucket_size"));
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            s.setUpdatedAt(updated.toLocalDateTime());
        }
        return s;
    };

    public CnoSettings load() {
        List<CnoSettings> rows = jdbcTemplate.query("SELECT * FROM cno_settings WHERE id = 1", ROW_MAPPER);
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        CnoSettings defaults = new CnoSettings();
        save(defaults);
        return defaults;
    }

    public void save(CnoSettings settings) {
        jdbcTemplate.update(
                "INSERT INTO cno_settings (id, series_start, series_end, bucket_size) VALUES (1, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE series_start = VALUES(series_start), " +
                        "series_end = VALUES(series_end), bucket_size = VALUES(bucket_size)",
                settings.getSeriesStart(),
                settings.getSeriesEnd(),
                settings.getBucketSize());
    }
}
