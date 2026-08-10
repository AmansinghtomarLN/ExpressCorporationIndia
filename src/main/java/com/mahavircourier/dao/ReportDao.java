package com.mahavircourier.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ReportDao {

    private final JdbcTemplate jdbcTemplate;

    public ReportDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countBookedBetween(LocalDate from, LocalDate toInclusive) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipments WHERE DATE(created_at) BETWEEN ? AND ?",
                Long.class, Date.valueOf(from), Date.valueOf(toInclusive));
        return count != null ? count : 0L;
    }

    public long countByStatusBetween(String status, LocalDate from, LocalDate toInclusive) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipments WHERE status = ? AND DATE(updated_at) BETWEEN ? AND ?",
                Long.class, status, Date.valueOf(from), Date.valueOf(toInclusive));
        return count != null ? count : 0L;
    }

    public long countDeliveredBetween(LocalDate from, LocalDate toInclusive) {
        return countByStatusBetween("DELIVERED", from, toInclusive);
    }

    public long countCancelledBetween(LocalDate from, LocalDate toInclusive) {
        return countByStatusBetween("CANCELLED", from, toInclusive) + countByStatusBetween("RTO", from, toInclusive);
    }

    public long countDelayedAsOf(LocalDate asOf) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipments WHERE expected_delivery < ? " +
                        "AND status NOT IN ('DELIVERED','CANCELLED','RTO')",
                Long.class, Date.valueOf(asOf));
        return count != null ? count : 0L;
    }

    public Map<String, Long> statusBreakdownCreatedBetween(LocalDate from, LocalDate toInclusive) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status, COUNT(*) AS cnt FROM shipments " +
                        "WHERE DATE(created_at) BETWEEN ? AND ? GROUP BY status ORDER BY status",
                Date.valueOf(from), Date.valueOf(toInclusive));
        Map<String, Long> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            map.put(String.valueOf(row.get("status")), ((Number) row.get("cnt")).longValue());
        }
        return map;
    }

    public Map<String, Long> serviceBreakdownCreatedBetween(LocalDate from, LocalDate toInclusive) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT service_type, COUNT(*) AS cnt FROM shipments " +
                        "WHERE DATE(created_at) BETWEEN ? AND ? GROUP BY service_type ORDER BY service_type",
                Date.valueOf(from), Date.valueOf(toInclusive));
        Map<String, Long> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            map.put(String.valueOf(row.get("service_type")), ((Number) row.get("cnt")).longValue());
        }
        return map;
    }

    public Map<String, Long> originCityBreakdown(LocalDate from, LocalDate toInclusive) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT origin_city, COUNT(*) AS cnt FROM shipments " +
                        "WHERE DATE(created_at) BETWEEN ? AND ? GROUP BY origin_city ORDER BY cnt DESC LIMIT 15",
                Date.valueOf(from), Date.valueOf(toInclusive));
        Map<String, Long> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            map.put(String.valueOf(row.get("origin_city")), ((Number) row.get("cnt")).longValue());
        }
        return map;
    }

    public BigDecimal sumFreightBookedBetween(LocalDate from, LocalDate toInclusive) {
        BigDecimal sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(freight_charge),0) FROM shipments WHERE DATE(created_at) BETWEEN ? AND ?",
                BigDecimal.class, Date.valueOf(from), Date.valueOf(toInclusive));
        return sum != null ? sum : BigDecimal.ZERO;
    }

    public BigDecimal sumCodBookedBetween(LocalDate from, LocalDate toInclusive) {
        BigDecimal sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(cod_amount),0) FROM shipments WHERE DATE(created_at) BETWEEN ? AND ?",
                BigDecimal.class, Date.valueOf(from), Date.valueOf(toInclusive));
        return sum != null ? sum : BigDecimal.ZERO;
    }

    public BigDecimal avgWeightBookedBetween(LocalDate from, LocalDate toInclusive) {
        BigDecimal avg = jdbcTemplate.queryForObject(
                "SELECT COALESCE(AVG(weight_kg),0) FROM shipments WHERE DATE(created_at) BETWEEN ? AND ?",
                BigDecimal.class, Date.valueOf(from), Date.valueOf(toInclusive));
        return avg != null ? avg : BigDecimal.ZERO;
    }

    public BigDecimal sumInvoiceFreightBetween(LocalDate from, LocalDate toInclusive) {
        BigDecimal sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(freight_amount),0) FROM invoices WHERE DATE(created_at) BETWEEN ? AND ?",
                BigDecimal.class, Date.valueOf(from), Date.valueOf(toInclusive));
        return sum != null ? sum : BigDecimal.ZERO;
    }

    public BigDecimal sumInvoiceCodBetween(LocalDate from, LocalDate toInclusive) {
        BigDecimal sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(cod_amount),0) FROM invoices WHERE DATE(created_at) BETWEEN ? AND ?",
                BigDecimal.class, Date.valueOf(from), Date.valueOf(toInclusive));
        return sum != null ? sum : BigDecimal.ZERO;
    }

    public BigDecimal sumInvoicePaidBetween(LocalDate from, LocalDate toInclusive) {
        BigDecimal sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_amount),0) FROM invoices " +
                        "WHERE status IN ('PAID','COD_COLLECTED') AND DATE(updated_at) BETWEEN ? AND ?",
                BigDecimal.class, Date.valueOf(from), Date.valueOf(toInclusive));
        return sum != null ? sum : BigDecimal.ZERO;
    }

    public BigDecimal sumInvoiceUnpaidOpen() {
        BigDecimal sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_amount),0) FROM invoices WHERE status = 'UNPAID'",
                BigDecimal.class);
        return sum != null ? sum : BigDecimal.ZERO;
    }

    public long countUnpaidInvoices() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE status = 'UNPAID'", Long.class);
        return count != null ? count : 0L;
    }

    public long countContactsBetween(LocalDate from, LocalDate toInclusive) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contact_messages WHERE DATE(created_at) BETWEEN ? AND ?",
                Long.class, Date.valueOf(from), Date.valueOf(toInclusive));
        return count != null ? count : 0L;
    }

    public long countUsersRegisteredBetween(LocalDate from, LocalDate toInclusive) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE DATE(created_at) BETWEEN ? AND ?",
                Long.class, Date.valueOf(from), Date.valueOf(toInclusive));
        return count != null ? count : 0L;
    }

    public long countNotificationsBetween(LocalDate from, LocalDate toInclusive) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE DATE(created_at) BETWEEN ? AND ?",
                Long.class, Date.valueOf(from), Date.valueOf(toInclusive));
        return count != null ? count : 0L;
    }

    public long countFailedNotificationsBetween(LocalDate from, LocalDate toInclusive) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE status = 'FAILED' AND DATE(created_at) BETWEEN ? AND ?",
                Long.class, Date.valueOf(from), Date.valueOf(toInclusive));
        return count != null ? count : 0L;
    }

    public List<Map<String, Object>> shipmentLinesBetween(LocalDate from, LocalDate toInclusive) {
        return jdbcTemplate.queryForList(
                "SELECT tracking_id, status, service_type, origin_city, destination_city, weight_kg, " +
                        "freight_charge, cod_amount, expected_delivery, created_at, updated_at, " +
                        "sender_name, receiver_name, courier_name, assigned_hub " +
                        "FROM shipments WHERE DATE(created_at) BETWEEN ? AND ? " +
                        "ORDER BY created_at DESC LIMIT 500",
                Date.valueOf(from), Date.valueOf(toInclusive));
    }
}
