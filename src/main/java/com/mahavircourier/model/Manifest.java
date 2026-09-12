package com.mahavircourier.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Manifest {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_CREATED = "CREATED";

    private Long id;
    private String manifestNumber;
    private Long partyId;
    private LocalDate manifestDate;
    private String throughName;
    private String originCity;
    private String serviceType;
    private String billingLane = "AUTO";
    private String status = STATUS_CREATED;
    private Long destinationBranchId;
    private String remarks;
    private int totalBoxes;
    private BigDecimal totalWeight = BigDecimal.ZERO;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String partyName;
    private String partyPhone;
    private String partyCity;
    private String partyAddress;
    private String partyGstin;

    private List<ManifestItem> items = new ArrayList<>();
    private BigDecimal totalFreight = BigDecimal.ZERO;
    private String destinationBranchName;
    private String destinationBranchCity;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getManifestNumber() {
        return manifestNumber;
    }

    public void setManifestNumber(String manifestNumber) {
        this.manifestNumber = manifestNumber;
    }

    public Long getPartyId() {
        return partyId;
    }

    public void setPartyId(Long partyId) {
        this.partyId = partyId;
    }

    public LocalDate getManifestDate() {
        return manifestDate;
    }

    public void setManifestDate(LocalDate manifestDate) {
        this.manifestDate = manifestDate;
    }

    public String getThroughName() {
        return throughName;
    }

    public void setThroughName(String throughName) {
        this.throughName = throughName;
    }

    public String getOriginCity() {
        return originCity;
    }

    public void setOriginCity(String originCity) {
        this.originCity = originCity;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getBillingLane() {
        return billingLane;
    }

    public void setBillingLane(String billingLane) {
        this.billingLane = billingLane;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isInProgress() {
        return STATUS_IN_PROGRESS.equalsIgnoreCase(status);
    }

    public boolean isSubmitted() {
        return STATUS_CREATED.equalsIgnoreCase(status);
    }

    public String getStatusLabel() {
        return isInProgress() ? "In progress" : "Submitted";
    }

    public Long getDestinationBranchId() {
        return destinationBranchId;
    }

    public void setDestinationBranchId(Long destinationBranchId) {
        this.destinationBranchId = destinationBranchId;
    }

    public String getDestinationBranchName() {
        return destinationBranchName;
    }

    public void setDestinationBranchName(String destinationBranchName) {
        this.destinationBranchName = destinationBranchName;
    }

    public String getDestinationBranchCity() {
        return destinationBranchCity;
    }

    public void setDestinationBranchCity(String destinationBranchCity) {
        this.destinationBranchCity = destinationBranchCity;
    }

    public String getDestinationBranchDisplay() {
        if (destinationBranchName != null && !destinationBranchName.isBlank()) {
            return destinationBranchCity != null && !destinationBranchCity.isBlank()
                    ? destinationBranchName + " — " + destinationBranchCity
                    : destinationBranchName;
        }
        return destinationBranchCity != null && !destinationBranchCity.isBlank()
                ? destinationBranchCity
                : "—";
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public int getTotalBoxes() {
        return totalBoxes;
    }

    public void setTotalBoxes(int totalBoxes) {
        this.totalBoxes = totalBoxes;
    }

    public BigDecimal getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(BigDecimal totalWeight) {
        this.totalWeight = totalWeight;
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

    public String getPartyPhone() {
        return partyPhone;
    }

    public void setPartyPhone(String partyPhone) {
        this.partyPhone = partyPhone;
    }

    public String getPartyCity() {
        return partyCity;
    }

    public void setPartyCity(String partyCity) {
        this.partyCity = partyCity;
    }

    public String getPartyAddress() {
        return partyAddress;
    }

    public void setPartyAddress(String partyAddress) {
        this.partyAddress = partyAddress;
    }

    public String getPartyGstin() {
        return partyGstin;
    }

    public void setPartyGstin(String partyGstin) {
        this.partyGstin = partyGstin;
    }

    public List<ManifestItem> getItems() {
        return items;
    }

    public void setItems(List<ManifestItem> items) {
        this.items = items;
    }

    public BigDecimal getTotalFreight() {
        return totalFreight;
    }

    public void setTotalFreight(BigDecimal totalFreight) {
        this.totalFreight = totalFreight;
    }
}
