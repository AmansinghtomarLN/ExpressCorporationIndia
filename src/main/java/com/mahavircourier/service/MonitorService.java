package com.mahavircourier.service;

import com.mahavircourier.config.AppProperties;
import com.mahavircourier.dao.InvoiceDao;
import com.mahavircourier.dao.ShipmentDao;
import com.mahavircourier.dto.MonitoringSnapshot;
import com.mahavircourier.service.notify.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ShipmentDao shipmentDao;
    private final InvoiceDao invoiceDao;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final AuditService auditService;

    public MonitorService(JdbcTemplate jdbcTemplate,
                          ShipmentDao shipmentDao,
                          InvoiceDao invoiceDao,
                          NotificationService notificationService,
                          EmailService emailService,
                          AppProperties appProperties,
                          AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.shipmentDao = shipmentDao;
        this.invoiceDao = invoiceDao;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.appProperties = appProperties;
        this.auditService = auditService;
    }

    public MonitoringSnapshot snapshot() {
        MonitoringSnapshot snap = new MonitoringSnapshot();
        snap.setDatabaseUp(pingDatabase());
        snap.setMailConfigured(emailService.isMailConfigured());
        snap.setSmsConfigured(appProperties.getSms().getTwilio().isConfigured());
        snap.setDelayedShipments(shipmentDao.countDelayed());
        snap.setUnpaidInvoices(invoiceDao.countByStatus("UNPAID"));
        snap.setFailedNotifications(notificationService.countFailed());
        snap.setTotalShipments(shipmentDao.countAll());
        snap.setInTransit(shipmentDao.countByStatus("IN_TRANSIT") + shipmentDao.countByStatus("AT_HUB"));
        snap.setOutForDelivery(shipmentDao.countByStatus("OUT_FOR_DELIVERY"));
        snap.setActiveAlerts(buildAlerts(snap));
        return snap;
    }

    @Scheduled(cron = "${app.monitor.alert-cron:0 0/30 * * * *}")
    public void evaluateAndAlert() {
        MonitoringSnapshot snap = snapshot();
        if (snap.getActiveAlerts().isEmpty()) {
            return;
        }
        StringBuilder body = new StringBuilder();
        body.append("Monitoring alerts for ").append(appProperties.getCompanyName()).append(":\n\n");
        for (String alert : snap.getActiveAlerts()) {
            body.append("- ").append(alert).append("\n");
        }
        body.append("\nOpen /admin/monitoring for details.");
        EmailService.SendResult result = emailService.sendText(
                appProperties.getMonitor().getAlertEmail(),
                "[ALERT] " + appProperties.getCompanyName() + " monitoring",
                body.toString());
        auditService.log(null, "monitor", "MONITOR_ALERT_EMAIL", "MONITOR", null,
                "alerts=" + snap.getActiveAlerts().size() + " status=" + result.status());
        log.warn("Monitoring alerts fired ({}): email={}", snap.getActiveAlerts().size(), result.status());
    }

    private List<String> buildAlerts(MonitoringSnapshot snap) {
        List<String> alerts = new ArrayList<>();
        if (!snap.isDatabaseUp()) {
            alerts.add("Database connectivity check FAILED");
        }
        if (snap.getDelayedShipments() >= appProperties.getMonitor().getDelayedThreshold()) {
            alerts.add("Delayed shipments: " + snap.getDelayedShipments());
        }
        if (snap.getUnpaidInvoices() >= appProperties.getMonitor().getUnpaidCodThreshold()) {
            alerts.add("Unpaid invoices: " + snap.getUnpaidInvoices());
        }
        if (snap.getFailedNotifications() >= appProperties.getMonitor().getFailedNotifyThreshold()) {
            alerts.add("Failed notifications: " + snap.getFailedNotifications());
        }
        if (!snap.isMailConfigured()) {
            alerts.add("Email is not fully configured (MAIL_USERNAME / MAIL_PASSWORD)");
        }
        if (appProperties.getSms().isEnabled() && !snap.isSmsConfigured()) {
            alerts.add("SMS enabled but Twilio credentials are missing");
        }
        return alerts;
    }

    private boolean pingDatabase() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception ex) {
            log.error("DB health check failed: {}", ex.getMessage());
            return false;
        }
    }
}
