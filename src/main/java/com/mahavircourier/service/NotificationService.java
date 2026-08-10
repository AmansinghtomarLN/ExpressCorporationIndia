package com.mahavircourier.service;

import com.mahavircourier.dao.NotificationDao;
import com.mahavircourier.model.NotificationLog;
import com.mahavircourier.model.Shipment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationDao notificationDao;

    public NotificationService(NotificationDao notificationDao) {
        this.notificationDao = notificationDao;
    }

    /**
     * Logs EMAIL and SMS notifications for a shipment status change.
     * Uses sender phone for SMS and a synthetic email for EMAIL channel.
     */
    public void notifyStatusChange(Shipment shipment, String newStatus) {
        if (shipment == null) {
            return;
        }
        String trackingId = shipment.getTrackingId();
        String statusLabel = newStatus != null ? newStatus.replace('_', ' ') : "";
        String body = "Your shipment " + trackingId + " is now " + statusLabel + ".";
        String subject = "Shipment update: " + trackingId;

        String smsRecipient = StringUtils.hasText(shipment.getReceiverPhone())
                ? shipment.getReceiverPhone()
                : shipment.getSenderPhone();
        String emailLocal = StringUtils.hasText(shipment.getSenderName())
                ? shipment.getSenderName().replaceAll("[^A-Za-z0-9]", "").toLowerCase()
                : "sender";
        if (!StringUtils.hasText(emailLocal)) {
            emailLocal = "sender";
        }
        String emailRecipient = emailLocal + "@notify.local";

        saveLog(shipment.getId(), "SMS", smsRecipient, subject, body);
        saveLog(shipment.getId(), "EMAIL", emailRecipient, subject, body);
    }

    public List<NotificationLog> listRecent(int limit) {
        return notificationDao.findAll(Math.min(Math.max(limit, 1), 500));
    }

    private void saveLog(Long shipmentId, String channel, String recipient, String subject, String body) {
        NotificationLog log = new NotificationLog();
        log.setShipmentId(shipmentId);
        log.setChannel(channel);
        log.setRecipient(recipient != null ? recipient : "unknown");
        log.setSubject(subject);
        log.setBody(body);
        log.setStatus("SENT");
        notificationDao.save(log);
    }
}
