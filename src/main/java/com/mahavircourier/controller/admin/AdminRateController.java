package com.mahavircourier.controller.admin;

import com.mahavircourier.model.RateCard;
import com.mahavircourier.service.AuditService;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.RateCardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/admin/rates")
public class AdminRateController {

    private final RateCardService rateCardService;
    private final AuditService auditService;

    public AdminRateController(RateCardService rateCardService, AuditService auditService) {
        this.rateCardService = rateCardService;
        this.auditService = auditService;
    }

    @GetMapping
    public String list(@RequestParam(value = "editId", required = false) Long editId, Model model) {
        model.addAttribute("rates", rateCardService.findAll());
        model.addAttribute("rate", new RateCard());
        if (editId != null) {
            rateCardService.findById(editId).ifPresent(r -> model.addAttribute("editRate", r));
        }
        return "admin/rates";
    }

    @PostMapping
    public String create(@ModelAttribute RateCard rate, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        if (rate.getMinWeightKg() == null) {
            rate.setMinWeightKg(BigDecimal.ZERO);
        }
        if (rate.getMaxWeightKg() == null) {
            rate.setMaxWeightKg(new BigDecimal("999"));
        }
        if (rate.getPerKgRate() == null) {
            rate.setPerKgRate(BigDecimal.ZERO);
        }
        RateCard saved = rateCardService.save(rate);
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "RATE_CREATE", "RATE_CARD", String.valueOf(saved.getId()),
                saved.getServiceType());
        redirectAttributes.addFlashAttribute("successMessage", "Rate card created.");
        return "redirect:/admin/rates";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @ModelAttribute RateCard rate,
                       RedirectAttributes redirectAttributes) {
        return update(id, rate, redirectAttributes);
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id,
                         @ModelAttribute RateCard rate,
                         RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        rate.setId(id);
        rateCardService.update(rate);
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "RATE_UPDATE", "RATE_CARD", String.valueOf(id), rate.getServiceType());
        redirectAttributes.addFlashAttribute("successMessage", "Rate card updated.");
        return "redirect:/admin/rates";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        rateCardService.delete(id);
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "RATE_DELETE", "RATE_CARD", String.valueOf(id), "Deleted rate card");
        redirectAttributes.addFlashAttribute("successMessage", "Rate card deleted.");
        return "redirect:/admin/rates";
    }
}
