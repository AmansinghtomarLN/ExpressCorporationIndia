package com.mahavircourier.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Allowed shipment status transitions for admin tracking updates.
 */
public final class StatusTransitions {

    public static final List<String> ALL_STATUSES = List.of(
            "BOOKED", "PICKED_UP", "IN_TRANSIT", "AT_HUB",
            "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED", "RTO"
    );

    private static final Set<String> STATUS_SET = Set.copyOf(ALL_STATUSES);

    private static final Map<String, List<String>> ALLOWED = new LinkedHashMap<>();

    static {
        ALLOWED.put("BOOKED", List.of("PICKED_UP", "CANCELLED"));
        ALLOWED.put("PICKED_UP", List.of("IN_TRANSIT", "AT_HUB", "CANCELLED"));
        ALLOWED.put("IN_TRANSIT", List.of("AT_HUB", "OUT_FOR_DELIVERY", "RTO"));
        ALLOWED.put("AT_HUB", List.of("IN_TRANSIT", "OUT_FOR_DELIVERY", "RTO"));
        ALLOWED.put("OUT_FOR_DELIVERY", List.of("DELIVERED", "RTO", "AT_HUB"));
        ALLOWED.put("DELIVERED", List.of());
        ALLOWED.put("CANCELLED", List.of());
        ALLOWED.put("RTO", List.of("DELIVERED", "AT_HUB"));
    }

    private StatusTransitions() {
    }

    public static List<String> allowedNext(String from) {
        if (from == null || from.isBlank()) {
            return List.of();
        }
        return ALLOWED.getOrDefault(from.trim().toUpperCase(), Collections.emptyList());
    }

    public static void validate(String from, String to) {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }
        String normalizedTo = to.trim().toUpperCase();
        if (!STATUS_SET.contains(normalizedTo)) {
            throw new IllegalArgumentException("Unknown status: " + to);
        }
        String normalizedFrom = from == null ? "" : from.trim().toUpperCase();
        if (normalizedFrom.equals(normalizedTo)) {
            return;
        }
        List<String> next = allowedNext(normalizedFrom);
        if (!next.contains(normalizedTo)) {
            throw new IllegalArgumentException(
                    "Invalid status transition from " + normalizedFrom + " to " + normalizedTo);
        }
    }
}
