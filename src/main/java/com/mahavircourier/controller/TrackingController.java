package com.mahavircourier.controller;

import com.mahavircourier.model.Shipment;
import com.mahavircourier.service.ShipmentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.Optional;

@Controller
public class TrackingController {

    private final ShipmentService shipmentService;

    public TrackingController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    /** Tracking page rendered from the homepage widget or the nav "Track" link. */
    @GetMapping("/track")
    public String trackPage(@RequestParam(value = "trackingId", required = false) String trackingId,
                             Model model) {
        model.addAttribute("trackingId", trackingId);

        if (StringUtils.hasText(trackingId)) {
            Optional<Shipment> shipmentOpt = shipmentService.trackByTrackingId(trackingId);
            if (shipmentOpt.isPresent()) {
                model.addAttribute("shipment", shipmentOpt.get());
            } else {
                model.addAttribute("notFound", true);
            }
        }
        return "track";
    }

    /** JSON API for AJAX tracking (used by the homepage quick-track widget). */
    @GetMapping(value = "/api/track/{trackingId}", produces = "application/json")
    @ResponseBody
    public Map<String, Object> trackApi(@org.springframework.web.bind.annotation.PathVariable String trackingId) {
        Optional<Shipment> shipmentOpt = shipmentService.trackByTrackingId(trackingId);
        if (shipmentOpt.isEmpty()) {
            return Map.of("found", false);
        }
        return Map.of("found", true, "shipment", shipmentOpt.get());
    }
}
