package com.mahavircourier.service;

import com.mahavircourier.config.AppProperties;
import com.mahavircourier.dao.ReportDao;
import com.mahavircourier.dto.PeriodReport;
import com.mahavircourier.service.notify.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final ReportDao reportDao;
    private final EmailService emailService;
    private final AppProperties appProperties;

    public ReportService(ReportDao reportDao, EmailService emailService, AppProperties appProperties) {
        this.reportDao = reportDao;
        this.emailService = emailService;
        this.appProperties = appProperties;
    }

    public PeriodReport buildDaily(LocalDate day) {
        return build(PeriodReport.PeriodType.DAILY, day, day,
                "Daily Operations Report — " + day.format(DAY));
    }

    public PeriodReport buildWeekly(LocalDate anyDayInWeek) {
        LocalDate from = anyDayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate to = anyDayInWeek.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return build(PeriodReport.PeriodType.WEEKLY, from, to,
                "Weekly Operations Report — " + from.format(DAY) + " to " + to.format(DAY));
    }

    public PeriodReport buildMonthly(LocalDate anyDayInMonth) {
        LocalDate from = anyDayInMonth.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate to = anyDayInMonth.with(TemporalAdjusters.lastDayOfMonth());
        return build(PeriodReport.PeriodType.MONTHLY, from, to,
                "Monthly Operations Report — " + from.format(DAY) + " to " + to.format(DAY));
    }

    public PeriodReport buildCustom(LocalDate from, LocalDate to) {
        return build(PeriodReport.PeriodType.CUSTOM, from, to,
                "Custom Report — " + from.format(DAY) + " to " + to.format(DAY));
    }

    public PeriodReport build(PeriodReport.PeriodType type, LocalDate from, LocalDate to, String title) {
        PeriodReport report = new PeriodReport();
        report.setPeriodType(type);
        report.setFromDate(from);
        report.setToDate(to);
        report.setTitle(title);

        report.setShipmentsBooked(reportDao.countBookedBetween(from, to));
        report.setDeliveredInPeriod(reportDao.countDeliveredBetween(from, to));
        report.setCancelledOrRtoInPeriod(reportDao.countCancelledBetween(from, to));
        report.setDelayedOpen(reportDao.countDelayedAsOf(to));
        report.setNewUsers(reportDao.countUsersRegisteredBetween(from, to));
        report.setNewContacts(reportDao.countContactsBetween(from, to));
        report.setNotificationsSent(reportDao.countNotificationsBetween(from, to));
        report.setNotificationsFailed(reportDao.countFailedNotificationsBetween(from, to));

        report.setTotalFreight(reportDao.sumFreightBookedBetween(from, to));
        report.setTotalCod(reportDao.sumCodBookedBetween(from, to));
        report.setAvgWeight(reportDao.avgWeightBookedBetween(from, to));
        report.setInvoiceFreight(reportDao.sumInvoiceFreightBetween(from, to));
        report.setInvoiceCod(reportDao.sumInvoiceCodBetween(from, to));
        report.setCollectedRevenue(reportDao.sumInvoicePaidBetween(from, to));
        report.setUnpaidInvoiceTotal(reportDao.sumInvoiceUnpaidOpen());
        report.setUnpaidInvoiceCount(reportDao.countUnpaidInvoices());

        report.setStatusBreakdown(reportDao.statusBreakdownCreatedBetween(from, to));
        report.setServiceBreakdown(reportDao.serviceBreakdownCreatedBetween(from, to));
        report.setTopOriginCities(reportDao.originCityBreakdown(from, to));
        report.setShipmentLines(reportDao.shipmentLinesBetween(from, to));
        report.recalculateDerived();
        return report;
    }

    public EmailService.SendResult emailReport(PeriodReport report) {
        String to = appProperties.getReport().getEmail();
        String subject = "[" + appProperties.getCompanyName() + "] " + report.getTitle();
        String html = toHtml(report);
        EmailService.SendResult result = emailService.sendHtml(to, subject, html);
        log.info("Report email ({}) to {} => {}", report.getPeriodType(), to, result.status());
        return result;
    }

    public String toHtml(PeriodReport r) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,sans-serif;color:#222;'>");
        html.append("<h2>").append(esc(r.getTitle())).append("</h2>");
        html.append("<p><strong>").append(esc(appProperties.getCompanyName())).append("</strong><br>");
        html.append("Period: ").append(r.getFromDate()).append(" → ").append(r.getToDate()).append("</p>");

        html.append("<h3>Volume</h3><ul>");
        html.append("<li>Shipments booked: <b>").append(r.getShipmentsBooked()).append("</b></li>");
        html.append("<li>Delivered (status updates in period): <b>").append(r.getDeliveredInPeriod()).append("</b></li>");
        html.append("<li>Cancelled / RTO in period: <b>").append(r.getCancelledOrRtoInPeriod()).append("</b></li>");
        html.append("<li>Currently delayed (open): <b>").append(r.getDelayedOpen()).append("</b></li>");
        html.append("<li>Delivery rate: <b>").append(r.getDeliveryRatePct()).append("%</b></li>");
        html.append("<li>Cancel/RTO rate: <b>").append(r.getCancelRatePct()).append("%</b></li>");
        html.append("<li>Avg weight: <b>").append(r.getAvgWeight()).append(" kg</b></li>");
        html.append("</ul>");

        html.append("<h3>Financials</h3><ul>");
        html.append("<li>Freight booked: <b>Rs. ").append(r.getTotalFreight()).append("</b></li>");
        html.append("<li>COD booked: <b>Rs. ").append(r.getTotalCod()).append("</b></li>");
        html.append("<li>Gross booked value (freight+COD): <b>Rs. ").append(r.getGrossBookedValue()).append("</b></li>");
        html.append("<li>Avg revenue / shipment: <b>Rs. ").append(r.getAvgRevenuePerShipment()).append("</b></li>");
        html.append("<li>Invoice freight (created in period): <b>Rs. ").append(r.getInvoiceFreight()).append("</b></li>");
        html.append("<li>Invoice COD (created in period): <b>Rs. ").append(r.getInvoiceCod()).append("</b></li>");
        html.append("<li>Collected revenue (PAID/COD_COLLECTED updates in period): <b>Rs. ")
                .append(r.getCollectedRevenue()).append("</b></li>");
        html.append("<li>Open unpaid invoices: <b>").append(r.getUnpaidInvoiceCount())
                .append("</b> totaling <b>Rs. ").append(r.getUnpaidInvoiceTotal()).append("</b></li>");
        html.append("</ul>");

        html.append("<h3>Status breakdown (booked in period)</h3>").append(mapToHtml(r.getStatusBreakdown()));
        html.append("<h3>Service breakdown</h3>").append(mapToHtml(r.getServiceBreakdown()));
        html.append("<h3>Top origin cities</h3>").append(mapToHtml(r.getTopOriginCities()));

        html.append("<h3>Engagement & notifications</h3><ul>");
        html.append("<li>New users: <b>").append(r.getNewUsers()).append("</b></li>");
        html.append("<li>Contact messages: <b>").append(r.getNewContacts()).append("</b></li>");
        html.append("<li>Notifications logged: <b>").append(r.getNotificationsSent()).append("</b></li>");
        html.append("<li>Notifications failed: <b>").append(r.getNotificationsFailed()).append("</b></li>");
        html.append("</ul>");

        html.append("<h3>Shipment detail lines (up to 500)</h3>");
        html.append("<table border='1' cellpadding='6' cellspacing='0' style='border-collapse:collapse;font-size:12px;'>");
        html.append("<tr><th>Tracking</th><th>Status</th><th>Service</th><th>Route</th><th>Weight</th>")
                .append("<th>Freight</th><th>COD</th><th>Expected</th><th>Sender</th><th>Receiver</th></tr>");
        for (Map<String, Object> line : r.getShipmentLines()) {
            html.append("<tr>");
            html.append("<td>").append(esc(line.get("tracking_id"))).append("</td>");
            html.append("<td>").append(esc(line.get("status"))).append("</td>");
            html.append("<td>").append(esc(line.get("service_type"))).append("</td>");
            html.append("<td>").append(esc(line.get("origin_city"))).append(" → ")
                    .append(esc(line.get("destination_city"))).append("</td>");
            html.append("<td>").append(esc(line.get("weight_kg"))).append("</td>");
            html.append("<td>").append(esc(line.get("freight_charge"))).append("</td>");
            html.append("<td>").append(esc(line.get("cod_amount"))).append("</td>");
            html.append("<td>").append(esc(line.get("expected_delivery"))).append("</td>");
            html.append("<td>").append(esc(line.get("sender_name"))).append("</td>");
            html.append("<td>").append(esc(line.get("receiver_name"))).append("</td>");
            html.append("</tr>");
        }
        html.append("</table>");
        html.append("<p style='color:#666;font-size:12px;'>Generated automatically by ")
                .append(esc(appProperties.getCompanyName())).append(" admin reporting.</p>");
        html.append("</body></html>");
        return html.toString();
    }

    private String mapToHtml(Map<String, Long> map) {
        if (map == null || map.isEmpty()) {
            return "<p>None</p>";
        }
        StringBuilder sb = new StringBuilder("<ul>");
        map.forEach((k, v) -> sb.append("<li>").append(esc(k)).append(": <b>").append(v).append("</b></li>"));
        sb.append("</ul>");
        return sb.toString();
    }

    private String esc(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
