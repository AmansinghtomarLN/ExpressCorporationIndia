package com.mahavircourier.service;

import com.mahavircourier.dao.NotificationDao;
import com.mahavircourier.dao.UserDao;
import com.mahavircourier.model.NotificationLog;
import com.mahavircourier.model.Shipment;
import com.mahavircourier.model.User;
import com.mahavircourier.service.notify.EmailService;
import com.mahavircourier.service.notify.SmsService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
public class NotificationService {

    private final NotificationDao notificationDao;
    private final UserDao userDao;
    private final EmailService emailService;
    private final SmsService smsService;

    public NotificationService(NotificationDao notificationDao,
                               UserDao userDao,
                               EmailService emailService,
                               SmsService smsService) {
        this.notificationDao = notificationDao;
        this.userDao = userDao;
        this.emailService = emailService;
        this.smsService = smsService;
    }

    /**
     * Sends real EMAIL (to booking customer if known) and SMS (to receiver phone),
     * then persists delivery status in notifications table.
     */
    public void notifyStatusChange(Shipment shipment, String newStatus) {
        if (shipment == null) {
            return;
        }
        String trackingId = shipment.getTrackingId();
        String statusLabel = newStatus != null ? newStatus.replace('_', ' ') : "";
        String body = "Your shipment " + trackingId + " is now " + statusLabel
                + ". Track online with your tracking ID.";
        String subject = "Shipment update: " + trackingId;

        String emailTo = resolveCustomerEmail(shipment);
        EmailService.SendResult emailResult = emailService.sendText(emailTo, subject, body);
        saveLog(shipment.getId(), "EMAIL",
                StringUtils.hasText(emailTo) ? emailTo : "unknown",
                subject, body, emailResult.status() + (emailResult.message() != null && !emailResult.success()
                        ? (": " + truncate(emailResult.message(), 180)) : ""));

        String smsTo = StringUtils.hasText(shipment.getReceiverPhone())
                ? shipment.getReceiverPhone()
                : shipment.getSenderPhone();
        EmailService.SendResult smsResult = smsService.send(smsTo, body);
        saveLog(shipment.getId(), "SMS",
                StringUtils.hasText(smsTo) ? smsTo : "unknown",
                subject, body, smsResult.status() + (smsResult.message() != null && !smsResult.success()
                        ? (": " + truncate(smsResult.message(), 180)) : ""));
    }

    public List<NotificationLog> listRecent(int limit) {
        return notificationDao.findAll(Math.min(Math.max(limit, 1), 500));
    }

    public long countFailed() {
        return notificationDao.countByStatus("FAILED");
    }

    private String resolveCustomerEmail(Shipment shipment) {
        if (shipment.getBookedByUserId() != null) {
            Optional<User> user = userDao.findById(shipment.getBookedByUserId());
            if (user.isPresent() && StringUtils.hasText(user.get().getEmail())) {
                return user.get().getEmail();
            }
        }
        return null;
    }

    private void saveLog(Long shipmentId, String channel, String recipient,
                         String subject, String body, String status) {
        NotificationLog log = new NotificationLog();
        log.setShipmentId(shipmentId);
        log.setChannel(channel);
        log.setRecipient(recipient != null ? recipient : "unknown");
        log.setSubject(subject);
        log.setBody(truncate(body, 1000));
        // Normalize status column to SENT/FAILED/SKIPPED
        String normalized = status != null && status.startsWith("SENT") ? "SENT"
                : status != null && status.startsWith("SKIPPED") ? "SKIPPED"
                : "FAILED";
        log.setStatus(normalized);
        if (status != null && status.length() > 5 && !"SENT".equals(normalized)) {
            log.setBody(truncate(body + " [" + status + "]", 1000));
        }
        notificationDao.save(log);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
