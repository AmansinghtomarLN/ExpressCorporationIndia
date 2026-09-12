package com.mahavircourier.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Party {

    private Long id;
    private String partyName;
    private String contactPerson;
    private String phone;
    private String email;
    private String gstin;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String notes;
    private BigDecimal perKgRate;
    private BigDecimal perBoxRate;
    private boolean enabled = true;
    private LocalDateTime createdAt;

    private List<ConsignmentRange> ranges = new ArrayList<>();
    private int rangeCount;
    private int manifestCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPartyName() {
        return partyName;
    }

    public void setPartyName(String partyName) {
        this.partyName = partyName;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    public boolean hasCustomRates() {
        return perKgRate != null || perBoxRate != null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<ConsignmentRange> getRanges() {
        return ranges;
    }

    public void setRanges(List<ConsignmentRange> ranges) {
        this.ranges = ranges;
    }

    public int getRangeCount() {
        return rangeCount;
    }

    public void setRangeCount(int rangeCount) {
        this.rangeCount = rangeCount;
    }

    public int getManifestCount() {
        return manifestCount;
    }

    public void setManifestCount(int manifestCount) {
        this.manifestCount = manifestCount;
    }
}
