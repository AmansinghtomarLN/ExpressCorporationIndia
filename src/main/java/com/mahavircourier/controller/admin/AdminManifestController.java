package com.mahavircourier.controller.admin;

import com.mahavircourier.dto.ManifestForm;
import com.mahavircourier.dto.ManifestItemForm;
import com.mahavircourier.model.Manifest;
import com.mahavircourier.model.ManifestItem;
import com.mahavircourier.service.AuditService;
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
    private final AuditService auditService;

    public AdminManifestController(ManifestService manifestService,
                                   PartyService partyService,
                                   AuditService auditService) {
        this.manifestService = manifestService;
        this.partyService = partyService;
        this.auditService = auditService;
    }

    @GetMapping
    public String list(@RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "partyId", required = false) Long partyId,
                       @RequestParam(value = "from", required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(value = "to", required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       Model model) {
        model.addAttribute("manifests", manifestService.search(q, partyId, from, to));
        model.addAttribute("parties", partyService.findEnabled());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("partyId", partyId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "admin/manifests";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(value = "partyId", required = false) Long partyId, Model model) {
        ManifestForm form = ManifestForm.blank(BLANK_ROWS);
        form.setPartyId(partyId);
        form.setManifestNumber(manifestService.suggestNextNumber());
        model.addAttribute("form", form);
        model.addAttribute("parties", partyService.findEnabled());
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
                    "Manifest " + created.getManifestNumber() + " saved. "
                            + created.getItems().size() + " shipment(s) created as DISPATCHED.");
            return "redirect:/admin/manifests/" + created.getId();
        } catch (IllegalArgumentException ex) {
            form.ensureMinRows(BLANK_ROWS);
            model.addAttribute("form", form);
            model.addAttribute("parties", partyService.findEnabled());
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
                    model.addAttribute("parties", partyService.findEnabled());
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
            model.addAttribute("parties", partyService.findEnabled());
            model.addAttribute("editing", true);
            model.addAttribute("errorMessage", ex.getMessage());
            return "admin/manifest-form";
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
        form.setRemarks(manifest.getRemarks());
        for (ManifestItem item : manifest.getItems()) {
            ManifestItemForm line = new ManifestItemForm();
            line.setId(item.getId());
            line.setConsignmentNo(item.getConsignmentNo());
            line.setDestinationCity(item.getDestinationCity());
            line.setNumberOfBoxes(item.getNumberOfBoxes());
            line.setWeightKg(item.getWeightKg());
            line.setReceiverName(item.getReceiverName());
            line.setReceiverPhone(item.getReceiverPhone());
            form.getItems().add(line);
        }
        form.ensureMinRows(Math.max(BLANK_ROWS, form.getItems().size() + 3));
        return form;
    }
}
