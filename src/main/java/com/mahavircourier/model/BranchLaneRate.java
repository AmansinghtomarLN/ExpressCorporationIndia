package com.mahavircourier.model;

import java.math.BigDecimal;

public class BranchLaneRate {

    private Long id;
    private Long originBranchId;
    private Long destBranchId;
    private BigDecimal perKgRate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOriginBranchId() {
        return originBranchId;
    }

    public void setOriginBranchId(Long originBranchId) {
        this.originBranchId = originBranchId;
    }

    public Long getDestBranchId() {
        return destBranchId;
    }

    public void setDestBranchId(Long destBranchId) {
        this.destBranchId = destBranchId;
    }

    public BigDecimal getPerKgRate() {
        return perKgRate;
    }

    public void setPerKgRate(BigDecimal perKgRate) {
        this.perKgRate = perKgRate;
    }
}
