package com.mahavircourier.controller;

import com.mahavircourier.model.Shipment;
import com.mahavircourier.service.ShipmentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final List<String> STATUSES = List.of(
            "BOOKED", "PICKED_UP", "IN_TRANSIT", "AT_HUB", "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED", "RTO"
    );

    private final ShipmentService shipmentService;

    public AdminController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @GetMapping
    public String adminHome(Model model) {
        model.addAttribute("shipments", shipmentService.findAll());
        return "admin/shipments";
    }

    @GetMapping("/shipments/{id}")
    public String shipmentDetail(@PathVariable Long id, Model model) {
        Optional<Shipment> shipmentOpt = shipmentService.findById(id);
        if (shipmentOpt.isEmpty()) {
            return "redirect:/admin";
        }
        model.addAttribute("shipment", shipmentOpt.get());
        model.addAttribute("statuses", STATUSES);
        return "admin/shipment-detail";
    }

    @PostMapping("/shipments/{id}/update")
    public String addUpdate(@PathVariable Long id,
                             @RequestParam String status,
                             @RequestParam String location,
                             @RequestParam(required = false) String remarks) {
        shipmentService.addTrackingUpdate(id, status, location, remarks);
        return "redirect:/admin/shipments/" + id;
    }
}
