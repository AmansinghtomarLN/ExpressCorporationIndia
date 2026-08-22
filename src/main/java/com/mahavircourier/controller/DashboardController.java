package com.mahavircourier.controller;

import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.ShipmentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class DashboardController {

    private final ShipmentService shipmentService;

    public DashboardController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("user", principal.getUser());
        model.addAttribute("shipments", shipmentService.findByUser(principal.getUser().getId()));
        return "dashboard";
    }

    @GetMapping("/book")
    public String bookPage(Model model) {
        model.addAttribute("bookingDisabled", true);
        return "book";
    }

    @PostMapping("/book")
    public String book(Model model) {
        model.addAttribute("bookingDisabled", true);
        model.addAttribute("errorMessage",
                "Shipments can only be created from a party manifest.");
        return "book";
    }
}
