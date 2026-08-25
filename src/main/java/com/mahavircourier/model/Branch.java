package com.mahavircourier.model;

import java.math.BigDecimal;

public class Branch {

    private Long id;
    private String branchName;
    private String city;
    private String state;
    private String pincode;
    private String phone;
    private String address;
    private String branchCategory = "DOMESTIC";
    private BigDecimal perKgRate = BigDecimal.ZERO;
    private BigDecimal perBoxRate = BigDecimal.ZERO;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getBranchCategory() {
        return branchCategory;
    }

    public void setBranchCategory(String branchCategory) {
        this.branchCategory = branchCategory;
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
}
