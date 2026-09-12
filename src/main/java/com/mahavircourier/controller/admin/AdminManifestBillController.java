package com.mahavircourier.controller.admin;

import com.mahavircourier.model.ManifestBill;
import com.mahavircourier.service.AuditService;
import com.mahavircourier.service.BranchService;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.ManifestBillService;
import com.mahavircourier.service.PartyService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin/billing")
public class AdminManifestBillController {

    private final ManifestBillService manifestBillService;
    private final PartyService partyService;
    private final BranchService branchService;
    private final AuditService auditService;

    public AdminManifestBillController(ManifestBillService manifestBillService,
                                       PartyService partyService,
                                       BranchService branchService,
                                       AuditService auditService) {
        this.manifestBillService = manifestBillService;
        this.partyService = partyService;
        this.branchService = branchService;
        this.auditService = auditService;
    }

    @GetMapping("/parties")
    public String partyBills(@RequestParam(required = false) Long partyId,
                             @RequestParam(required = false) Long branchId,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             Model model) {
        populateBills(model, ManifestBill.TYPE_PARTY, partyId, branchId, status, from, to);
        return "admin/billing-parties";
    }

    @GetMapping("/branches")
    public String branchBills(@RequestParam(required = false) Long branchId,
                              @RequestParam(required = false) Long partyId,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              Model model) {
        populateBills(model, ManifestBill.TYPE_BRANCH, partyId, branchId, status, from, to);
        return "admin/billing-branches";
    }

    @PostMapping("/parties/{id}/received")
    public String markPartyReceived(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return updateStatus(id, true, "/admin/billing/parties", redirectAttributes);
    }

    @PostMapping("/parties/{id}/pending")
    public String markPartyPending(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return updateStatus(id, false, "/admin/billing/parties", redirectAttributes);
    }

    @PostMapping("/branches/{id}/received")
    public String markBranchReceived(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return updateStatus(id, true, "/admin/billing/branches", redirectAttributes);
    }

    @PostMapping("/branches/{id}/pending")
    public String markBranchPending(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return updateStatus(id, false, "/admin/billing/branches", redirectAttributes);
    }

    private String updateStatus(Long id, boolean received, String redirect,
                                RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            if (received) {
                manifestBillService.markReceived(id);
            } else {
                manifestBillService.markPending(id);
            }
            String label = received ? "RECEIVED" : "PENDING";
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "BILL_" + label, "MANIFEST_BILL", String.valueOf(id),
                    "Marked " + label);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Bill marked as " + (received ? "received" : "pending") + ".");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + redirect;
    }

    private void populateBills(Model model, String billType, Long partyId, Long branchId,
                               String status, LocalDate from, LocalDate to) {
        String view = resolveView(status);
        String statusFilter = switch (view) {
            case "HISTORY" -> ManifestBill.STATUS_RECEIVED;
            case "ALL" -> null;
            default -> ManifestBill.STATUS_PENDING;
        };
        if (ManifestBill.TYPE_PARTY.equals(billType)) {
            model.addAttribute("bills", manifestBillService.searchPartyBills(
                    partyId, branchId, statusFilter, from, to));
        } else {
            model.addAttribute("bills", manifestBillService.searchBranchBills(
                    branchId, partyId, statusFilter, from, to));
        }
        model.addAttribute("parties", partyService.findEnabled());
        model.addAttribute("branches", branchService.findAll());
        model.addAttribute("partyId", partyId);
        model.addAttribute("branchId", branchId);
        model.addAttribute("status", view);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("pendingCount", manifestBillService.countPending(billType));
        model.addAttribute("receivedCount", manifestBillService.countReceived(billType));
        model.addAttribute("pendingAmount", manifestBillService.sumPending(billType));
        model.addAttribute("receivedAmount", manifestBillService.sumReceived(billType));
    }

    private String resolveView(String status) {
        if (status == null || status.isBlank() || "PENDING".equalsIgnoreCase(status)) {
            return "PENDING";
        }
        if ("HISTORY".equalsIgnoreCase(status) || "RECEIVED".equalsIgnoreCase(status)) {
            return "HISTORY";
        }
        if ("ALL".equalsIgnoreCase(status)) {
            return "ALL";
        }
        return "PENDING";
    }
}
