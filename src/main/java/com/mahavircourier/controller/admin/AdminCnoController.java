package com.mahavircourier.controller.admin;

import com.mahavircourier.model.ConsignmentRange;
import com.mahavircourier.model.CnoSettings;
import com.mahavircourier.service.AuditService;
import com.mahavircourier.service.CnoInventoryService;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.PartyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/cno-ranges")
public class AdminCnoController {

    private final CnoInventoryService cnoInventoryService;
    private final PartyService partyService;
    private final AuditService auditService;

    public AdminCnoController(CnoInventoryService cnoInventoryService,
                              PartyService partyService,
                              AuditService auditService) {
        this.cnoInventoryService = cnoInventoryService;
        this.partyService = partyService;
        this.auditService = auditService;
    }

    @GetMapping
    public String page(Model model) {
        CnoSettings settings = cnoInventoryService.settings();
        model.addAttribute("settings", settings);
        model.addAttribute("parties", partyService.findEnabled());
        model.addAttribute("summaries", cnoInventoryService.partySummaries());
        model.addAttribute("allocations", cnoInventoryService.allAllocations());
        try {
            long[] next = cnoInventoryService.previewNextBucket(null);
            model.addAttribute("nextStart", next[0]);
            model.addAttribute("nextEnd", next[1]);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("nextError", ex.getMessage());
        }
        return "admin/cno-ranges";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam long seriesStart,
                               @RequestParam long seriesEnd,
                               @RequestParam int bucketSize,
                               RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            cnoInventoryService.updateSettings(seriesStart, seriesEnd, bucketSize);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "CNO_SETTINGS", "CNO_SETTINGS", "1",
                    "Series " + seriesStart + "-" + seriesEnd + ", bucket " + bucketSize);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Bucket size and series saved.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/cno-ranges";
    }

    @PostMapping("/allocate")
    public String allocate(@RequestParam Long partyId,
                           @RequestParam int bucketCount,
                           @RequestParam(required = false) Integer bucketSize,
                           @RequestParam(required = false) String notes,
                           RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            List<ConsignmentRange> created = cnoInventoryService.allocateBuckets(
                    partyId, bucketCount, bucketSize, notes);
            StringBuilder detail = new StringBuilder("Allocated ");
            for (ConsignmentRange range : created) {
                detail.append(range.getRangeStart()).append("-").append(range.getRangeEnd()).append(" ");
            }
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "CNO_BUCKET_ALLOCATE", "PARTY", String.valueOf(partyId), detail.toString().trim());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Allocated " + created.size() + " bucket(s): " + detail);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/cno-ranges";
    }

    @PostMapping("/{partyId}/ranges/{rangeId}/delete")
    public String deleteRange(@PathVariable Long partyId,
                              @PathVariable Long rangeId,
                              RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            partyService.deleteRange(partyId, rangeId);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "CNO_RANGE_DELETE", "PARTY", String.valueOf(partyId), "Deleted range " + rangeId);
            redirectAttributes.addFlashAttribute("successMessage", "Range removed.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/cno-ranges";
    }
}
