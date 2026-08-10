package com.mahavircourier.controller.admin;

import com.mahavircourier.service.AuditService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/audit")
public class AdminAuditController {

    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public String list(@RequestParam(value = "limit", defaultValue = "100") int limit, Model model) {
        model.addAttribute("logs", auditService.listRecent(limit));
        model.addAttribute("auditLogs", auditService.listRecent(limit));
        return "admin/audit";
    }
}
