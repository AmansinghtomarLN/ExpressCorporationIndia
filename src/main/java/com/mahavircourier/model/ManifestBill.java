package com.mahavircourier.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ManifestBill {

    public static final String TYPE_PARTY = "PARTY";
    public static final String TYPE_BRANCH = "BRANCH";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RECEIVED = "RECEIVED";

    private Long id;
    private String billNumber;
    private String billType;
    private Long manifestId;
    private Long partyId;
    private Long branchId;
    private BigDecimal weightKg = BigDecimal.ZERO;
    private int numberOfBoxes;
    private BigDecimal perKgRate = BigDecimal.ZERO;
    private BigDecimal perBoxRate = BigDecimal.ZERO;
    private BigDecimal freightAmount = BigDecimal.ZERO;
    private String status = STATUS_PENDING;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String partyName;
    private String branchName;
    private String branchCity;
    private String manifestNumber;

    public boolean isPending() {
        return STATUS_PENDING.equalsIgnoreCase(status);
    }

    public String getStatusLabel() {
        return isPending() ? "Pending" : "Received";
    }

    public boolean isPartyBill() {
        return TYPE_PARTY.equalsIgnoreCase(billType);
    }

    public String getTypeLabel() {
        return isPartyBill() ? "Party" : "Branch";
    }

    public String getPartyDisplay() {
        return partyName != null && !partyName.isBlank() ? partyName : "—";
    }

    public String getBranchDisplay() {
        if (branchName != null && !branchName.isBlank()) {
            return branchCity != null && !branchCity.isBlank()
                    ? branchName + " — " + branchCity
                    : branchName;
        }
        return branchCity != null && !branchCity.isBlank() ? branchCity : "—";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBillNumber() {
        return billNumber;
    }

    public void setBillNumber(String billNumber) {
        this.billNumber = billNumber;
    }

    public String getBillType() {
        return billType;
    }

    public void setBillType(String billType) {
        this.billType = billType;
    }

    public Long getManifestId() {
        return manifestId;
    }

    public void setManifestId(Long manifestId) {
        this.manifestId = manifestId;
    }

    public Long getPartyId() {
        return partyId;
    }

    public void setPartyId(Long partyId) {
        this.partyId = partyId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public int getNumberOfBoxes() {
        return numberOfBoxes;
    }

    public void setNumberOfBoxes(int numberOfBoxes) {
        this.numberOfBoxes = numberOfBoxes;
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

    public BigDecimal getFreightAmount() {
        return freightAmount;
    }

    public void setFreightAmount(BigDecimal freightAmount) {
        this.freightAmount = freightAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPartyName() {
        return partyName;
    }

    public void setPartyName(String partyName) {
        this.partyName = partyName;
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

    public String getManifestNumber() {
        return manifestNumber;
    }

    public void setManifestNumber(String manifestNumber) {
        this.manifestNumber = manifestNumber;
    }
}
