package com.mahavircourier.dto;

import java.util.ArrayList;
import java.util.List;

public class MonitoringSnapshot {

    private boolean databaseUp;
    private boolean mailConfigured;
    private boolean smsConfigured;
    private long delayedShipments;
    private long unpaidInvoices;
    private long failedNotifications;
    private long totalShipments;
    private long inTransit;
    private long outForDelivery;
    private List<String> activeAlerts = new ArrayList<>();

    public boolean isDatabaseUp() {
        return databaseUp;
    }

    public void setDatabaseUp(boolean databaseUp) {
        this.databaseUp = databaseUp;
    }

    public boolean isMailConfigured() {
        return mailConfigured;
    }

    public void setMailConfigured(boolean mailConfigured) {
        this.mailConfigured = mailConfigured;
    }

    public boolean isSmsConfigured() {
        return smsConfigured;
    }

    public void setSmsConfigured(boolean smsConfigured) {
        this.smsConfigured = smsConfigured;
    }

    public long getDelayedShipments() {
        return delayedShipments;
    }

    public void setDelayedShipments(long delayedShipments) {
        this.delayedShipments = delayedShipments;
    }

    public long getUnpaidInvoices() {
        return unpaidInvoices;
    }

    public void setUnpaidInvoices(long unpaidInvoices) {
        this.unpaidInvoices = unpaidInvoices;
    }

    public long getFailedNotifications() {
        return failedNotifications;
    }

    public void setFailedNotifications(long failedNotifications) {
        this.failedNotifications = failedNotifications;
    }

    public long getTotalShipments() {
        return totalShipments;
    }

    public void setTotalShipments(long totalShipments) {
        this.totalShipments = totalShipments;
    }

    public long getInTransit() {
        return inTransit;
    }

    public void setInTransit(long inTransit) {
        this.inTransit = inTransit;
    }

    public long getOutForDelivery() {
        return outForDelivery;
    }

    public void setOutForDelivery(long outForDelivery) {
        this.outForDelivery = outForDelivery;
    }

    public List<String> getActiveAlerts() {
        return activeAlerts;
    }

    public void setActiveAlerts(List<String> activeAlerts) {
        this.activeAlerts = activeAlerts;
    }

    public boolean isHealthy() {
        return databaseUp && activeAlerts.isEmpty();
    }
}
