package com.mahavircourier.controller.admin;

import com.mahavircourier.model.User;
import com.mahavircourier.service.AuditService;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final AuditService auditService;

    public AdminUserController(UserService userService, AuditService auditService) {
        this.userService = userService;
        this.auditService = auditService;
    }

    @GetMapping
    public String list(@RequestParam(value = "q", required = false) String q, Model model) {
        model.addAttribute("users", userService.searchUsers(q));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("roles", new String[]{"CUSTOMER", "STAFF", "ADMIN"});
        return "admin/users";
    }

    @PostMapping
    public String create(@RequestParam String fullName,
                         @RequestParam String email,
                         @RequestParam String phone,
                         @RequestParam String password,
                         @RequestParam String role,
                         RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            User created = userService.createUser(fullName, email, phone, password, role);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "USER_CREATE", "USER", String.valueOf(created.getId()),
                    "Created user " + created.getEmail() + " as " + created.getRole());
            redirectAttributes.addFlashAttribute("successMessage", "User created.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/enabled")
    public String setEnabled(@PathVariable Long id,
                             @RequestParam boolean enabled,
                             RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        userService.setEnabled(id, enabled);
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "USER_ENABLED", "USER", String.valueOf(id), "enabled=" + enabled);
        redirectAttributes.addFlashAttribute("successMessage",
                enabled ? "User enabled." : "User disabled.");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/role")
    public String setRole(@PathVariable Long id,
                          @RequestParam String role,
                          RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            userService.setRole(id, role);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "USER_ROLE", "USER", String.valueOf(id), "role=" + role);
            redirectAttributes.addFlashAttribute("successMessage", "Role updated.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/password")
    public String resetPassword(@PathVariable Long id,
                                @RequestParam String password,
                                RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            userService.resetPassword(id, password);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "USER_PASSWORD_RESET", "USER", String.valueOf(id), "Password reset");
            redirectAttributes.addFlashAttribute("successMessage", "Password reset.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }
}
