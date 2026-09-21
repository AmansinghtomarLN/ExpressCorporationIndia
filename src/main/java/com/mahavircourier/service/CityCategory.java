package com.mahavircourier.service;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Destination city groups used on manifests and the Cities admin page.
 */
public final class CityCategory {

    public static final String LOCAL = "LOCAL";
    public static final String MP = "MP";
    public static final String CG = "CG";
    public static final String NEARBY = "NEARBY";
    public static final String REST = "REST";

    public static final List<String> ALL = List.of(LOCAL, MP, CG, NEARBY, REST);

    public static final List<String> STATES = List.of(
            "Andaman and Nicobar Islands", "Andhra Pradesh", "Arunachal Pradesh", "Assam",
            "Bihar", "Chandigarh", "Chhattisgarh", "Dadra and Nagar Haveli and Daman and Diu",
            "Delhi", "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir",
            "Jharkhand", "Karnataka", "Kerala", "Ladakh", "Lakshadweep", "Madhya Pradesh",
            "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha",
            "Puducherry", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana",
            "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal"
    );

    private static final Set<String> NEARBY_STATES = Set.of(
            "maharashtra", "gujarat", "gujrat", "rajasthan", "uttar pradesh", "up"
    );

    private CityCategory() {
    }

    public static String fromState(String state) {
        String normalized = normalizeKey(state);
        if (normalized.isEmpty()) {
            return REST;
        }
        if (normalized.equals("madhya pradesh") || normalized.equals("mp")) {
            return MP;
        }
        if (normalized.contains("chhatt") || normalized.contains("chattis") || normalized.equals("cg")) {
            return CG;
        }
        if (NEARBY_STATES.contains(normalized)) {
            return NEARBY;
        }
        return REST;
    }

    public static String normalize(String category) {
        if (!StringUtils.hasText(category)) {
            return REST;
        }
        String value = category.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if ("REST_OF_INDIA".equals(value) || "RESTOFINDIA".equals(value)) {
            return REST;
        }
        if ("NEARBY_STATES".equals(value) || "NEARBYSTATES".equals(value)) {
            return NEARBY;
        }
        if (ALL.contains(value)) {
            return value;
        }
        throw new IllegalArgumentException("Unknown city category: " + category);
    }

    public static String displayName(String category) {
        return switch (normalize(category)) {
            case LOCAL -> "Local";
            case MP -> "MP";
            case CG -> "CG";
            case NEARBY -> "Nearby States";
            default -> "Rest of India";
        };
    }

    public static String displayNameForDropdown(String category) {
        return switch (normalize(category)) {
            case LOCAL -> "Local";
            case MP -> "MP";
            case CG -> "CG";
            case NEARBY -> "Nearby States (MH / GJ / RJ / UP)";
            default -> "Rest of India";
        };
    }

    public static boolean sameCity(String left, String right) {
        return normalizeKey(left).equals(normalizeKey(right)) && !normalizeKey(left).isEmpty();
    }

    private static String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
