package com.mahavircourier.controller;

import com.mahavircourier.dto.BookingForm;
import com.mahavircourier.model.Shipment;
import com.mahavircourier.service.BranchService;
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
    private final BranchService branchService;

    public DashboardController(ShipmentService shipmentService,
                               BranchService branchService) {
        this.shipmentService = shipmentService;
        this.branchService = branchService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("user", principal.getUser());
        model.addAttribute("shipments", shipmentService.findByUser(principal.getUser().getId()));
        return "dashboard";
    }

    @GetMapping("/book")
    public String bookPage(Model model) {
        model.addAttribute("form", new BookingForm());
        model.addAttribute("branches", branchService.findAll());
        return "book";
    }

    @PostMapping("/book")
    public String book(@Valid @ModelAttribute("form") BookingForm form,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal CustomUserDetails principal,
                       Model model) {
        model.addAttribute("branches", branchService.findAll());
        if (bindingResult.hasErrors()) {
            return "book";
        }
        try {
            Shipment booked = shipmentService.bookShipment(form, principal.getUser().getId());
            model.addAttribute("bookedShipment", booked);
            return "book-success";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "book";
        }
    }
}
