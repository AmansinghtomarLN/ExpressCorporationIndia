package com.mahavircourier.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RateCard {

    private Long id;
    private String serviceType;
    private BigDecimal minWeightKg;
    private BigDecimal maxWeightKg;
    private BigDecimal baseRate;
    private BigDecimal perKgRate;
    private boolean active = true;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public BigDecimal getMinWeightKg() {
        return minWeightKg;
    }

    public void setMinWeightKg(BigDecimal minWeightKg) {
        this.minWeightKg = minWeightKg;
    }

    public BigDecimal getMaxWeightKg() {
        return maxWeightKg;
    }

    public void setMaxWeightKg(BigDecimal maxWeightKg) {
        this.maxWeightKg = maxWeightKg;
    }

    public BigDecimal getBaseRate() {
        return baseRate;
    }

    public void setBaseRate(BigDecimal baseRate) {
        this.baseRate = baseRate;
    }

    public BigDecimal getPerKgRate() {
        return perKgRate;
    }

    public void setPerKgRate(BigDecimal perKgRate) {
        this.perKgRate = perKgRate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
