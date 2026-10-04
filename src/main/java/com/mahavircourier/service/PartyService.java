package com.mahavircourier.service;

import com.mahavircourier.dao.ConsignmentRangeDao;
import com.mahavircourier.dao.CnoSettingsDao;
import com.mahavircourier.dao.ManifestDao;
import com.mahavircourier.dao.ManifestItemDao;
import com.mahavircourier.dao.PartyCityRateDao;
import com.mahavircourier.dao.PartyDao;
import com.mahavircourier.dao.ShipmentDao;
import com.mahavircourier.dto.ConsignmentPool;
import com.mahavircourier.model.ConsignmentRange;
import com.mahavircourier.model.Party;
import com.mahavircourier.model.PartyCityRate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class PartyService {

    private final PartyDao partyDao;
    private final ConsignmentRangeDao consignmentRangeDao;
    private final ManifestDao manifestDao;
    private final ManifestItemDao manifestItemDao;
    private final ShipmentDao shipmentDao;
    private final CnoSettingsDao cnoSettingsDao;
    private final PartyCityRateDao partyCityRateDao;
    private final CityService cityService;

    public PartyService(PartyDao partyDao,
                        ConsignmentRangeDao consignmentRangeDao,
                        ManifestDao manifestDao,
                        ManifestItemDao manifestItemDao,
                        ShipmentDao shipmentDao,
                        CnoSettingsDao cnoSettingsDao,
                        PartyCityRateDao partyCityRateDao,
                        CityService cityService) {
        this.partyDao = partyDao;
        this.consignmentRangeDao = consignmentRangeDao;
        this.manifestDao = manifestDao;
        this.manifestItemDao = manifestItemDao;
        this.shipmentDao = shipmentDao;
        this.cnoSettingsDao = cnoSettingsDao;
        this.partyCityRateDao = partyCityRateDao;
        this.cityService = cityService;
    }

    public List<Party> search(String query) {
        return partyDao.search(query);
    }

    public List<Party> findEnabled() {
        return partyDao.findEnabled();
    }

    public Optional<Party> findById(Long id) {
        Optional<Party> party = partyDao.findById(id);
        party.ifPresent(p -> p.setRanges(consignmentRangeDao.findByPartyId(p.getId())));
        return party;
    }

    @Transactional
    public Party create(Party party) {
        normalize(party);
        validate(party);
        Long id = partyDao.save(party);
        party.setId(id);
        return party;
    }

    public List<PartyCityRate> findCityRates(Long partyId) {
        return partyCityRateDao.findByPartyId(partyId);
    }

    public java.util.Map<Long, Integer> countCityRatesByParty() {
        return partyCityRateDao.countByParty();
    }

    @Transactional
    public int applyGroupRate(Long partyId, String category, BigDecimal perKgRate, BigDecimal minWeightKg) {
        Party party = partyDao.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("Party not found"));
        if (perKgRate == null) {
            throw new IllegalArgumentException("Per kg rate is required");
        }
        if (perKgRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Per kg rate cannot be negative");
        }
        String group = CityCategory.normalize(category);
        List<com.mahavircourier.model.City> cities = cityService.search(null, null, group);
        if (cities.isEmpty()) {
            throw new IllegalArgumentException("No cities in " + CityCategory.displayName(group));
        }
        boolean updateMinimum = minWeightKg != null;
        BigDecimal minimum = updateMinimum ? normalizeMinimum(party, minWeightKg) : null;
        for (com.mahavircourier.model.City city : cities) {
            PartyCityRate rate = new PartyCityRate();
            rate.setPartyId(party.getId());
            rate.setCityName(city.getCityName());
            rate.setPerKgRate(perKgRate);
            rate.setMinWeightKg(minimum);
            if (updateMinimum) {
                partyCityRateDao.upsert(rate);
            } else {
                partyCityRateDao.upsertPerKg(rate);
            }
        }
        return cities.size();
    }

    @Transactional
    public void saveRates(Long id, BigDecimal perKgRate, BigDecimal perBoxRate, BigDecimal minWeightKg) {
        Party party = partyDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Party not found"));
        if (perKgRate != null && perKgRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Per kg rate cannot be negative");
        }
        if (perBoxRate != null && perBoxRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Per box rate cannot be negative");
        }
        party.setPerKgRate(perKgRate);
        if (perBoxRate != null) {
            party.setPerBoxRate(perBoxRate);
        }
        party.setMinWeightKg(normalizeMinimum(party, minWeightKg));
        partyDao.update(party);
    }

    @Transactional
    public void setMinimumRate(Long id, boolean follow) {
        Party party = partyDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Party not found"));
        party.setMinRateEligible(follow);
        if (!follow) {
            party.setMinWeightKg(null);
            partyCityRateDao.clearMinimums(id);
        }
        partyDao.update(party);
    }

    @Transactional
    public int saveCityRates(Long partyId,
                             List<Long> removeIds,
                             List<String> cityNames,
                             List<BigDecimal> perKgRates,
                             List<BigDecimal> minWeights) {
        if (removeIds != null) {
            for (Long rateId : removeIds) {
                if (rateId != null) {
                    deleteCityRate(partyId, rateId);
                }
            }
        }
        if (cityNames == null || cityNames.isEmpty()) {
            return 0;
        }
        int saved = 0;
        for (int i = 0; i < cityNames.size(); i++) {
            String cityName = cityNames.get(i);
            BigDecimal perKgRate = perKgRates != null && i < perKgRates.size() ? perKgRates.get(i) : null;
            BigDecimal minWeightKg = minWeights != null && i < minWeights.size() ? minWeights.get(i) : null;
            if (!StringUtils.hasText(cityName) || perKgRate == null) {
                continue;
            }
            saveCityRate(partyId, cityName, perKgRate, minWeightKg);
            saved++;
        }
        return saved;
    }

    @Transactional
    public void saveCityRate(Long partyId, String cityName, BigDecimal perKgRate, BigDecimal minWeightKg) {
        Party party = partyDao.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("Party not found"));
        if (!StringUtils.hasText(cityName)) {
            throw new IllegalArgumentException("City is required");
        }
        if (perKgRate == null) {
            throw new IllegalArgumentException("Per kg rate is required");
        }
        if (perKgRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Per kg rate cannot be negative");
        }
        String city = cityService.findByName(cityName)
                .map(known -> known.getCityName())
                .orElse(cityName.trim());
        PartyCityRate rate = new PartyCityRate();
        rate.setPartyId(party.getId());
        rate.setCityName(city);
        rate.setPerKgRate(perKgRate);
        rate.setMinWeightKg(normalizeMinimum(party, minWeightKg));
        partyCityRateDao.upsert(rate);
    }

    @Transactional
    public void deleteCityRate(Long partyId, Long rateId) {
        PartyCityRate rate = partyCityRateDao.findById(rateId)
                .orElseThrow(() -> new IllegalArgumentException("City rate not found"));
        if (!rate.getPartyId().equals(partyId)) {
            throw new IllegalArgumentException("City rate does not belong to this party");
        }
        partyCityRateDao.deleteById(rateId);
    }

    @Transactional
    public void update(Party party) {
        Party existing = party.getId() == null ? null : partyDao.findById(party.getId()).orElse(null);
        if (existing == null) {
            throw new IllegalArgumentException("Party not found");
        }
        normalize(party);
        party.setPerBoxRate(existing.getPerBoxRate());
        party.setMinWeightKg(existing.getMinWeightKg());
        if (!party.isMinRateEligible()) {
            party.setMinWeightKg(null);
            partyCityRateDao.clearMinimums(party.getId());
        }
        validate(party);
        partyDao.update(party);
    }

    @Transactional
    public void delete(Long id) {
        if (manifestDao.countByPartyId(id) > 0) {
            throw new IllegalArgumentException("Cannot delete a party that already has manifests");
        }
        partyDao.deleteById(id);
    }

    @Transactional
    public ConsignmentRange addRange(Long partyId, long start, long end, String notes) {
        partyDao.findById(partyId).orElseThrow(() -> new IllegalArgumentException("Party not found"));
        if (start <= 0 || end <= 0) {
            throw new IllegalArgumentException("Consignment range must be positive numbers");
        }
        if (end < start) {
            throw new IllegalArgumentException("Range end must be greater than or equal to range start");
        }
        if (consignmentRangeDao.overlaps(start, end, null)) {
            String owner = consignmentRangeDao.findCovering(start)
                    .or(() -> consignmentRangeDao.findCovering(end))
                    .map(ConsignmentRange::getPartyName)
                    .orElse("another party");
            throw new IllegalArgumentException(
                    "This consignment range overlaps numbers allocated to " + owner);
        }
        ConsignmentRange range = new ConsignmentRange();
        range.setPartyId(partyId);
        range.setRangeStart(start);
        range.setRangeEnd(end);
        range.setNotes(StringUtils.hasText(notes) ? notes.trim() : null);
        Long id = consignmentRangeDao.save(range);
        range.setId(id);
        return range;
    }

    @Transactional
    public void deleteRange(Long partyId, Long rangeId) {
        ConsignmentRange range = consignmentRangeDao.findById(rangeId)
                .orElseThrow(() -> new IllegalArgumentException("Range not found"));
        if (!range.getPartyId().equals(partyId)) {
            throw new IllegalArgumentException("Range does not belong to this party");
        }
        if (consignmentRangeDao.countUsedInRange(range.getRangeStart(), range.getRangeEnd()) > 0) {
            throw new IllegalArgumentException("Cannot delete a range that already has used consignment numbers");
        }
        consignmentRangeDao.deleteById(rangeId);
    }

    public ConsignmentPool buildPool(Long partyId) {
        Party party = partyDao.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("Party not found"));
        List<ConsignmentRange> ranges = consignmentRangeDao.findByPartyId(partyId);
        Set<Long> used = new HashSet<>();
        List<String> usedDisplay = new ArrayList<>();
        for (String cno : manifestItemDao.findUsedConsignmentNosByParty(partyId)) {
            Long parsed = parseConsignment(cno);
            if (parsed != null) {
                used.add(parsed);
            }
            usedDisplay.add(cno);
        }
        for (String cno : shipmentDao.findTrackingIdsByParty(partyId)) {
            Long parsed = parseConsignment(cno);
            if (parsed != null) {
                used.add(parsed);
            }
            if (!usedDisplay.contains(cno)) {
                usedDisplay.add(cno);
            }
        }

        ConsignmentPool pool = new ConsignmentPool();
        pool.setPartyId(party.getId());
        pool.setPartyName(party.getPartyName());
        pool.setUsedNumbers(usedDisplay.size() > 40
                ? usedDisplay.subList(usedDisplay.size() - 40, usedDisplay.size())
                : usedDisplay);

        List<String> available = new ArrayList<>();
        long allocated = 0;
        long usedInRanges = 0;
        for (ConsignmentRange range : ranges) {
            long size = range.getAllocatedCount();
            allocated += size;
            long rangeUsed = 0;
            for (long n = range.getRangeStart(); n <= range.getRangeEnd(); n++) {
                if (used.contains(n)) {
                    rangeUsed++;
                } else if (available.size() < 120) {
                    available.add(String.valueOf(n));
                }
            }
            usedInRanges += rangeUsed;
            ConsignmentPool.RangeSummary summary = new ConsignmentPool.RangeSummary();
            summary.setStart(range.getRangeStart());
            summary.setEnd(range.getRangeEnd());
            summary.setUsed(rangeUsed);
            summary.setRemaining(Math.max(0, size - rangeUsed));
            pool.getRanges().add(summary);
        }
        pool.setAllocated(allocated);
        pool.setUsed(usedInRanges);
        pool.setRemaining(Math.max(0, allocated - usedInRanges));
        pool.setAvailable(available);
        if (!available.isEmpty()) {
            pool.setNextNumber(available.get(0));
        }
        return pool;
    }

    /**
     * Next number that is not inside any party range and not already booked.
     */
    public long nextUnassignedConsignment() {
        return nextVacantNumber();
    }

    /**
     * A short hint for party booking: the next free number in that party's sequence.
     */
    public String partySequenceHint(Long partyId) {
        ConsignmentPool pool = buildPool(partyId);
        String next = pool.getNextNumber();
        if (!StringUtils.hasText(next)) {
            return "No consignment numbers are left for this party. Assign a range on C.No mapping.";
        }
        List<String> preview = new ArrayList<>();
        if (pool.getAvailable() != null) {
            for (String candidate : pool.getAvailable()) {
                if (preview.size() >= 8) {
                    break;
                }
                preview.add(candidate);
            }
        }
        String sequence = String.join(", ", preview);
        if (pool.getRanges() == null || pool.getRanges().isEmpty()) {
            return "No sequence is assigned yet. Next available Consignment Number: " + next;
        }
        if (preview.size() <= 1) {
            return "Next available Consignment Number: " + next;
        }
        return "Next available Consignment Number: " + next + ". Sequence: " + sequence;
    }

    /**
     * Live booking number: must not already belong to a party, and must not be in use.
     * A blank request takes the next free number.
     */
    public String requireUnassignedConsignment(String requested) {
        long number;
        if (!StringUtils.hasText(requested)) {
            number = nextVacantNumber();
        } else {
            Long parsed = parseConsignment(requested.trim());
            if (parsed == null) {
                throw new IllegalArgumentException("Consignment number must be numeric");
            }
            number = parsed;
        }
        Optional<ConsignmentRange> owner = consignmentRangeDao.findCovering(number);
        if (owner.isPresent()) {
            String name = owner.get().getPartyName() != null ? owner.get().getPartyName() : "a party";
            throw new IllegalArgumentException(
                    "C.No " + number + " is already assigned to " + name
                            + ". Enter a number that is not assigned to any party.");
        }
        String token = String.valueOf(number);
        if (shipmentDao.existsByTrackingId(token) || manifestItemDao.existsByConsignmentNo(token)) {
            throw new IllegalArgumentException("C.No " + number + " is already used");
        }
        return token;
    }

    public String nextUnusedConsignment(Long partyId) {
        String next = buildPool(partyId).getNextNumber();
        if (!StringUtils.hasText(next)) {
            throw new IllegalArgumentException("No unused C.No left for this party. Allocate a new range first.");
        }
        return next;
    }

    /**
     * Uses the requested C.No if it is free, or generates and assigns a new one
     * when the party has no remaining allocated numbers.
     */
    @Transactional
    public String resolveForBooking(Long partyId, String requested) {
        partyDao.findById(partyId).orElseThrow(() -> new IllegalArgumentException("Party not found"));
        if (StringUtils.hasText(requested)) {
            Long number = parseConsignment(requested.trim());
            if (number == null) {
                throw new IllegalArgumentException("Consignment number must be numeric");
            }
            String token = String.valueOf(number);
            if (shipmentDao.existsByTrackingId(token) || manifestItemDao.existsByConsignmentNo(token)) {
                throw new IllegalArgumentException("C.No " + number + " is already used");
            }
            claimNumber(partyId, number);
            return token;
        }
        return allocateNext(partyId);
    }

    @Transactional
    public String allocateNext(Long partyId) {
        ConsignmentPool pool = buildPool(partyId);
        if (pool.getAvailable() != null) {
            for (String candidate : pool.getAvailable()) {
                if (StringUtils.hasText(candidate)
                        && !shipmentDao.existsByTrackingId(candidate)
                        && !manifestItemDao.existsByConsignmentNo(candidate)) {
                    return candidate;
                }
            }
        }
        long generated = nextVacantNumber();
        claimNumber(partyId, generated);
        return String.valueOf(generated);
    }

    @Transactional
    public void claimNumber(Long partyId, long number) {
        Optional<ConsignmentRange> owner = consignmentRangeDao.findCovering(number);
        if (owner.isPresent()) {
            if (!owner.get().getPartyId().equals(partyId)) {
                String name = owner.get().getPartyName() != null ? owner.get().getPartyName() : "another party";
                throw new IllegalArgumentException(
                        "C.No " + number + " is allocated to " + name + " and cannot be used");
            }
            return;
        }
        addRange(partyId, number, number, "Auto-allocated");
    }

    public Optional<ConsignmentRange> findOwner(long number) {
        return consignmentRangeDao.findCovering(number);
    }

    public boolean ownsConsignment(Long partyId, String consignmentNo) {
        Long number = parseConsignment(consignmentNo);
        return number != null && consignmentRangeDao.covers(partyId, number);
    }

    private long nextVacantNumber() {
        long seriesStart = 1L;
        try {
            long configured = cnoSettingsDao.load().getSeriesStart();
            if (configured > 0) {
                seriesStart = configured;
            }
        } catch (Exception ignored) {
            // use 1
        }
        long candidate = Math.max(seriesStart, consignmentRangeDao.maxAllocatedEnd() + 1);
        while (consignmentRangeDao.findCovering(candidate).isPresent()
                || shipmentDao.existsByTrackingId(String.valueOf(candidate))
                || manifestItemDao.existsByConsignmentNo(String.valueOf(candidate))) {
            candidate++;
        }
        return candidate;
    }

    public static Long parseConsignment(String consignmentNo) {
        if (!StringUtils.hasText(consignmentNo)) {
            return null;
        }
        String digits = consignmentNo.trim();
        if (!digits.matches("\\d+")) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void normalize(Party party) {
        party.setPartyName(trimRequired(party.getPartyName(), "Party name"));
        party.setPhone(trimRequired(party.getPhone(), "Phone"));
        party.setCity(trimRequired(party.getCity(), "City"));
        party.setContactPerson(trimToNull(party.getContactPerson()));
        party.setEmail(trimToNull(party.getEmail()));
        party.setGstin(normalizeGstin(party.getGstin()));
        party.setAddress(trimToNull(party.getAddress()));
        party.setState(trimToNull(party.getState()));
        party.setPincode(trimToNull(party.getPincode()));
        party.setNotes(trimToNull(party.getNotes()));
    }

    private void validate(Party party) {
        if (party.getPartyName().length() < 2) {
            throw new IllegalArgumentException("Party name is too short");
        }
        if (!StringUtils.hasText(party.getGstin())) {
            throw new IllegalArgumentException("GST number is required");
        }
        if (party.getPerKgRate() != null && party.getPerKgRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Per kg rate cannot be negative");
        }
        if (party.getPerBoxRate() != null && party.getPerBoxRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Per box rate cannot be negative");
        }
        if (party.getMinWeightKg() != null && party.getMinWeightKg().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Minimum kg cannot be negative");
        }
        if (party.getMinWeightKg() != null && !party.isMinRateEligible()) {
            throw new IllegalArgumentException(
                    "Tag this party for a minimum rate before setting a minimum kg");
        }
    }

    private BigDecimal normalizeMinimum(Party party, BigDecimal minWeightKg) {
        if (minWeightKg == null) {
            return null;
        }
        if (minWeightKg.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Minimum kg cannot be negative");
        }
        if (!party.isMinRateEligible()) {
            throw new IllegalArgumentException(
                    "Tag this party for a minimum rate when creating or editing the party, then set the minimum kg here");
        }
        if (minWeightKg.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return minWeightKg;
    }

    private String normalizeGstin(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String gstin = value.trim().toUpperCase();
        if (!gstin.matches("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][A-Z0-9]Z[A-Z0-9]")) {
            throw new IllegalArgumentException("Enter a valid 15-character GST number");
        }
        return gstin;
    }

    private String trimRequired(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
