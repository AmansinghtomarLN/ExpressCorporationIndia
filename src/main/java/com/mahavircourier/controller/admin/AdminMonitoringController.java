package com.mahavircourier.controller.admin;

import com.mahavircourier.service.AuditService;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.MonitorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/monitoring")
public class AdminMonitoringController {

    private final MonitorService monitorService;
    private final AuditService auditService;

    public AdminMonitoringController(MonitorService monitorService, AuditService auditService) {
        this.monitorService = monitorService;
        this.auditService = auditService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("snapshot", monitorService.snapshot());
        return "admin/monitoring";
    }

    @PostMapping("/run-alerts")
    public String runAlerts(RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        monitorService.evaluateAndAlert();
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "MONITOR_ALERT_RUN", "MONITOR", null, "Manual alert evaluation");
        redirectAttributes.addFlashAttribute("successMessage",
                "Alert evaluation completed. Check inbox if thresholds were breached.");
        return "redirect:/admin/monitoring";
    }
}
