package com.mahavircourier.controller.admin;

import com.mahavircourier.model.ConsignmentRange;
import com.mahavircourier.service.CnoInventoryService;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.PartyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/cno-map")
public class AdminCnoMapController {

    private final CnoInventoryService cnoInventoryService;
    private final PartyService partyService;

    public AdminCnoMapController(CnoInventoryService cnoInventoryService,
                                 PartyService partyService) {
        this.cnoInventoryService = cnoInventoryService;
        this.partyService = partyService;
    }

    @GetMapping
    public String page(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("q", q);
        model.addAttribute("parties", partyService.findEnabled());
        model.addAttribute("assignments", cnoInventoryService.search(q));
        return "admin/cno-map";
    }

    @PostMapping
    public String assign(@RequestParam Long partyId,
                         @RequestParam long rangeStart,
                         @RequestParam(required = false) Long rangeEnd,
                         @RequestParam(required = false) String notes,
                         @RequestParam(required = false) String q,
                         RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        long end = rangeEnd != null ? rangeEnd : rangeStart;
        try {
            ConsignmentRange created = cnoInventoryService.assign(partyId, rangeStart, end, notes);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Assigned " + created.getRangeStart()
                            + (created.getRangeEnd() == created.getRangeStart() ? "" : "–" + created.getRangeEnd())
                            + " to " + created.getPartyName() + ".");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect(q, redirectAttributes);
    }

    @PostMapping("/{id}/party")
    public String reassign(@PathVariable Long id,
                           @RequestParam Long partyId,
                           @RequestParam(required = false) String q,
                           RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            cnoInventoryService.reassign(id, partyId);
            redirectAttributes.addFlashAttribute("successMessage", "Party mapping updated. Bills use this party.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect(q, redirectAttributes);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam(required = false) String q,
                         RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            var range = cnoInventoryService.allAllocations().stream()
                    .filter(r -> id.equals(r.getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
            partyService.deleteRange(range.getPartyId(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Mapping removed.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect(q, redirectAttributes);
    }

    private String redirect(String q, RedirectAttributes redirectAttributes) {
        if (q != null && !q.isBlank()) {
            redirectAttributes.addAttribute("q", q);
        }
        return "redirect:/admin/cno-map";
    }
}
