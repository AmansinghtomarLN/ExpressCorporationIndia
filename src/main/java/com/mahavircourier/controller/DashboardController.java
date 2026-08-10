package com.mahavircourier.controller;

import com.mahavircourier.dto.BookingForm;
import com.mahavircourier.model.Shipment;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.ShipmentService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
        if (!model.containsAttribute("bookingForm")) {
            model.addAttribute("bookingForm", new BookingForm());
        }
        return "book";
    }

    @PostMapping("/book")
    public String book(@Valid @ModelAttribute("bookingForm") BookingForm form,
                        BindingResult bindingResult,
                        @AuthenticationPrincipal CustomUserDetails principal,
                        Model model) {
        if (bindingResult.hasErrors()) {
            return "book";
        }
        Shipment shipment = shipmentService.bookShipment(form, principal.getUser().getId());
        model.addAttribute("bookedShipment", shipment);
        return "book-success";
    }
}
