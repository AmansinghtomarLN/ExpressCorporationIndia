package com.mahavircourier.controller.admin;

import com.mahavircourier.service.AuditService;
import com.mahavircourier.service.ContactService;
import com.mahavircourier.service.CustomUserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/contacts")
public class AdminContactController {

    private final ContactService contactService;
    private final AuditService auditService;

    public AdminContactController(ContactService contactService, AuditService auditService) {
        this.contactService = contactService;
        this.auditService = auditService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("contacts", contactService.list());
        model.addAttribute("unreadCount", contactService.countUnread());
        return "admin/contacts";
    }

    @PostMapping("/{id}/read")
    public String markRead(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        contactService.markRead(id);
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "CONTACT_READ", "CONTACT", String.valueOf(id), "Marked as read");
        redirectAttributes.addFlashAttribute("successMessage", "Message marked as read.");
        return "redirect:/admin/contacts";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        contactService.delete(id);
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "CONTACT_DELETE", "CONTACT", String.valueOf(id), "Deleted contact message");
        redirectAttributes.addFlashAttribute("successMessage", "Message deleted.");
        return "redirect:/admin/contacts";
    }
}
