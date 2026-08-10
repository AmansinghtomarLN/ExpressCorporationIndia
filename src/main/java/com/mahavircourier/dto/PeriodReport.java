package com.mahavircourier.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PeriodReport {

    public enum PeriodType {
        DAILY, WEEKLY, MONTHLY, CUSTOM
    }

    private PeriodType periodType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String title;

    private long shipmentsBooked;
    private long deliveredInPeriod;
    private long cancelledOrRtoInPeriod;
    private long delayedOpen;
    private long newUsers;
    private long newContacts;
    private long notificationsSent;
    private long notificationsFailed;

    private BigDecimal totalFreight = BigDecimal.ZERO;
    private BigDecimal totalCod = BigDecimal.ZERO;
    private BigDecimal avgWeight = BigDecimal.ZERO;
    private BigDecimal invoiceFreight = BigDecimal.ZERO;
    private BigDecimal invoiceCod = BigDecimal.ZERO;
    private BigDecimal collectedRevenue = BigDecimal.ZERO;
    private BigDecimal unpaidInvoiceTotal = BigDecimal.ZERO;
    private long unpaidInvoiceCount;

    private BigDecimal deliveryRatePct = BigDecimal.ZERO;
    private BigDecimal cancelRatePct = BigDecimal.ZERO;
    private BigDecimal avgRevenuePerShipment = BigDecimal.ZERO;

    private Map<String, Long> statusBreakdown = new LinkedHashMap<>();
    private Map<String, Long> serviceBreakdown = new LinkedHashMap<>();
    private Map<String, Long> topOriginCities = new LinkedHashMap<>();
    private List<Map<String, Object>> shipmentLines = new ArrayList<>();

    public void recalculateDerived() {
        if (shipmentsBooked > 0) {
            deliveryRatePct = BigDecimal.valueOf(deliveredInPeriod)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(shipmentsBooked), 2, RoundingMode.HALF_UP);
            cancelRatePct = BigDecimal.valueOf(cancelledOrRtoInPeriod)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(shipmentsBooked), 2, RoundingMode.HALF_UP);
            avgRevenuePerShipment = totalFreight.add(totalCod)
                    .divide(BigDecimal.valueOf(shipmentsBooked), 2, RoundingMode.HALF_UP);
        } else {
            deliveryRatePct = BigDecimal.ZERO;
            cancelRatePct = BigDecimal.ZERO;
            avgRevenuePerShipment = BigDecimal.ZERO;
        }
        if (avgWeight != null) {
            avgWeight = avgWeight.setScale(2, RoundingMode.HALF_UP);
        }
    }

    public PeriodType getPeriodType() {
        return periodType;
    }

    public void setPeriodType(PeriodType periodType) {
        this.periodType = periodType;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getShipmentsBooked() {
        return shipmentsBooked;
    }

    public void setShipmentsBooked(long shipmentsBooked) {
        this.shipmentsBooked = shipmentsBooked;
    }

    public long getDeliveredInPeriod() {
        return deliveredInPeriod;
    }

    public void setDeliveredInPeriod(long deliveredInPeriod) {
        this.deliveredInPeriod = deliveredInPeriod;
    }

    public long getCancelledOrRtoInPeriod() {
        return cancelledOrRtoInPeriod;
    }

    public void setCancelledOrRtoInPeriod(long cancelledOrRtoInPeriod) {
        this.cancelledOrRtoInPeriod = cancelledOrRtoInPeriod;
    }

    public long getDelayedOpen() {
        return delayedOpen;
    }

    public void setDelayedOpen(long delayedOpen) {
        this.delayedOpen = delayedOpen;
    }

    public long getNewUsers() {
        return newUsers;
    }

    public void setNewUsers(long newUsers) {
        this.newUsers = newUsers;
    }

    public long getNewContacts() {
        return newContacts;
    }

    public void setNewContacts(long newContacts) {
        this.newContacts = newContacts;
    }

    public long getNotificationsSent() {
        return notificationsSent;
    }

    public void setNotificationsSent(long notificationsSent) {
        this.notificationsSent = notificationsSent;
    }

    public long getNotificationsFailed() {
        return notificationsFailed;
    }

    public void setNotificationsFailed(long notificationsFailed) {
        this.notificationsFailed = notificationsFailed;
    }

    public BigDecimal getTotalFreight() {
        return totalFreight;
    }

    public void setTotalFreight(BigDecimal totalFreight) {
        this.totalFreight = totalFreight;
    }

    public BigDecimal getTotalCod() {
        return totalCod;
    }

    public void setTotalCod(BigDecimal totalCod) {
        this.totalCod = totalCod;
    }

    public BigDecimal getAvgWeight() {
        return avgWeight;
    }

    public void setAvgWeight(BigDecimal avgWeight) {
        this.avgWeight = avgWeight;
    }

    public BigDecimal getInvoiceFreight() {
        return invoiceFreight;
    }

    public void setInvoiceFreight(BigDecimal invoiceFreight) {
        this.invoiceFreight = invoiceFreight;
    }

    public BigDecimal getInvoiceCod() {
        return invoiceCod;
    }

    public void setInvoiceCod(BigDecimal invoiceCod) {
        this.invoiceCod = invoiceCod;
    }

    public BigDecimal getCollectedRevenue() {
        return collectedRevenue;
    }

    public void setCollectedRevenue(BigDecimal collectedRevenue) {
        this.collectedRevenue = collectedRevenue;
    }

    public BigDecimal getUnpaidInvoiceTotal() {
        return unpaidInvoiceTotal;
    }

    public void setUnpaidInvoiceTotal(BigDecimal unpaidInvoiceTotal) {
        this.unpaidInvoiceTotal = unpaidInvoiceTotal;
    }

    public long getUnpaidInvoiceCount() {
        return unpaidInvoiceCount;
    }

    public void setUnpaidInvoiceCount(long unpaidInvoiceCount) {
        this.unpaidInvoiceCount = unpaidInvoiceCount;
    }

    public BigDecimal getDeliveryRatePct() {
        return deliveryRatePct;
    }

    public BigDecimal getCancelRatePct() {
        return cancelRatePct;
    }

    public BigDecimal getAvgRevenuePerShipment() {
        return avgRevenuePerShipment;
    }

    public Map<String, Long> getStatusBreakdown() {
        return statusBreakdown;
    }

    public void setStatusBreakdown(Map<String, Long> statusBreakdown) {
        this.statusBreakdown = statusBreakdown;
    }

    public Map<String, Long> getServiceBreakdown() {
        return serviceBreakdown;
    }

    public void setServiceBreakdown(Map<String, Long> serviceBreakdown) {
        this.serviceBreakdown = serviceBreakdown;
    }

    public Map<String, Long> getTopOriginCities() {
        return topOriginCities;
    }

    public void setTopOriginCities(Map<String, Long> topOriginCities) {
        this.topOriginCities = topOriginCities;
    }

    public List<Map<String, Object>> getShipmentLines() {
        return shipmentLines;
    }

    public void setShipmentLines(List<Map<String, Object>> shipmentLines) {
        this.shipmentLines = shipmentLines;
    }

    public BigDecimal getGrossBookedValue() {
        return totalFreight.add(totalCod);
    }
}
