package com.mahavircourier.service;

import com.mahavircourier.config.AppProperties;
import com.mahavircourier.dto.PeriodReport;
import com.mahavircourier.service.notify.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@ConditionalOnProperty(prefix = "app.report", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduler.class);

    private final ReportService reportService;
    private final AuditService auditService;
    private final AppProperties appProperties;

    public ReportScheduler(ReportService reportService,
                           AuditService auditService,
                           AppProperties appProperties) {
        this.reportService = reportService;
        this.auditService = auditService;
        this.appProperties = appProperties;
    }

    /** Every day at 08:00 — yesterday's full daily report. */
    @Scheduled(cron = "${app.report.daily.cron:0 0 8 * * *}")
    public void sendDailyReport() {
        LocalDate day = LocalDate.now().minusDays(1);
        PeriodReport report = reportService.buildDaily(day);
        EmailService.SendResult result = reportService.emailReport(report);
        auditService.log(null, "scheduler", "REPORT_DAILY_EMAIL", "REPORT", day.toString(),
                "to=" + appProperties.getReport().getEmail() + " status=" + result.status());
        log.info("Daily report scheduled send: {}", result.status());
    }

    /** Every Monday 08:00 — previous calendar week (Mon–Sun). */
    @Scheduled(cron = "${app.report.weekly.cron:0 0 8 * * MON}")
    public void sendWeeklyReport() {
        LocalDate lastWeek = LocalDate.now().minusWeeks(1);
        PeriodReport report = reportService.buildWeekly(lastWeek);
        EmailService.SendResult result = reportService.emailReport(report);
        auditService.log(null, "scheduler", "REPORT_WEEKLY_EMAIL", "REPORT",
                report.getFromDate() + ":" + report.getToDate(),
                "to=" + appProperties.getReport().getEmail() + " status=" + result.status());
        log.info("Weekly report scheduled send: {}", result.status());
    }

    /** 1st of each month 08:00 — previous month. */
    @Scheduled(cron = "${app.report.monthly.cron:0 0 8 1 * *}")
    public void sendMonthlyReport() {
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        PeriodReport report = reportService.buildMonthly(lastMonth);
        EmailService.SendResult result = reportService.emailReport(report);
        auditService.log(null, "scheduler", "REPORT_MONTHLY_EMAIL", "REPORT",
                report.getFromDate() + ":" + report.getToDate(),
                "to=" + appProperties.getReport().getEmail() + " status=" + result.status());
        log.info("Monthly report scheduled send: {}", result.status());
    }
}
