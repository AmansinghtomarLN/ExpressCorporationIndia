package com.mahavircourier.model;

import java.time.LocalDateTime;

public class ConsignmentRange {

    private Long id;
    private Long partyId;
    private long rangeStart;
    private long rangeEnd;
    private String notes;
    private LocalDateTime createdAt;
    private String partyName;
    private long usedCount;
    private long vacantCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPartyId() {
        return partyId;
    }

    public void setPartyId(Long partyId) {
        this.partyId = partyId;
    }

    public long getRangeStart() {
        return rangeStart;
    }

    public void setRangeStart(long rangeStart) {
        this.rangeStart = rangeStart;
    }

    public long getRangeEnd() {
        return rangeEnd;
    }

    public void setRangeEnd(long rangeEnd) {
        this.rangeEnd = rangeEnd;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getPartyName() {
        return partyName;
    }

    public void setPartyName(String partyName) {
        this.partyName = partyName;
    }

    public long getAllocatedCount() {
        return rangeEnd >= rangeStart ? (rangeEnd - rangeStart + 1) : 0;
    }

    public long getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(long usedCount) {
        this.usedCount = usedCount;
    }

    public long getVacantCount() {
        return vacantCount;
    }

    public void setVacantCount(long vacantCount) {
        this.vacantCount = vacantCount;
    }
}
