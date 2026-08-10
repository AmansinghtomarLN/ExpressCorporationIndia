package com.mahavircourier.controller.admin;

import com.mahavircourier.service.NotificationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/notifications")
public class AdminNotificationController {

    private final NotificationService notificationService;

    public AdminNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public String list(@RequestParam(value = "limit", defaultValue = "100") int limit, Model model) {
        model.addAttribute("notifications", notificationService.listRecent(limit));
        return "admin/notifications";
    }
}
