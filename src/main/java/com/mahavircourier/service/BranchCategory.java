package com.mahavircourier.service;

import org.springframework.util.StringUtils;

/**
 * MP branches are Domestic. Chhattisgarh branches are National.
 */
public final class BranchCategory {

    public static final String DOMESTIC = "DOMESTIC";
    public static final String NATIONAL = "NATIONAL";
    public static final String AUTO = "AUTO";

    private BranchCategory() {
    }

    public static String fromState(String state) {
        if (!StringUtils.hasText(state)) {
            return DOMESTIC;
        }
        String normalized = state.trim().toLowerCase();
        if (normalized.contains("chhatt") || normalized.contains("chattis") || normalized.equals("cg")) {
            return NATIONAL;
        }
        return DOMESTIC;
    }

    public static String normalizeLane(String lane) {
        if (!StringUtils.hasText(lane)) {
            return AUTO;
        }
        String value = lane.trim().toUpperCase();
        if (DOMESTIC.equals(value) || NATIONAL.equals(value) || AUTO.equals(value)) {
            return value;
        }
        throw new IllegalArgumentException("Unknown billing lane: " + lane);
    }

    public static String displayName(String lane) {
        if (NATIONAL.equalsIgnoreCase(lane)) {
            return "National (Chhattisgarh)";
        }
        if (AUTO.equalsIgnoreCase(lane)) {
            return "Auto (from destination branch)";
        }
        return "Domestic (Madhya Pradesh)";
    }
}
