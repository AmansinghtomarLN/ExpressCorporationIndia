package com.mahavircourier.dto;

public class DashboardStats {

    private long totalShipments;
    private long booked;
    private long inTransit;
    private long outForDelivery;
    private long delivered;
    private long cancelled;
    private long delayed;
    private long unreadContacts;
    private long unpaidInvoices;

    public long getTotalShipments() {
        return totalShipments;
    }

    public void setTotalShipments(long totalShipments) {
        this.totalShipments = totalShipments;
    }

    public long getBooked() {
        return booked;
    }

    public void setBooked(long booked) {
        this.booked = booked;
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

    public long getDelivered() {
        return delivered;
    }

    public void setDelivered(long delivered) {
        this.delivered = delivered;
    }

    public long getCancelled() {
        return cancelled;
    }

    public void setCancelled(long cancelled) {
        this.cancelled = cancelled;
    }

    public long getDelayed() {
        return delayed;
    }

    public void setDelayed(long delayed) {
        this.delayed = delayed;
    }

    public long getUnreadContacts() {
        return unreadContacts;
    }

    public void setUnreadContacts(long unreadContacts) {
        this.unreadContacts = unreadContacts;
    }

    public long getUnpaidInvoices() {
        return unpaidInvoices;
    }

    public void setUnpaidInvoices(long unpaidInvoices) {
        this.unpaidInvoices = unpaidInvoices;
    }
}
