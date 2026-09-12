package com.mahavircourier.controller.admin;

import com.mahavircourier.dto.ManifestForm;
import com.mahavircourier.dto.ManifestItemForm;
import com.mahavircourier.model.Manifest;
import com.mahavircourier.model.ManifestItem;
import com.mahavircourier.service.AuditService;
import com.mahavircourier.service.BranchService;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.ManifestService;
import com.mahavircourier.service.PartyService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/manifests")
public class AdminManifestController {

    private static final int BLANK_ROWS = 20;

    private final ManifestService manifestService;
    private final PartyService partyService;
    private final BranchService branchService;
    private final AuditService auditService;

    public AdminManifestController(ManifestService manifestService,
                                   PartyService partyService,
                                   BranchService branchService,
                                   AuditService auditService) {
        this.manifestService = manifestService;
        this.partyService = partyService;
        this.branchService = branchService;
        this.auditService = auditService;
    }

    @GetMapping
    public String list(@RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "partyId", required = false) Long partyId,
                       @RequestParam(value = "branchId", required = false) Long branchId,
                       @RequestParam(value = "status", required = false) String status,
                       @RequestParam(value = "from", required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(value = "to", required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       Model model) {
        String statusFilter = status == null || status.isBlank() ? "ALL" : status.trim();
        if ("ALL".equalsIgnoreCase(statusFilter)) {
            statusFilter = null;
        } else if ("SUBMITTED".equalsIgnoreCase(statusFilter)) {
            statusFilter = Manifest.STATUS_CREATED;
        }
        model.addAttribute("manifests", manifestService.search(q, partyId, from, to, statusFilter, branchId));
        model.addAttribute("parties", partyService.findEnabled());
        model.addAttribute("branches", branchService.findAll());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("partyId", partyId);
        model.addAttribute("branchId", branchId);
        model.addAttribute("status", status == null || status.isBlank() ? "ALL" : status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("inProgressCount", manifestService.countInProgress());
        model.addAttribute("createdCount", manifestService.countCreated());
        return "admin/manifests";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(value = "partyId", required = false) Long partyId, Model model) {
        ManifestForm form = ManifestForm.blank(BLANK_ROWS);
        form.setPartyId(partyId);
        form.setManifestNumber(manifestService.suggestNextNumber());
        model.addAttribute("form", form);
        populateManifestLookups(model);
        return "admin/manifest-form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") ManifestForm form,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            Manifest created = manifestService.create(form, principal.getUser().getId());
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "MANIFEST_CREATE", "MANIFEST", String.valueOf(created.getId()),
                    "MF " + created.getManifestNumber() + " — " + created.getItems().size() + " consignments dispatched");
            redirectAttributes.addFlashAttribute("successMessage",
                    "In-progress manifest " + created.getManifestNumber() + " saved. "
                            + created.getItems().size() + " consignment(s) ready to create.");
            return "redirect:/admin/manifests/" + created.getId();
        } catch (IllegalArgumentException ex) {
            form.ensureMinRows(BLANK_ROWS);
            model.addAttribute("form", form);
            populateManifestLookups(model);
            model.addAttribute("errorMessage", ex.getMessage());
            return "admin/manifest-form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        return manifestService.findById(id)
                .map(manifest -> {
                    model.addAttribute("manifest", manifest);
                    return "admin/manifest-detail";
                })
                .orElse("redirect:/admin/manifests");
    }

    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id, Model model) {
        return manifestService.findById(id)
                .map(manifest -> {
                    model.addAttribute("manifest", manifest);
                    return "admin/manifest-print";
                })
                .orElse("redirect:/admin/manifests");
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        return manifestService.findById(id)
                .map(manifest -> {
                    model.addAttribute("form", toForm(manifest));
                    populateManifestLookups(model);
                    model.addAttribute("editing", true);
                    return "admin/manifest-form";
                })
                .orElse("redirect:/admin/manifests");
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("form") ManifestForm form,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            Manifest updated = manifestService.update(id, form);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "MANIFEST_UPDATE", "MANIFEST", String.valueOf(id),
                    "Updated MF " + updated.getManifestNumber());
            redirectAttributes.addFlashAttribute("successMessage", "Manifest updated.");
            return "redirect:/admin/manifests/" + id;
        } catch (IllegalArgumentException ex) {
            form.setId(id);
            form.ensureMinRows(BLANK_ROWS);
            model.addAttribute("form", form);
            populateManifestLookups(model);
            model.addAttribute("editing", true);
            model.addAttribute("errorMessage", ex.getMessage());
            return "admin/manifest-form";
        }
    }

    @PostMapping("/{id}/finalize")
    public String finalize(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            Manifest created = manifestService.finalize(id);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "MANIFEST_CREATE", "MANIFEST", String.valueOf(id),
                    "Submitted MF " + created.getManifestNumber());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Manifest " + created.getManifestNumber() + " submitted. "
                            + created.getItems().size() + " shipment(s) dispatched and invoiced.");
            return "redirect:/admin/manifests/" + id;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/manifests/" + id;
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            String number = manifestService.findById(id)
                    .map(Manifest::getManifestNumber).orElse(String.valueOf(id));
            manifestService.delete(id);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "MANIFEST_DELETE", "MANIFEST", String.valueOf(id), "Deleted MF " + number);
            redirectAttributes.addFlashAttribute("successMessage", "Manifest deleted.");
            return "redirect:/admin/manifests";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/manifests/" + id;
        }
    }

    @GetMapping(value = "/party-pool", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Object partyPool(@RequestParam Long partyId) {
        try {
            return partyService.buildPool(partyId);
        } catch (IllegalArgumentException ex) {
            return Map.of("ok", false, "message", ex.getMessage());
        }
    }

    @GetMapping(value = "/check-cno", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> checkCno(@RequestParam Long partyId, @RequestParam String cno) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            result.put("ok", true);
            result.put("message", manifestService.validateConsignment(partyId, cno));
        } catch (IllegalArgumentException ex) {
            result.put("ok", false);
            result.put("message", ex.getMessage());
        }
        return result;
    }

    private ManifestForm toForm(Manifest manifest) {
        ManifestForm form = new ManifestForm();
        form.setId(manifest.getId());
        form.setManifestNumber(manifest.getManifestNumber());
        form.setPartyId(manifest.getPartyId());
        form.setManifestDate(manifest.getManifestDate());
        form.setThroughName(manifest.getThroughName());
        form.setOriginCity(manifest.getOriginCity());
        form.setServiceType(manifest.getServiceType());
        form.setBillingLane(manifest.getBillingLane());
        form.setDestinationBranchId(manifest.getDestinationBranchId());
        form.setRemarks(manifest.getRemarks());
        for (ManifestItem item : manifest.getItems()) {
            ManifestItemForm line = new ManifestItemForm();
            line.setId(item.getId());
            line.setConsignmentNo(item.getConsignmentNo());
            line.setDestinationCity(item.getDestinationCity());
            branchService.findAll().stream()
                    .filter(b -> item.getDestinationCity() != null
                            && item.getDestinationCity().equalsIgnoreCase(b.getCity()))
                    .findFirst()
                    .ifPresent(b -> line.setDestinationBranchId(b.getId()));
            line.setNumberOfBoxes(item.getNumberOfBoxes());
            line.setWeightKg(item.getWeightKg());
            line.setReceiverName(item.getReceiverName());
            line.setReceiverPhone(item.getReceiverPhone());
            form.getItems().add(line);
        }
        form.ensureMinRows(Math.max(BLANK_ROWS, form.getItems().size() + 3));
        return form;
    }

    private void populateManifestLookups(Model model) {
        model.addAttribute("parties", partyService.findEnabled());
        model.addAttribute("branches", branchService.findAll());
    }
}
