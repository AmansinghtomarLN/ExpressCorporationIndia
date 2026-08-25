package com.mahavircourier.controller.admin;

import com.mahavircourier.dto.FreightQuote;
import com.mahavircourier.model.BillingTariff;
import com.mahavircourier.service.AuditService;
import com.mahavircourier.service.BranchCategory;
import com.mahavircourier.service.BranchService;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.RateCardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/admin/billing")
public class AdminBillingController {

    private final RateCardService rateCardService;
    private final BranchService branchService;
    private final AuditService auditService;

    public AdminBillingController(RateCardService rateCardService,
                                  BranchService branchService,
                                  AuditService auditService) {
        this.rateCardService = rateCardService;
        this.branchService = branchService;
        this.auditService = auditService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("tariffs", rateCardService.findTariffs());
        model.addAttribute("domestic", rateCardService.findTariff(BranchCategory.DOMESTIC)
                .orElseGet(() -> blank(BranchCategory.DOMESTIC)));
        model.addAttribute("national", rateCardService.findTariff(BranchCategory.NATIONAL)
                .orElseGet(() -> blank(BranchCategory.NATIONAL)));
        model.addAttribute("branches", branchService.findAll());
        return "admin/billing";
    }

    @PostMapping("/{lane}")
    public String save(@PathVariable String lane,
                       @ModelAttribute BillingTariff tariff,
                       RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            tariff.setLaneType(lane);
            rateCardService.saveTariff(tariff);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "BILLING_TARIFF_UPDATE", "BILLING_TARIFF", lane,
                    "Updated " + lane + " branch default rates");
            redirectAttributes.addFlashAttribute("successMessage",
                    BranchCategory.displayName(lane)
                            + " defaults saved and applied to every branch in that category.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/billing";
    }

    @PostMapping("/branches/{id}")
    public String saveBranchRates(@PathVariable Long id,
                                  @RequestParam BigDecimal perKgRate,
                                  @RequestParam BigDecimal perBoxRate,
                                  RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            rateCardService.saveBranchRates(id, perKgRate, perBoxRate);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "BRANCH_RATES_UPDATE", "BRANCH", String.valueOf(id),
                    "per kg=" + perKgRate + ", per box=" + perBoxRate);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Branch rates saved. New invoices for that destination will use these prices.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/billing";
    }

    @GetMapping("/preview")
    @ResponseBody
    public String preview(@RequestParam(required = false) String lane,
                          @RequestParam(required = false) Long branchId,
                          @RequestParam(required = false) String city,
                          @RequestParam(required = false) BigDecimal weight,
                          @RequestParam(required = false) Integer boxes) {
        FreightQuote quote;
        if (branchId != null) {
            quote = rateCardService.quoteBranch(branchId, weight, boxes);
        } else if (city != null && !city.isBlank()) {
            quote = rateCardService.quote(city, lane != null ? lane : BranchCategory.AUTO, weight, boxes);
        } else {
            quote = rateCardService.quote(null, lane != null ? lane : BranchCategory.DOMESTIC, weight, boxes);
        }
        return quote.getAmount().toPlainString();
    }

    private BillingTariff blank(String lane) {
        BillingTariff tariff = new BillingTariff();
        tariff.setLaneType(lane);
        return tariff;
    }
}
