package com.mahavircourier.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

public final class DashboardPeriod {

    public static final String TODAY = "TODAY";
    public static final String WEEK = "WEEK";
    public static final String MONTH = "MONTH";
    public static final String QUARTER = "QUARTER";
    public static final String HALFYEAR = "HALFYEAR";
    public static final String ANNUAL = "ANNUAL";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public record Range(String key, String label, LocalDate from, LocalDate to) {
    }

    private DashboardPeriod() {
    }

    public static Range resolve(String period) {
        LocalDate today = LocalDate.now();
        String key = period == null || period.isBlank() ? TODAY : period.trim().toUpperCase();
        return switch (key) {
            case WEEK -> {
                LocalDate from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate to = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                yield new Range(WEEK, "This week · " + from.format(DAY) + " – " + to.format(DAY), from, to);
            }
            case MONTH -> {
                LocalDate from = today.with(TemporalAdjusters.firstDayOfMonth());
                LocalDate to = today.with(TemporalAdjusters.lastDayOfMonth());
                yield new Range(MONTH, "This month · " + from.format(DAY) + " – " + to.format(DAY), from, to);
            }
            case QUARTER -> {
                int firstMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate from = LocalDate.of(today.getYear(), firstMonth, 1);
                LocalDate to = from.plusMonths(3).minusDays(1);
                yield new Range(QUARTER, "This quarter · " + from.format(DAY) + " – " + to.format(DAY), from, to);
            }
            case HALFYEAR -> {
                LocalDate from = today.getMonthValue() <= 6
                        ? LocalDate.of(today.getYear(), 1, 1)
                        : LocalDate.of(today.getYear(), 7, 1);
                LocalDate to = from.plusMonths(6).minusDays(1);
                yield new Range(HALFYEAR, "This half-year · " + from.format(DAY) + " – " + to.format(DAY), from, to);
            }
            case ANNUAL -> {
                LocalDate from = today.with(TemporalAdjusters.firstDayOfYear());
                LocalDate to = today.with(TemporalAdjusters.lastDayOfYear());
                yield new Range(ANNUAL, "This year · " + from.format(DAY) + " – " + to.format(DAY), from, to);
            }
            default -> new Range(TODAY, "Today · " + today.format(DAY), today, today);
        };
    }
}
