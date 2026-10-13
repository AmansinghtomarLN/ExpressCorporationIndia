package com.mahavircourier.service;

import com.mahavircourier.dao.BillingTariffDao;
import com.mahavircourier.dao.BranchDao;
import com.mahavircourier.model.BillingTariff;
import com.mahavircourier.model.Branch;
import com.mahavircourier.model.City;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class BranchService {

    private static final int NAME_LIMIT = 120;
    private static final int CITY_LIMIT = 80;
    private static final int STATE_LIMIT = 80;
    private static final int PIN_LIMIT = 10;
    private static final int PHONE_LIMIT = 60;
    private static final int ADDRESS_LIMIT = 255;
    private static final int CONTACT_LIMIT = 120;

    private final BranchDao branchDao;
    private final BillingTariffDao billingTariffDao;
    private final CityService cityService;

    public BranchService(BranchDao branchDao, BillingTariffDao billingTariffDao, CityService cityService) {
        this.branchDao = branchDao;
        this.billingTariffDao = billingTariffDao;
        this.cityService = cityService;
    }

    public List<Branch> findAll() {
        return branchDao.findAll();
    }

    public Optional<Branch> findByCity(String city) {
        if (city == null || city.isBlank()) {
            return Optional.empty();
        }
        return branchDao.findByCityIgnoreCase(city.trim());
    }

    public Optional<Branch> findById(Long id) {
        return branchDao.findById(id);
    }

    @Transactional
    public Branch create(Branch branch) {
        prepare(branch);
        applyCategory(branch);
        Long id = branchDao.save(branch);
        branch.setId(id);
        return branch;
    }

    @Transactional
    public void update(Branch branch) {
        prepare(branch);
        applyCategory(branch);
        branchDao.update(branch);
    }

    @Transactional
    public ImportOutcome importExcel(InputStream input, String filename) throws IOException {
        byte[] data = input == null ? new byte[0] : input.readAllBytes();
        if (filename != null && !filename.isBlank()) {
            String lower = filename.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".xls") && !lower.endsWith(".xlsx")) {
                throw new IllegalArgumentException("Use an Excel file (.xls or .xlsx)");
            }
        }
        List<List<String>> grid = ExcelGridReader.read(data);
        int header = headerRow(grid);
        if (header < 0) {
            throw new IllegalArgumentException(
                    "The sheet needs the columns SR NO., BranchName, CONT. PERSONAL, and MOBILE NO.");
        }
        List<String> titles = grid.get(header);
        int nameCol = column(titles, "branch");
        int contactCol = column(titles, "contact");
        int mobileCol = column(titles, "mobile");
        if (nameCol < 0) {
            throw new IllegalArgumentException("Could not find the BranchName column.");
        }
        List<City> cities = cityService.findAll();
        int created = 0;
        int updated = 0;
        List<String> problems = new ArrayList<>();
        for (int r = header + 1; r < grid.size(); r++) {
            List<String> row = grid.get(r);
            String rawName = cell(row, nameCol);
            String contact = contactCol < 0 ? "" : cell(row, contactCol);
            String mobile = mobileCol < 0 ? "" : cell(row, mobileCol);
            if (rawName.isBlank() && contact.isBlank() && mobile.isBlank()) {
                continue;
            }
            int excelRow = r + 1;
            if (rawName.isBlank()) {
                addProblem(problems, "Row " + excelRow + " has no BranchName, so it was skipped.");
                continue;
            }
            BranchNameParser.Parsed parsed = BranchNameParser.parse(rawName, cities);
            if (parsed.getBranchName().isBlank()) {
                addProblem(problems, "Row " + excelRow + " has no BranchName, so it was skipped.");
                continue;
            }
            Optional<Branch> existing = branchDao.findByNameIgnoreCase(parsed.getBranchName());
            if (existing.isPresent()) {
                Branch branch = existing.get();
                branch.setCity(limit(parsed.getCity(), CITY_LIMIT));
                branch.setAddress(blankToNull(limit(parsed.getAddress(), ADDRESS_LIMIT)));
                if (!parsed.getState().isBlank()) {
                    branch.setState(limit(parsed.getState(), STATE_LIMIT));
                }
                if (!parsed.getPincode().isBlank()) {
                    branch.setPincode(limit(parsed.getPincode(), PIN_LIMIT));
                }
                if (!contact.isBlank()) {
                    branch.setContactPerson(limit(contact, CONTACT_LIMIT));
                }
                if (!mobile.isBlank()) {
                    branch.setPhone(limit(mobile, PHONE_LIMIT));
                }
                update(branch);
                updated++;
            } else {
                Branch branch = new Branch();
                branch.setBranchName(parsed.getBranchName());
                branch.setCity(parsed.getCity());
                branch.setState(parsed.getState());
                branch.setPincode(parsed.getPincode());
                branch.setAddress(parsed.getAddress());
                branch.setContactPerson(contact);
                branch.setPhone(mobile);
                branch.setBranchCategory(null);
                create(branch);
                created++;
            }
        }
        if (created == 0 && updated == 0 && problems.isEmpty()) {
            throw new IllegalArgumentException("The sheet has no branch rows under the header.");
        }
        return new ImportOutcome(created, updated, problems);
    }

    private void applyCategory(Branch branch) {
        if (branch.getBranchCategory() == null || branch.getBranchCategory().isBlank()
                || "AUTO".equalsIgnoreCase(branch.getBranchCategory())) {
            branch.setBranchCategory(BranchCategory.fromState(branch.getState()));
        } else {
            String lane = branch.getBranchCategory().trim().toUpperCase();
            if (!BranchCategory.DOMESTIC.equals(lane) && !BranchCategory.NATIONAL.equals(lane)) {
                throw new IllegalArgumentException("Branch category must be DOMESTIC or NATIONAL");
            }
            branch.setBranchCategory(lane);
        }
        applyDefaultRates(branch);
    }

    private void prepare(Branch branch) {
        if (branch.getBranchName() == null || branch.getBranchName().isBlank()) {
            throw new IllegalArgumentException("Branch name is required");
        }
        branch.setBranchName(limit(branch.getBranchName(), NAME_LIMIT));
        branch.setCity(limit(blankToEmpty(branch.getCity()), CITY_LIMIT));
        branch.setState(limit(blankToEmpty(branch.getState()), STATE_LIMIT));
        branch.setPincode(limit(blankToEmpty(branch.getPincode()), PIN_LIMIT));
        branch.setPhone(blankToNull(limit(branch.getPhone(), PHONE_LIMIT)));
        branch.setAddress(blankToNull(limit(branch.getAddress(), ADDRESS_LIMIT)));
        branch.setContactPerson(blankToNull(limit(branch.getContactPerson(), CONTACT_LIMIT)));
    }

    private static int headerRow(List<List<String>> grid) {
        int last = Math.min(grid.size(), 20);
        for (int i = 0; i < last; i++) {
            if (column(grid.get(i), "branch") >= 0) {
                return i;
            }
        }
        return -1;
    }

    private static int column(List<String> titles, String kind) {
        for (int i = 0; i < titles.size(); i++) {
            String key = titles.get(i) == null ? "" : titles.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
            if (key.isEmpty()) {
                continue;
            }
            boolean match = switch (kind) {
                case "branch" -> key.equals("branchname") || key.equals("branch name") || key.equals("branch");
                case "contact" -> key.equals("cont personal") || key.equals("contact person")
                        || key.equals("contact personal") || key.equals("cont person") || key.contains("personal");
                case "mobile" -> key.equals("mobile no") || key.equals("mobile") || key.equals("mobile number")
                        || key.equals("phone") || key.equals("phone no") || key.equals("mob") || key.equals("mob no");
                default -> false;
            };
            if (match) {
                return i;
            }
        }
        return -1;
    }

    private static String cell(List<String> row, int index) {
        if (index < 0 || index >= row.size() || row.get(index) == null) {
            return "";
        }
        return row.get(index).trim();
    }

    private static void addProblem(List<String> problems, String message) {
        if (problems.size() < 8) {
            problems.add(message);
        }
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private void applyDefaultRates(Branch branch) {
        Optional<BillingTariff> tariff = billingTariffDao.findByLane(branch.getBranchCategory());
        if (branch.getPerKgRate() == null) {
            branch.setPerKgRate(tariff.map(BillingTariff::getPerKgRate).orElse(BigDecimal.ZERO));
        }
        if (branch.getPerBoxRate() == null) {
            branch.setPerBoxRate(tariff.map(BillingTariff::getPerBoxRate).orElse(BigDecimal.ZERO));
        }
    }

    @Transactional
    public void delete(Long id) {
        branchDao.deleteById(id);
    }

    public static final class ImportOutcome {
        private final int created;
        private final int updated;
        private final List<String> problems;

        public ImportOutcome(int created, int updated, List<String> problems) {
            this.created = created;
            this.updated = updated;
            this.problems = problems;
        }

        public String successText() {
            int total = created + updated;
            if (total == 0) {
                return null;
            }
            return "Imported " + total + " branch" + (total == 1 ? "" : "es")
                    + " from Excel. " + created + " new, " + updated + " updated.";
        }

        public String problemText() {
            if (problems == null || problems.isEmpty()) {
                return null;
            }
            return String.join(" ", problems);
        }
    }
}
