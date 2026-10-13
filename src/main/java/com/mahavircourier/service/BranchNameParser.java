package com.mahavircourier.service;

import com.mahavircourier.model.City;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BranchName in the branch Excel holds the branch name, the city, and the address together.
 * Accepted shapes:
 * three lines (branch, city, address);
 * "Branch, City, Address";
 * "City - address", for example "INDORE - 12, Palasia Square".
 */
public final class BranchNameParser {

    private static final Pattern PINCODE = Pattern.compile("\\b(\\d{6})\\b");

    private BranchNameParser() {
    }

    public static Parsed parse(String raw, List<City> cities) {
        String text = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (text.isEmpty()) {
            return new Parsed("", "", "", "", "");
        }
        String pincode = "";
        Matcher pin = PINCODE.matcher(text);
        if (pin.find()) {
            pincode = pin.group(1);
        }

        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }

        String branchName;
        String cityText;
        String address;
        if (lines.size() >= 3) {
            branchName = lines.get(0);
            cityText = lines.get(1);
            address = String.join(", ", lines.subList(2, lines.size()));
        } else if (lines.size() == 2) {
            Parts top = splitParts(lines.get(0));
            if (top.parts.size() >= 2) {
                Assigned assigned = assign(top.parts, cities, lines.get(0));
                branchName = assigned.branchName;
                cityText = assigned.city;
                address = join(assigned.address, lines.get(1));
            } else {
                branchName = lines.get(0);
                cityText = "";
                address = lines.get(1);
            }
        } else {
            String line = lines.isEmpty() ? text : lines.get(0);
            Assigned assigned = assign(splitParts(line).parts, cities, line);
            branchName = assigned.branchName;
            cityText = assigned.city;
            address = assigned.address;
        }

        City matched = exactCity(cityText, cities);
        if (matched == null) {
            matched = cityInside(text, cities);
        }
        if (matched != null) {
            cityText = matched.getCityName();
        }
        if (cityText.isBlank()) {
            cityText = branchName;
        }
        if (address.isBlank()) {
            address = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        }
        if (branchName.isBlank()) {
            branchName = cityText;
        }
        String state = matched == null ? "" : matched.getState();
        return new Parsed(clean(branchName), clean(cityText), state == null ? "" : state.trim(), clean(address), pincode);
    }

    private static Parts splitParts(String line) {
        String trimmed = line.trim();
        int dash = indexOfSpacedDash(trimmed);
        if (dash > 0 && !trimmed.substring(0, dash).contains(",")) {
            String left = trimmed.substring(0, dash).trim();
            String right = trimmed.substring(dash, trimmed.length()).replaceFirst("^\\s*[-\u2013]\\s*", "").trim();
            if (countChar(left, ',') == 1) {
                String[] halves = left.split(",", 2);
                return new Parts(List.of(halves[0].trim(), halves[1].trim(), right));
            }
            return new Parts(List.of(left, right));
        }
        int commas = countChar(trimmed, ',');
        if (commas >= 2) {
            String[] pieces = trimmed.split(",", 3);
            return new Parts(List.of(pieces[0].trim(), pieces[1].trim(), pieces[2].trim()));
        }
        if (commas == 1) {
            String[] pieces = trimmed.split(",", 2);
            return new Parts(List.of(pieces[0].trim(), pieces[1].trim()));
        }
        return new Parts(List.of(trimmed));
    }

    private static Assigned assign(List<String> parts, List<City> cities, String original) {
        if (parts.size() >= 3) {
            int cityIdx = exactIndex(parts, cities);
            if (cityIdx < 0) {
                cityIdx = 1;
            }
            String address = joinExcept(parts, 0, cityIdx);
            return new Assigned(parts.get(0), parts.get(cityIdx), address.isBlank() ? original : address);
        }
        if (parts.size() == 2) {
            boolean first = exactCity(parts.get(0), cities) != null;
            boolean second = exactCity(parts.get(1), cities) != null;
            if (first && !second) {
                return new Assigned(parts.get(0), parts.get(0), parts.get(1));
            }
            if (second && !first) {
                return new Assigned(parts.get(0), parts.get(1), original);
            }
            return new Assigned(parts.get(0), parts.get(0), parts.get(1));
        }
        String only = parts.isEmpty() ? original : parts.get(0);
        return new Assigned(only, "", "");
    }

    private static int exactIndex(List<String> parts, List<City> cities) {
        for (int i = 0; i < parts.size(); i++) {
            if (exactCity(parts.get(i), cities) != null) {
                return i;
            }
        }
        return -1;
    }

    private static City exactCity(String text, List<City> cities) {
        if (text == null || text.isBlank() || cities == null) {
            return null;
        }
        String wanted = text.trim().toLowerCase(Locale.ROOT);
        for (City city : cities) {
            if (city.getCityName() != null && city.getCityName().trim().toLowerCase(Locale.ROOT).equals(wanted)) {
                return city;
            }
        }
        return null;
    }

    private static City cityInside(String text, List<City> cities) {
        if (text == null || text.isBlank() || cities == null || cities.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        List<City> ordered = new ArrayList<>(cities);
        ordered.sort(Comparator.comparingInt((City city) -> city.getCityName() == null ? 0 : city.getCityName().length()).reversed());
        for (City city : ordered) {
            if (city.getCityName() == null) {
                continue;
            }
            String name = city.getCityName().trim().toLowerCase(Locale.ROOT);
            if (name.length() < 3) {
                continue;
            }
            int from = 0;
            while (from < lower.length()) {
                int idx = lower.indexOf(name, from);
                if (idx < 0) {
                    break;
                }
                int end = idx + name.length();
                boolean leftOk = idx == 0 || !Character.isLetterOrDigit(lower.charAt(idx - 1));
                boolean rightOk = end >= lower.length() || !Character.isLetterOrDigit(lower.charAt(end));
                if (leftOk && rightOk) {
                    return city;
                }
                from = idx + 1;
            }
        }
        return null;
    }

    private static int indexOfSpacedDash(String text) {
        int hyphen = text.indexOf(" - ");
        int en = text.indexOf(" \u2013 ");
        if (hyphen < 0) {
            return en;
        }
        if (en < 0) {
            return hyphen;
        }
        return Math.min(hyphen, en);
    }

    private static int countChar(String text, char c) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    private static String joinExcept(List<String> parts, int skipA, int skipB) {
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            if (i == skipA || i == skipB) {
                continue;
            }
            if (!parts.get(i).isBlank()) {
                kept.add(parts.get(i).trim());
            }
        }
        return String.join(", ", kept);
    }

    private static String join(String first, String second) {
        if (first == null || first.isBlank()) {
            return second == null ? "" : second.trim();
        }
        if (second == null || second.isBlank()) {
            return first.trim();
        }
        return first.trim() + ", " + second.trim();
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    public static final class Parsed {
        private final String branchName;
        private final String city;
        private final String state;
        private final String address;
        private final String pincode;

        public Parsed(String branchName, String city, String state, String address, String pincode) {
            this.branchName = branchName;
            this.city = city;
            this.state = state;
            this.address = address;
            this.pincode = pincode;
        }

        public String getBranchName() {
            return branchName;
        }

        public String getCity() {
            return city;
        }

        public String getState() {
            return state;
        }

        public String getAddress() {
            return address;
        }

        public String getPincode() {
            return pincode;
        }
    }

    private static final class Parts {
        private final List<String> parts;

        private Parts(List<String> parts) {
            this.parts = parts;
        }
    }

    private static final class Assigned {
        private final String branchName;
        private final String city;
        private final String address;

        private Assigned(String branchName, String city, String address) {
            this.branchName = branchName;
            this.city = city;
            this.address = address;
        }
    }
}
