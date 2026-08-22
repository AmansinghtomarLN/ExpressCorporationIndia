package com.mahavircourier.dto;

import java.util.ArrayList;
import java.util.List;

public class ConsignmentPool {

    private Long partyId;
    private String partyName;
    private long allocated;
    private long used;
    private long remaining;
    private String nextNumber;
    private List<RangeSummary> ranges = new ArrayList<>();
    private List<String> available = new ArrayList<>();
    private List<String> usedNumbers = new ArrayList<>();

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

    public long getRemaining() {
        return remaining;
    }

    public void setRemaining(long remaining) {
        this.remaining = remaining;
    }

    public String getNextNumber() {
        return nextNumber;
    }

    public void setNextNumber(String nextNumber) {
        this.nextNumber = nextNumber;
    }

    public List<RangeSummary> getRanges() {
        return ranges;
    }

    public void setRanges(List<RangeSummary> ranges) {
        this.ranges = ranges;
    }

    public List<String> getAvailable() {
        return available;
    }

    public void setAvailable(List<String> available) {
        this.available = available;
    }

    public List<String> getUsedNumbers() {
        return usedNumbers;
    }

    public void setUsedNumbers(List<String> usedNumbers) {
        this.usedNumbers = usedNumbers;
    }

    public static class RangeSummary {
        private long start;
        private long end;
        private long used;
        private long remaining;

        public long getStart() {
            return start;
        }

        public void setStart(long start) {
            this.start = start;
        }

        public long getEnd() {
            return end;
        }

        public void setEnd(long end) {
            this.end = end;
        }

        public long getUsed() {
            return used;
        }

        public void setUsed(long used) {
            this.used = used;
        }

        public long getRemaining() {
            return remaining;
        }

        public void setRemaining(long remaining) {
            this.remaining = remaining;
        }
    }
}
