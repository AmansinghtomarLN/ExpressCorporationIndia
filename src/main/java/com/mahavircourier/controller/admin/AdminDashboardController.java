package com.mahavircourier.controller.admin;

import com.mahavircourier.service.ShipmentService;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final ShipmentService shipmentService;

    public AdminDashboardController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @GetMapping
    public String dashboard(Model model, HttpServletResponse response) {
        preventCaching(response);
        model.addAttribute("stats", shipmentService.countStats());
        model.addAttribute("recentShipments", shipmentService.findRecent(10));
        return "admin/dashboard";
    }

    private void preventCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setDateHeader(HttpHeaders.EXPIRES, 0);
    }
}
