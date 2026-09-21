package com.mahavircourier.controller.admin;

import com.mahavircourier.dto.AdminShipmentForm;
import com.mahavircourier.dto.PageResult;
import com.mahavircourier.model.Shipment;
import com.mahavircourier.service.AuditService;
import com.mahavircourier.service.BranchService;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.ManifestService;
import com.mahavircourier.service.PartyService;
import com.mahavircourier.service.ShipmentService;
import com.mahavircourier.service.WorkspaceService;
import com.mahavircourier.service.StatusTransitions;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/shipments")
public class AdminShipmentController {

    private static final List<String> STATUSES = StatusTransitions.ALL_STATUSES;
    private static final List<Integer> PAGE_SIZE_OPTIONS = List.of(5, 10, 25, 50, 100);
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final ShipmentService shipmentService;
    private final BranchService branchService;
    private final PartyService partyService;
    private final ManifestService manifestService;
    private final AuditService auditService;
    private final WorkspaceService workspaceService;

    public AdminShipmentController(ShipmentService shipmentService,
                                   BranchService branchService,
                                   PartyService partyService,
                                   ManifestService manifestService,
                                   AuditService auditService,
                                   WorkspaceService workspaceService) {
        this.shipmentService = shipmentService;
        this.branchService = branchService;
        this.partyService = partyService;
        this.manifestService = manifestService;
        this.auditService = auditService;
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public String list(@RequestParam(value = "q", required = false) String q,
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

    @GetMapping("/new")
    public String newForm(@RequestParam(value = "branchId", required = false) Long branchId,
                          @RequestParam(value = "manifestId", required = false) Long manifestId,
                          Model model) {
        AdminShipmentForm form = new AdminShipmentForm();
        try {
            form.setOriginCity(workspaceService.resolveCurrent(AdminAuth.requirePrincipal().getUser()).getCity());
        } catch (IllegalArgumentException ignored) {
            form.setOriginCity(WorkspaceService.DEFAULT_CITY);
        }
        if (branchId != null) {
            form.setDestinationBranchId(branchId);
        } else if (manifestId != null) {
            manifestService.findById(manifestId).ifPresent(m -> {
                form.setDestinationBranchId(m.getDestinationBranchId());
                form.setPartyId(m.getPartyId());
            });
        }
        model.addAttribute("form", form);
        model.addAttribute("manifestId", manifestId);
        model.addAttribute("lockDestination", form.getDestinationBranchId() != null);
        populateBookingLookups(model);
        return "admin/shipment-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") AdminShipmentForm form,
                         BindingResult bindingResult,
                         @RequestParam(value = "manifestId", required = false) Long manifestId,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("manifestId", manifestId);
            model.addAttribute("lockDestination", form.getDestinationBranchId() != null && manifestId != null);
            populateBookingLookups(model);
            return "admin/shipment-form";
        }
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            Shipment created = shipmentService.adminCreateShipment(form, principal.getUser().getId());
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "SHIPMENT_BOOK", "SHIPMENT", String.valueOf(created.getId()),
                    "C.No " + created.getTrackingId() + " booked");
            redirectAttributes.addFlashAttribute("successMessage",
                    "Shipment " + created.getTrackingId() + " booked. Add it to a manifest when you create or update one.");
            return "redirect:/admin/shipments/" + created.getId();
        } catch (IllegalArgumentException ex) {
            model.addAttribute("manifestId", manifestId);
            model.addAttribute("lockDestination", form.getDestinationBranchId() != null && manifestId != null);
            populateBookingLookups(model);
            model.addAttribute("errorMessage", ex.getMessage());
            return "admin/shipment-form";
        }
    }

    private void populateBookingLookups(Model model) {
        model.addAttribute("branches", branchService.findAll());
        model.addAttribute("parties", partyService.findEnabled());
    }

    @GetMapping("/export.csv")
    public void exportCsv(HttpServletResponse response) throws IOException {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        String csv = shipmentService.exportCsv();
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "SHIPMENT_EXPORT", "SHIPMENT", null, "Exported shipments CSV");
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"shipments.csv\"");
        response.getWriter().write(csv);
    }

    @GetMapping("/bulk")
    public String bulkForm() {
        return "admin/bulk";
    }

    @PostMapping("/bulk")
    public String bulkUpload(@RequestParam("csvText") String csvText,
                             RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        List<String> lines = Arrays.asList(csvText != null ? csvText.split("\\R") : new String[0]);
        int updated = shipmentService.bulkUpdateFromCsv(lines);
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "SHIPMENT_BULK", "SHIPMENT", null, "Bulk updated " + updated + " rows");
        redirectAttributes.addFlashAttribute("successMessage",
                "Bulk update complete. Updated " + updated + " shipment(s).");
        return "redirect:/admin/shipments";
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@PathVariable Long id, Model model, HttpServletResponse response) {
        preventCaching(response);
        Optional<Shipment> shipmentOpt = shipmentService.findById(id);
        if (shipmentOpt.isEmpty()) {
            return "redirect:/admin/shipments";
        }
        Shipment shipment = shipmentOpt.get();
        model.addAttribute("shipment", shipment);
        model.addAttribute("statuses", STATUSES);
        model.addAttribute("branches", branchService.findAll());
        return "admin/shipment-detail";
    }

    @PostMapping("/{id:\\d+}/update")
    public String addUpdate(@PathVariable Long id,
                            @RequestParam String status,
                            @RequestParam String location,
                            @RequestParam(required = false) String remarks,
                            @RequestParam(value = "returnTo", required = false) String returnTo,
                            RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            shipmentService.addTrackingUpdate(id, status, location, remarks);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "TRACKING_UPDATE", "SHIPMENT", String.valueOf(id),
                    "Status -> " + status + " @ " + location);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Tracking updated to " + status.replace('_', ' ') + ".");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        if ("track".equalsIgnoreCase(returnTo)) {
            shipmentService.findById(id).ifPresent(s ->
                    redirectAttributes.addAttribute("trackingId", s.getTrackingId()));
            return "redirect:/track";
        }
        return "redirect:/admin/shipments/" + id;
    }

    @PostMapping("/{id:\\d+}/edit")
    public String editDetails(@PathVariable Long id,
                              @RequestParam(required = false) String senderName,
                              @RequestParam(required = false) String senderPhone,
                              @RequestParam(required = false) String senderAddress,
                              @RequestParam(required = false) String receiverName,
                              @RequestParam(required = false) String receiverPhone,
                              @RequestParam(required = false) String receiverAddress,
                              @RequestParam(required = false) String originCity,
                              @RequestParam(required = false) String destinationCity,
                              @RequestParam(required = false) BigDecimal weightKg,
                              @RequestParam(required = false) String serviceType,
                              @RequestParam(required = false) String expectedDelivery,
                              @RequestParam(required = false) BigDecimal freightCharge,
                              @RequestParam(required = false) BigDecimal codAmount,
                              RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            Shipment fields = new Shipment();
            fields.setId(id);
            fields.setSenderName(senderName);
            fields.setSenderPhone(senderPhone);
            fields.setSenderAddress(senderAddress);
            fields.setReceiverName(receiverName);
            fields.setReceiverPhone(receiverPhone);
            fields.setReceiverAddress(receiverAddress);
            fields.setOriginCity(originCity);
            fields.setDestinationCity(destinationCity);
            fields.setWeightKg(weightKg);
            fields.setServiceType(serviceType);
            if (expectedDelivery != null && !expectedDelivery.isBlank()) {
                fields.setExpectedDelivery(LocalDate.parse(expectedDelivery));
            }
            fields.setFreightCharge(freightCharge);
            fields.setCodAmount(codAmount);
            shipmentService.updateShipmentDetails(fields);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "SHIPMENT_EDIT", "SHIPMENT", String.valueOf(id), "Updated shipment details");
            redirectAttributes.addFlashAttribute("successMessage", "Shipment details updated.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/shipments/" + id;
    }

    @PostMapping("/{id:\\d+}/assign")
    public String assign(@PathVariable Long id,
                         @RequestParam(required = false) Long assignedBranchId,
                         @RequestParam(required = false) String assignedHub,
                         @RequestParam(required = false) String courierName,
                         @RequestParam(required = false) String courierPhone,
                         RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            shipmentService.assignResources(id, assignedBranchId, assignedHub, courierName, courierPhone);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "SHIPMENT_ASSIGN", "SHIPMENT", String.valueOf(id),
                    "Assigned branch=" + assignedBranchId + ", hub=" + assignedHub);
            redirectAttributes.addFlashAttribute("successMessage", "Resources assigned.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/shipments/" + id;
    }

    @PostMapping("/{id:\\d+}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            shipmentService.cancelShipment(id);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "SHIPMENT_CANCEL", "SHIPMENT", String.valueOf(id), "Cancelled shipment");
            redirectAttributes.addFlashAttribute("successMessage", "Shipment cancelled.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/shipments/" + id;
    }

    @PostMapping("/{id:\\d+}/events/{eventId:\\d+}/edit")
    public String editEvent(@PathVariable Long id,
                            @PathVariable Long eventId,
                            @RequestParam String status,
                            @RequestParam String location,
                            @RequestParam(required = false) String remarks,
                            RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            shipmentService.updateTrackingEvent(id, eventId, status, location, remarks);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "EVENT_EDIT", "TRACKING_EVENT", String.valueOf(eventId),
                    "Edited event on shipment " + id);
            redirectAttributes.addFlashAttribute("successMessage", "Tracking event updated.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/shipments/" + id;
    }

    @PostMapping("/{id:\\d+}/events/{eventId:\\d+}/delete")
    public String deleteEvent(@PathVariable Long id,
                              @PathVariable Long eventId,
                              RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            shipmentService.deleteTrackingEvent(id, eventId);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "EVENT_DELETE", "TRACKING_EVENT", String.valueOf(eventId),
                    "Deleted event on shipment " + id);
            redirectAttributes.addFlashAttribute("successMessage", "Tracking event deleted.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/shipments/" + id;
    }

    @GetMapping("/{id:\\d+}/label")
    public String label(@PathVariable Long id, Model model) {
        Optional<Shipment> shipmentOpt = shipmentService.findById(id);
        if (shipmentOpt.isEmpty()) {
            return "redirect:/admin/shipments";
        }
        model.addAttribute("shipment", shipmentOpt.get());
        return "admin/shipment-label";
    }

    private void preventCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setDateHeader(HttpHeaders.EXPIRES, 0);
    }
}
