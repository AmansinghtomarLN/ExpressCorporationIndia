package com.mahavircourier.service;

import com.mahavircourier.dao.ConsignmentRangeDao;
import com.mahavircourier.dao.CnoSettingsDao;
import com.mahavircourier.dao.PartyDao;
import com.mahavircourier.model.ConsignmentRange;
import com.mahavircourier.model.CnoSettings;
import com.mahavircourier.model.Party;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CnoInventoryService {

    private final CnoSettingsDao cnoSettingsDao;
    private final ConsignmentRangeDao consignmentRangeDao;
    private final PartyDao partyDao;

    public CnoInventoryService(CnoSettingsDao cnoSettingsDao,
                               ConsignmentRangeDao consignmentRangeDao,
                               PartyDao partyDao) {
        this.cnoSettingsDao = cnoSettingsDao;
        this.consignmentRangeDao = consignmentRangeDao;
        this.partyDao = partyDao;
    }

    public CnoSettings settings() {
        return cnoSettingsDao.load();
    }

    @Transactional
    public void updateSettings(long seriesStart, long seriesEnd, int bucketSize) {
        if (seriesStart <= 0 || seriesEnd <= 0) {
            throw new IllegalArgumentException("Series must be positive");
        }
        if (seriesEnd < seriesStart) {
            throw new IllegalArgumentException("Series end must be after series start");
        }
        if (bucketSize < 1) {
            throw new IllegalArgumentException("Bucket size must be at least 1");
        }
        CnoSettings settings = new CnoSettings();
        settings.setSeriesStart(seriesStart);
        settings.setSeriesEnd(seriesEnd);
        settings.setBucketSize(bucketSize);
        cnoSettingsDao.save(settings);
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

    public List<PartyRangeSummary> partySummaries() {
        Map<Long, PartyRangeSummary> byParty = new LinkedHashMap<>();
        for (ConsignmentRange range : allAllocations()) {
            PartyRangeSummary summary = byParty.computeIfAbsent(range.getPartyId(), id -> {
                PartyRangeSummary s = new PartyRangeSummary();
                s.setPartyId(id);
                s.setPartyName(range.getPartyName());
                return s;
            });
            summary.setRangeCount(summary.getRangeCount() + 1);
            summary.setAllocated(summary.getAllocated() + range.getAllocatedCount());
            summary.setUsed(summary.getUsed() + range.getUsedCount());
            summary.setVacant(summary.getVacant() + range.getVacantCount());
        }
        return new ArrayList<>(byParty.values());
    }

    public long[] previewNextBucket(Integer overrideSize) {
        CnoSettings settings = settings();
        int size = overrideSize != null && overrideSize > 0 ? overrideSize : settings.getBucketSize();
        return findNextBucket(settings, size);
    }

    @Transactional
    public List<ConsignmentRange> allocateBuckets(Long partyId, int bucketCount, Integer overrideSize, String notes) {
        Party party = partyDao.findById(partyId).orElseThrow(() -> new IllegalArgumentException("Party not found"));
        if (bucketCount < 1) {
            throw new IllegalArgumentException("Allocate at least 1 bucket");
        }
        CnoSettings settings = settings();
        int size = overrideSize != null && overrideSize > 0 ? overrideSize : settings.getBucketSize();
        List<ConsignmentRange> created = new ArrayList<>();
        for (int i = 0; i < bucketCount; i++) {
            long[] bucket = findNextBucket(settings, size);
            ConsignmentRange range = new ConsignmentRange();
            range.setPartyId(party.getId());
            range.setRangeStart(bucket[0]);
            range.setRangeEnd(bucket[1]);
            String label = "Bucket of " + size;
            if (StringUtils.hasText(notes)) {
                label = notes.trim() + " (" + label + ")";
            }
            range.setNotes(label);
            range.setId(consignmentRangeDao.save(range));
            range.setPartyName(party.getPartyName());
            range.setUsedCount(0);
            range.setVacantCount(range.getAllocatedCount());
            created.add(range);
        }
        return created;
    }

    private long[] findNextBucket(CnoSettings settings, int size) {
        List<ConsignmentRange> occupied = consignmentRangeDao.findAllWithParty();
        occupied.sort((a, b) -> Long.compare(a.getRangeStart(), b.getRangeStart()));
        long cursor = settings.getSeriesStart();
        for (ConsignmentRange range : occupied) {
            if (range.getRangeEnd() < cursor) {
                continue;
            }
            if (range.getRangeStart() > cursor) {
                long gap = range.getRangeStart() - cursor;
                if (gap >= size) {
                    return new long[]{cursor, cursor + size - 1};
                }
            }
            cursor = Math.max(cursor, range.getRangeEnd() + 1);
        }
        if (cursor + size - 1 <= settings.getSeriesEnd()) {
            return new long[]{cursor, cursor + size - 1};
        }
        throw new IllegalArgumentException(
                "No vacant bucket of " + size + " left in series "
                        + settings.getSeriesStart() + "–" + settings.getSeriesEnd());
    }

    public static class PartyRangeSummary {
        private Long partyId;
        private String partyName;
        private int rangeCount;
        private long allocated;
        private long used;
        private long vacant;

        public Long getPartyId() {
            return partyId;
        }

        public void setPartyId(Long partyId) {
            this.partyId = partyId;
        }

        public String getPartyName() {
            return partyName;
        }

        public void setPartyName(String partyName) {
            this.partyName = partyName;
        }

        public int getRangeCount() {
            return rangeCount;
        }

        public void setRangeCount(int rangeCount) {
            this.rangeCount = rangeCount;
        }

        public long getAllocated() {
            return allocated;
        }

        public void setAllocated(long allocated) {
            this.allocated = allocated;
        }

        public long getUsed() {
            return used;
        }

        public void setUsed(long used) {
            this.used = used;
        }

        public long getVacant() {
            return vacant;
        }

        public void setVacant(long vacant) {
            this.vacant = vacant;
        }
    }
}
