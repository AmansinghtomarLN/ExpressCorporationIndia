package com.mahavircourier.dto;

import java.math.BigDecimal;

public class FreightQuote {

    private Long branchId;
    private String branchName;
    private String branchCity;
    private String lane;
    private BigDecimal perKgRate = BigDecimal.ZERO;
    private BigDecimal perBoxRate = BigDecimal.ZERO;
    private BigDecimal amount = BigDecimal.ZERO;

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getBranchCity() {
        return branchCity;
    }

    public void setBranchCity(String branchCity) {
        this.branchCity = branchCity;
    }

    public String getLane() {
        return lane;
    }

    public void setLane(String lane) {
        this.lane = lane;
    }

    public BigDecimal getPerKgRate() {
        return perKgRate;
    }

    public void setPerKgRate(BigDecimal perKgRate) {
        this.perKgRate = perKgRate;
    }

    public BigDecimal getPerBoxRate() {
        return perBoxRate;
    }

    public void setPerBoxRate(BigDecimal perBoxRate) {
        this.perBoxRate = perBoxRate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
