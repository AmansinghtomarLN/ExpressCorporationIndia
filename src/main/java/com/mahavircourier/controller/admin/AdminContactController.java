package com.mahavircourier.controller.admin;

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

    public AdminContactController(ContactService contactService) {
        this.contactService = contactService;
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
        redirectAttributes.addFlashAttribute("successMessage", "Message marked as read.");
        return "redirect:/admin/contacts";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        contactService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Message deleted.");
        return "redirect:/admin/contacts";
    }
}
