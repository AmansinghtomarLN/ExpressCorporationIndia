package com.mahavircourier.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ManifestForm {

    private Long id;
    private String manifestNumber;
    private Long partyId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate manifestDate = LocalDate.now();
    private String throughName;
    private String originCity;
    private String serviceType = "DOMESTIC_STANDARD";
    private String billingLane = "AUTO";
    private String remarks;
    private List<ManifestItemForm> items = new ArrayList<>();

    public static ManifestForm blank(int rows) {
        ManifestForm form = new ManifestForm();
        form.setManifestDate(LocalDate.now());
        form.setServiceType("DOMESTIC_STANDARD");
        form.setBillingLane("AUTO");
        for (int i = 0; i < rows; i++) {
            form.getItems().add(new ManifestItemForm());
        }
        return form;
    }

    public void ensureMinRows(int rows) {
        while (items.size() < rows) {
            items.add(new ManifestItemForm());
        }
    }

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

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public List<ManifestItemForm> getItems() {
        return items;
    }

    public void setItems(List<ManifestItemForm> items) {
        this.items = items != null ? items : new ArrayList<>();
    }
}
