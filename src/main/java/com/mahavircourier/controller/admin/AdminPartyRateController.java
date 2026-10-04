package com.mahavircourier.controller.admin;

import com.mahavircourier.model.City;
import com.mahavircourier.model.Party;
import com.mahavircourier.model.PartyCityRate;
import com.mahavircourier.service.CityCategory;
import com.mahavircourier.service.CityService;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.PartyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

@Controller
@RequestMapping("/admin/party-rates")
public class AdminPartyRateController {

    static final String BASE_CITY = "Indore";

    private final PartyService partyService;
    private final CityService cityService;

    public AdminPartyRateController(PartyService partyService,
                                    CityService cityService
                                    ) {
        this.partyService = partyService;
        this.cityService = cityService;
    }

    @GetMapping
    public String page(@RequestParam(value = "partyId", required = false) Long partyId,
                       @RequestParam(value = "group", required = false) String group,
                       Model model) {
        List<Party> parties = partyService.findEnabled();
        model.addAttribute("parties", parties);
        model.addAttribute("groups", CityCategory.ALL);
        model.addAttribute("baseCity", BASE_CITY);
        Party selected = partyId == null ? null : partyService.findById(partyId).orElse(null);
        model.addAttribute("selectedParty", selected);
        String selectedGroup = normalizeGroup(group);
        model.addAttribute("group", selectedGroup);
        model.addAttribute("groupLabel", selectedGroup == null ? "" : CityCategory.displayName(selectedGroup));

        List<City> groupCities = List.of();
        Map<String, PartyCityRate> ratesByCity = Map.of();
        int setCount = 0;
        if (selected != null && selectedGroup != null) {
            groupCities = cityService.search(null, null, selectedGroup);
            ratesByCity = indexRates(partyService.findCityRates(selected.getId()));
            for (City city : groupCities) {
                if (ratesByCity.containsKey(city.getCityName().toLowerCase(Locale.ROOT))) {
                    setCount++;
                }
            }
        }
        model.addAttribute("groupCities", groupCities);
        model.addAttribute("ratesByCity", ratesByCity);
        model.addAttribute("setCount", setCount);
        model.addAttribute("missingCount", groupCities.size() - setCount);
        model.addAttribute("states", distinctStates(groupCities));
        return "admin/party-rates";
    }

    @GetMapping("/status")
    public String status(@RequestParam(value = "partyId", required = false) Long partyId, Model model) {
        List<Party> parties = partyService.findEnabled();
        List<City> cities = cityService.findAll();
        Map<Long, Integer> counts = partyService.countCityRatesByParty();
        List<PartyCoverage> coverage = new ArrayList<>();
        for (Party party : parties) {
            int set = counts.getOrDefault(party.getId(), 0);
            coverage.add(new PartyCoverage(party.getId(), party.getPartyName(), party.getCity(), set, cities.size()));
        }
        model.addAttribute("coverage", coverage);
        model.addAttribute("cityTotal", cities.size());
        model.addAttribute("parties", parties);
        Party selected = partyId == null ? null : partyService.findById(partyId).orElse(null);
        model.addAttribute("selectedParty", selected);
        model.addAttribute("groups", CityCategory.ALL);

        List<RateRow> setRows = new ArrayList<>();
        List<RateRow> missingRows = new ArrayList<>();
        List<GroupCoverage> groupCoverage = new ArrayList<>();
        if (selected != null) {
            Map<String, PartyCityRate> ratesByCity = indexRates(partyService.findCityRates(selected.getId()));
            Map<String, int[]> byGroup = new LinkedHashMap<>();
            for (String code : CityCategory.ALL) {
                byGroup.put(code, new int[]{0, 0});
            }
            for (City city : cities) {
                String code = CityCategory.normalize(city.getCategory());
                int[] tally = byGroup.computeIfAbsent(code, key -> new int[]{0, 0});
                tally[1]++;
                PartyCityRate rate = ratesByCity.get(city.getCityName().toLowerCase(Locale.ROOT));
                if (rate != null) {
                    tally[0]++;
                    setRows.add(new RateRow(city.getCityName(), city.getState(), code,
                            CityCategory.displayName(code), rate.getPerKgRate(), rate.getMinWeightKg()));
                } else {
                    missingRows.add(new RateRow(city.getCityName(), city.getState(), code,
                            CityCategory.displayName(code), null, null));
                }
            }
            setRows.sort(Comparator.comparing(RateRow::getGroup).thenComparing(RateRow::getCityName));
            missingRows.sort(Comparator.comparing(RateRow::getGroup).thenComparing(RateRow::getCityName));
            for (String code : CityCategory.ALL) {
                int[] tally = byGroup.getOrDefault(code, new int[]{0, 0});
                groupCoverage.add(new GroupCoverage(code, CityCategory.displayName(code), tally[0], tally[1]));
            }
        }
        model.addAttribute("setRows", setRows);
        model.addAttribute("missingRows", missingRows);
        model.addAttribute("groupCoverage", groupCoverage);
        return "admin/party-rate-status";
    }

    @PostMapping("/base")
    public String saveBase(@RequestParam Long partyId,
                           @RequestParam(required = false) String group,
                           @RequestParam(required = false) BigDecimal perKgRate,
                           @RequestParam(required = false) BigDecimal minWeightKg,
                           RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            partyService.saveRates(partyId, perKgRate, null, minWeightKg);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Indore base rate saved. Cities without their own rate use this price.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect(partyId, group);
    }

    @PostMapping("/group")
    public String applyGroup(@RequestParam Long partyId,
                             @RequestParam String group,
                             @RequestParam BigDecimal perKgRate,
                             @RequestParam(required = false) BigDecimal minWeightKg,
                             RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            int saved = partyService.applyGroupRate(partyId, group, perKgRate, minWeightKg);
            String label = CityCategory.displayName(CityCategory.normalize(group));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Set ₹" + perKgRate.toPlainString() + " per kg on " + saved
                            + (saved == 1 ? " city" : " cities") + " in " + label + ".");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect(partyId, group);
    }

    @PostMapping("/minimum")
    public String setMinimum(@RequestParam Long partyId,
                             @RequestParam(required = false) String group,
                             @RequestParam boolean follow,
                             RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            Party party = partyService.findById(partyId)
                    .orElseThrow(() -> new IllegalArgumentException("Party not found"));
            partyService.setMinimumRate(partyId, follow);
            redirectAttributes.addFlashAttribute("successMessage",
                    follow
                            ? party.getPartyName() + " now follows a minimum rate. Set the minimum kg below."
                            : "Minimum rate removed for " + party.getPartyName() + ".");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect(partyId, group);
    }

    @PostMapping("/cities")
    public String saveCities(@RequestParam Long partyId,
                             @RequestParam(required = false) String group,
                             @RequestParam(required = false) List<Long> removeId,
                             @RequestParam(required = false) List<String> cityName,
                             @RequestParam(required = false) List<BigDecimal> perKgRate,
                             @RequestParam(required = false) List<BigDecimal> minWeightKg,
                             RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            int saved = partyService.saveCityRates(partyId, removeId, cityName, perKgRate, minWeightKg);
            int removed = removeId == null ? 0 : (int) removeId.stream().filter(id -> id != null).count();
            if (saved == 0 && removed == 0) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Add a destination city and a per kg rate, then save.");
            } else if (saved == 0) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Removed " + removed + " city rate" + (removed == 1 ? "" : "s") + ".");
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Saved " + saved + " city rate" + (saved == 1 ? "" : "s")
                                + (removed > 0 ? " and removed " + removed : "") + ".");
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect(partyId, group);
    }

    private String redirect(Long partyId, String group) {
        String url = "redirect:/admin/party-rates?partyId=" + partyId;
        String normalized = null;
        try {
            normalized = normalizeGroup(group);
        } catch (IllegalArgumentException ex) {
            normalized = null;
        }
        if (normalized != null) {
            url += "&group=" + normalized;
        }
        return url;
    }

    private static String normalizeGroup(String group) {
        if (!StringUtils.hasText(group)) {
            return null;
        }
        return CityCategory.normalize(group);
    }

    private static Map<String, PartyCityRate> indexRates(List<PartyCityRate> rates) {
        Map<String, PartyCityRate> byCity = new HashMap<>();
        for (PartyCityRate rate : rates) {
            if (rate.getCityName() != null) {
                byCity.put(rate.getCityName().toLowerCase(Locale.ROOT), rate);
            }
        }
        return byCity;
    }

    private static List<String> distinctStates(List<City> cities) {
        TreeSet<String> states = new TreeSet<>();
        for (City city : cities) {
            if (StringUtils.hasText(city.getState())) {
                states.add(city.getState().trim());
            }
        }
        return new ArrayList<>(states);
    }

    public static final class PartyCoverage {
        private final Long partyId;
        private final String partyName;
        private final String city;
        private final int set;
        private final int total;

        public PartyCoverage(Long partyId, String partyName, String city, int set, int total) {
            this.partyId = partyId;
            this.partyName = partyName;
            this.city = city;
            this.set = set;
            this.total = total;
        }

        public Long getPartyId() {
            return partyId;
        }

        public String getPartyName() {
            return partyName;
        }

        public String getCity() {
            return city;
        }

        public int getSet() {
            return set;
        }

        public int getTotal() {
            return total;
        }

        public int getMissing() {
            return Math.max(0, total - set);
        }
    }

    public static final class GroupCoverage {
        private final String code;
        private final String label;
        private final int set;
        private final int total;

        public GroupCoverage(String code, String label, int set, int total) {
            this.code = code;
            this.label = label;
            this.set = set;
            this.total = total;
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        public int getSet() {
            return set;
        }

        public int getTotal() {
            return total;
        }

        public int getMissing() {
            return Math.max(0, total - set);
        }
    }

    public static final class RateRow {
        private final String cityName;
        private final String state;
        private final String group;
        private final String groupLabel;
        private final BigDecimal perKgRate;
        private final BigDecimal minWeightKg;

        public RateRow(String cityName, String state, String group, String groupLabel,
                       BigDecimal perKgRate, BigDecimal minWeightKg) {
            this.cityName = cityName;
            this.state = state;
            this.group = group;
            this.groupLabel = groupLabel;
            this.perKgRate = perKgRate;
            this.minWeightKg = minWeightKg;
        }

        public String getCityName() {
            return cityName;
        }

        public String getState() {
            return state;
        }

        public String getGroup() {
            return group;
        }

        public String getGroupLabel() {
            return groupLabel;
        }

        public BigDecimal getPerKgRate() {
            return perKgRate;
        }

        public BigDecimal getMinWeightKg() {
            return minWeightKg;
        }
    }
}
