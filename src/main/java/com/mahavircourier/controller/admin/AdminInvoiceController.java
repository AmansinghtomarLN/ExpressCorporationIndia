package com.mahavircourier.controller.admin;

import com.mahavircourier.service.AuditService;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.InvoiceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/invoices")
public class AdminInvoiceController {

    private final InvoiceService invoiceService;
    private final AuditService auditService;

    public AdminInvoiceController(InvoiceService invoiceService, AuditService auditService) {
        this.invoiceService = invoiceService;
        this.auditService = auditService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("invoices", invoiceService.list());
        return "admin/invoices";
    }

    @PostMapping("/{id}/paid")
    public String markPaid(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        invoiceService.markPaid(id);
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "INVOICE_PAID", "INVOICE", String.valueOf(id), "Marked PAID");
        redirectAttributes.addFlashAttribute("successMessage", "Invoice marked as paid.");
        return "redirect:/admin/invoices";
    }

    @PostMapping("/{id}/unpaid")
    public String markUnpaid(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        invoiceService.markUnpaid(id);
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "INVOICE_UNPAID", "INVOICE", String.valueOf(id), "Marked UNPAID");
        redirectAttributes.addFlashAttribute("successMessage", "Invoice marked as unpaid.");
        return "redirect:/admin/invoices";
    }

    @PostMapping("/{id}/cod-collected")
    public String markCodCollected(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        invoiceService.markCodCollected(id);
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "INVOICE_COD", "INVOICE", String.valueOf(id), "Marked COD_COLLECTED");
        redirectAttributes.addFlashAttribute("successMessage", "COD marked as collected.");
        return "redirect:/admin/invoices";
    }
}
