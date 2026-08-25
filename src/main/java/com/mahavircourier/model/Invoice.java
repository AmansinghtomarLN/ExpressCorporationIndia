package com.mahavircourier.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Invoice {

    private Long id;
    private Long shipmentId;
    private String invoiceNumber;
    private BigDecimal freightAmount;
    private BigDecimal codAmount;
    private BigDecimal totalAmount;
    private String status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Long billedBranchId;
    private BigDecimal weightKg;
    private Integer numberOfBoxes;
    private BigDecimal perKgRate;
    private BigDecimal perBoxRate;

    /** Transient display fields (not always persisted). */
    private String trackingId;
    private String billedBranchName;
    private String billedBranchCity;
    private String destinationCity;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(Long shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public BigDecimal getFreightAmount() {
        return freightAmount;
    }

    public void setFreightAmount(BigDecimal freightAmount) {
        this.freightAmount = freightAmount;
    }

    public BigDecimal getCodAmount() {
        return codAmount;
    }

    public void setCodAmount(BigDecimal codAmount) {
        this.codAmount = codAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
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

    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(String trackingId) {
        this.trackingId = trackingId;
    }

    public Long getBilledBranchId() {
        return billedBranchId;
    }

    public void setBilledBranchId(Long billedBranchId) {
        this.billedBranchId = billedBranchId;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public Integer getNumberOfBoxes() {
        return numberOfBoxes;
    }

    public void setNumberOfBoxes(Integer numberOfBoxes) {
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

    public String getBilledBranchName() {
        return billedBranchName;
    }

    public void setBilledBranchName(String billedBranchName) {
        this.billedBranchName = billedBranchName;
    }

    public String getBilledBranchCity() {
        return billedBranchCity;
    }

    public void setBilledBranchCity(String billedBranchCity) {
        this.billedBranchCity = billedBranchCity;
    }

    public String getDestinationCity() {
        return destinationCity;
    }

    public void setDestinationCity(String destinationCity) {
        this.destinationCity = destinationCity;
    }
}
