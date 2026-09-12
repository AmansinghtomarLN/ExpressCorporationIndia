package com.mahavircourier.service;

import com.mahavircourier.dao.ConsignmentRangeDao;
import com.mahavircourier.dao.ManifestDao;
import com.mahavircourier.dao.ManifestItemDao;
import com.mahavircourier.dao.PartyDao;
import com.mahavircourier.dto.ConsignmentPool;
import com.mahavircourier.model.ConsignmentRange;
import com.mahavircourier.model.Party;
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

    public PartyService(PartyDao partyDao,
                        ConsignmentRangeDao consignmentRangeDao,
                        ManifestDao manifestDao,
                        ManifestItemDao manifestItemDao) {
        this.partyDao = partyDao;
        this.consignmentRangeDao = consignmentRangeDao;
        this.manifestDao = manifestDao;
        this.manifestItemDao = manifestItemDao;
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

    @Transactional
    public void saveRates(Long id, BigDecimal perKgRate, BigDecimal perBoxRate) {
        Party party = partyDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Party not found"));
        if (perKgRate != null && perKgRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Per kg rate cannot be negative");
        }
        if (perBoxRate != null && perBoxRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Per box rate cannot be negative");
        }
        party.setPerKgRate(perKgRate);
        party.setPerBoxRate(perBoxRate);
        partyDao.update(party);
    }

    @Transactional
    public void update(Party party) {
        if (party.getId() == null || partyDao.findById(party.getId()).isEmpty()) {
            throw new IllegalArgumentException("Party not found");
        }
        normalize(party);
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
            throw new IllegalArgumentException(
                    "This consignment range overlaps another party's allocated numbers");
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

    public String nextUnusedConsignment(Long partyId) {
        String next = buildPool(partyId).getNextNumber();
        if (!StringUtils.hasText(next)) {
            throw new IllegalArgumentException("No unused C.No left for this party. Allocate a new range first.");
        }
        return next;
    }

    public boolean ownsConsignment(Long partyId, String consignmentNo) {
        Long number = parseConsignment(consignmentNo);
        return number != null && consignmentRangeDao.covers(partyId, number);
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
        party.setGstin(trimToNull(party.getGstin()));
        party.setAddress(trimToNull(party.getAddress()));
        party.setState(trimToNull(party.getState()));
        party.setPincode(trimToNull(party.getPincode()));
        party.setNotes(trimToNull(party.getNotes()));
    }

    private void validate(Party party) {
        if (party.getPartyName().length() < 2) {
            throw new IllegalArgumentException("Party name is too short");
        }
        if (party.getPerKgRate() != null && party.getPerKgRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Per kg rate cannot be negative");
        }
        if (party.getPerBoxRate() != null && party.getPerBoxRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Per box rate cannot be negative");
        }
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
