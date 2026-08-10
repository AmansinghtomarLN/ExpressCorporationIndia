package com.mahavircourier.controller;

import com.mahavircourier.dto.PageResult;
import com.mahavircourier.model.Shipment;
import com.mahavircourier.service.ShipmentService;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final List<String> STATUSES = List.of(
            "BOOKED", "PICKED_UP", "IN_TRANSIT", "AT_HUB", "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED", "RTO"
    );

    private static final List<Integer> PAGE_SIZE_OPTIONS = List.of(5, 10, 25, 50, 100);
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final ShipmentService shipmentService;

    public AdminController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @GetMapping
    public String adminHome(@RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "status", required = false) String status,
                            @RequestParam(value = "page", defaultValue = "1") int page,
                            @RequestParam(value = "size", defaultValue = "10") int size,
                            Model model,
                            HttpServletResponse response) {
        preventCaching(response);

        int safeSize = PAGE_SIZE_OPTIONS.contains(size) ? size : DEFAULT_PAGE_SIZE;
        PageResult<Shipment> result = shipmentService.searchForAdmin(q, status, page, safeSize);

        model.addAttribute("shipments", result.getContent());
        model.addAttribute("pageResult", result);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("statusFilter", status == null ? "" : status);
        model.addAttribute("statuses", STATUSES);
        model.addAttribute("pageSizeOptions", PAGE_SIZE_OPTIONS);
        return "admin/shipments";
    }

    @GetMapping("/shipments/{id}")
    public String shipmentDetail(@PathVariable Long id, Model model, HttpServletResponse response) {
        preventCaching(response);

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
                             @RequestParam(required = false) String remarks,
                             RedirectAttributes redirectAttributes) {
        shipmentService.addTrackingUpdate(id, status, location, remarks);
        redirectAttributes.addFlashAttribute("successMessage",
                "Tracking updated to " + status.replace('_', ' ') + ".");
        return "redirect:/admin/shipments/" + id;
    }

    private void preventCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setDateHeader(HttpHeaders.EXPIRES, 0);
    }
}
