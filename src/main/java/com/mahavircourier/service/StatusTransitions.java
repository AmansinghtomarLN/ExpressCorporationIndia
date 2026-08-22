package com.mahavircourier.service;

import java.util.List;
import java.util.Set;

/**
 * Known shipment statuses for admin tracking updates.
 * Any status may be selected when updating a shipment.
 */
public final class StatusTransitions {

    public static final List<String> ALL_STATUSES = List.of(
            "BOOKED", "DISPATCHED", "PICKED_UP", "IN_TRANSIT", "AT_HUB",
            "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED", "RTO"
    );

    private static final Set<String> STATUS_SET = Set.copyOf(ALL_STATUSES);

    private StatusTransitions() {
    }

    /** All statuses available for the next-status dropdown. */
    public static List<String> allowedNext(String from) {
        return ALL_STATUSES;
    }

    public static void validate(String from, String to) {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }
        String normalizedTo = to.trim().toUpperCase();
        if (!STATUS_SET.contains(normalizedTo)) {
            throw new IllegalArgumentException("Unknown status: " + to);
        }
    }
}
