package com.mahavircourier.controller.admin;

import com.mahavircourier.config.AppProperties;
import com.mahavircourier.dto.PeriodReport;
import com.mahavircourier.service.AuditService;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.ReportService;
import com.mahavircourier.service.notify.EmailService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin/reports")
public class AdminReportController {

    private final ReportService reportService;
    private final AuditService auditService;
    private final AppProperties appProperties;

    public AdminReportController(ReportService reportService,
                                 AuditService auditService,
                                 AppProperties appProperties) {
        this.reportService = reportService;
        this.auditService = auditService;
        this.appProperties = appProperties;
    }

    @GetMapping
    public String reports(@RequestParam(value = "period", defaultValue = "DAILY") String period,
                          @RequestParam(value = "from", required = false) String from,
                          @RequestParam(value = "to", required = false) String to,
                          Model model) {
        PeriodReport report = resolve(period, from, to);
        model.addAttribute("report", report);
        model.addAttribute("period", report.getPeriodType().name());
        model.addAttribute("reportEmail", appProperties.getReport().getEmail());
        model.addAttribute("from", report.getFromDate().toString());
        model.addAttribute("to", report.getToDate().toString());
        return "admin/reports";
    }

    @PostMapping("/email")
    public String emailNow(@RequestParam(value = "period", defaultValue = "DAILY") String period,
                           @RequestParam(value = "from", required = false) String from,
                           @RequestParam(value = "to", required = false) String to,
                           RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        PeriodReport report = resolve(period, from, to);
        EmailService.SendResult result = reportService.emailReport(report);
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "REPORT_EMAIL_MANUAL", "REPORT", report.getPeriodType().name(),
                "to=" + appProperties.getReport().getEmail() + " status=" + result.status());
        if (result.success()) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Report emailed to " + appProperties.getReport().getEmail());
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Report email " + result.status() + ": " + result.message());
        }
        return "redirect:/admin/reports?period=" + report.getPeriodType().name()
                + "&from=" + report.getFromDate() + "&to=" + report.getToDate();
    }

    private PeriodReport resolve(String period, String from, String to) {
        String p = period != null ? period.toUpperCase() : "DAILY";
        return switch (p) {
            case "WEEKLY" -> reportService.buildWeekly(LocalDate.now());
            case "MONTHLY" -> reportService.buildMonthly(LocalDate.now());
            case "CUSTOM" -> {
                LocalDate f = from != null && !from.isBlank() ? LocalDate.parse(from) : LocalDate.now().minusDays(7);
                LocalDate t = to != null && !to.isBlank() ? LocalDate.parse(to) : LocalDate.now();
                yield reportService.buildCustom(f, t);
            }
            default -> reportService.buildDaily(LocalDate.now());
        };
    }
}
