package com.mahavircourier.model;

import java.time.LocalDateTime;

public class CnoSettings {

    private Long id = 1L;
    private long seriesStart = 30000;
    private long seriesEnd = 199999;
    private int bucketSize = 100;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getSeriesStart() {
        return seriesStart;
    }

    public void setSeriesStart(long seriesStart) {
        this.seriesStart = seriesStart;
    }

    public long getSeriesEnd() {
        return seriesEnd;
    }

    public void setSeriesEnd(long seriesEnd) {
        this.seriesEnd = seriesEnd;
    }

    public int getBucketSize() {
        return bucketSize;
    }

    public void setBucketSize(int bucketSize) {
        this.bucketSize = bucketSize;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
