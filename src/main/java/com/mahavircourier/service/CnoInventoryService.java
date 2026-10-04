package com.mahavircourier.service;

import com.mahavircourier.dao.ConsignmentRangeDao;
import com.mahavircourier.dao.PartyDao;
import com.mahavircourier.model.ConsignmentRange;
import com.mahavircourier.model.Party;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class CnoInventoryService {

    private final ConsignmentRangeDao consignmentRangeDao;
    private final PartyDao partyDao;

    public CnoInventoryService(ConsignmentRangeDao consignmentRangeDao,
                               PartyDao partyDao) {
        this.consignmentRangeDao = consignmentRangeDao;
        this.partyDao = partyDao;
    }

    public List<ConsignmentRange> search(String query) {
        List<ConsignmentRange> all = allAllocations();
        if (!StringUtils.hasText(query)) {
            return all;
        }
        String q = query.trim();
        String lower = q.toLowerCase();
        Long number = null;
        if (q.matches("\\d+")) {
            try {
                number = Long.parseLong(q);
            } catch (NumberFormatException ignored) {
                number = null;
            }
        }
        List<ConsignmentRange> matched = new ArrayList<>();
        for (ConsignmentRange range : all) {
            boolean party = range.getPartyName() != null && range.getPartyName().toLowerCase().contains(lower);
            boolean covers = number != null && number >= range.getRangeStart() && number <= range.getRangeEnd();
            boolean bounds = String.valueOf(range.getRangeStart()).contains(q)
                    || String.valueOf(range.getRangeEnd()).contains(q);
            if (party || covers || bounds) {
                matched.add(range);
            }
        }
        return matched;
    }

    @Transactional
    public ConsignmentRange assign(Long partyId, long start, long end, String notes) {
        Party party = partyDao.findById(partyId).orElseThrow(() -> new IllegalArgumentException("Party not found"));
        if (start <= 0 || end <= 0) {
            throw new IllegalArgumentException("Consignment number must be a positive number");
        }
        if (end < start) {
            throw new IllegalArgumentException("End number must be greater than or equal to the start number");
        }
        if (consignmentRangeDao.overlaps(start, end, null)) {
            String owner = consignmentRangeDao.findCovering(start)
                    .or(() -> consignmentRangeDao.findCovering(end))
                    .map(ConsignmentRange::getPartyName)
                    .orElse("another party");
            throw new IllegalArgumentException("These numbers overlap a range already assigned to " + owner);
        }
        ConsignmentRange range = new ConsignmentRange();
        range.setPartyId(party.getId());
        range.setRangeStart(start);
        range.setRangeEnd(end);
        range.setNotes(StringUtils.hasText(notes) ? notes.trim() : null);
        range.setId(consignmentRangeDao.save(range));
        range.setPartyName(party.getPartyName());
        return range;
    }

    @Transactional
    public void reassign(Long rangeId, Long partyId) {
        consignmentRangeDao.findById(rangeId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        Party party = partyDao.findById(partyId).orElseThrow(() -> new IllegalArgumentException("Party not found"));
        if (!party.isEnabled()) {
            throw new IllegalArgumentException("Party " + party.getPartyName() + " is disabled");
        }
        consignmentRangeDao.updateParty(rangeId, party.getId());
    }

    public List<ConsignmentRange> allAllocations() {
        List<ConsignmentRange> ranges = consignmentRangeDao.findAllWithParty();
        for (ConsignmentRange range : ranges) {
            long used = consignmentRangeDao.countUsedInRange(range.getRangeStart(), range.getRangeEnd());
            range.setUsedCount(used);
            range.setVacantCount(Math.max(0, range.getAllocatedCount() - used));
        }
        return ranges;
    }

}
