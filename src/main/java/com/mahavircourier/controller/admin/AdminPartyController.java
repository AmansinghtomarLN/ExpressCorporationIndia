package com.mahavircourier.controller.admin;

import com.mahavircourier.model.Party;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.ManifestService;
import com.mahavircourier.service.PartyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/parties")
public class AdminPartyController {

    private final PartyService partyService;
    private final ManifestService manifestService;

    public AdminPartyController(PartyService partyService,
                                ManifestService manifestService
                                ) {
        this.partyService = partyService;
        this.manifestService = manifestService;
    }

    @GetMapping
    public String list(@RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "editId", required = false) Long editId,
                       Model model) {
        model.addAttribute("parties", partyService.search(q));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("party", new Party());
        if (editId != null) {
            partyService.findById(editId).ifPresent(p -> model.addAttribute("editParty", p));
        }
        return "admin/parties";
    }

    @PostMapping
    public String create(@ModelAttribute Party party, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            Party created = partyService.create(party);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Party created. Next assign a consignment number range.");
            return "redirect:/admin/parties/" + created.getId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/parties";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        return partyService.findById(id)
                .map(party -> {
                    model.addAttribute("party", party);
                    model.addAttribute("manifests", manifestService.search(null, id, null, null));
                    return "admin/party-detail";
                })
                .orElse("redirect:/admin/parties");
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @ModelAttribute Party party,
                       RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            party.setId(id);
            partyService.update(party);
            redirectAttributes.addFlashAttribute("successMessage", "Party updated.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/parties/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            String name = partyService.findById(id).map(Party::getPartyName).orElse(String.valueOf(id));
            partyService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Party deleted.");
            return "redirect:/admin/parties";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/parties/" + id;
        }
    }

    @PostMapping("/{id}/ranges")
    public String addRange(@PathVariable Long id,
                           @RequestParam long rangeStart,
                           @RequestParam long rangeEnd,
                           @RequestParam(required = false) String notes,
                           RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            partyService.addRange(id, rangeStart, rangeEnd, notes);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Consignment range " + rangeStart + "–" + rangeEnd + " assigned.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/parties/" + id;
    }

    @PostMapping("/{id}/ranges/{rangeId}/delete")
    public String deleteRange(@PathVariable Long id,
                              @PathVariable Long rangeId,
                              RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            partyService.deleteRange(id, rangeId);
            redirectAttributes.addFlashAttribute("successMessage", "Consignment range removed.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/parties/" + id;
    }
}
