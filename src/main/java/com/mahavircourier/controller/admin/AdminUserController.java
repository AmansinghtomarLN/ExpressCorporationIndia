package com.mahavircourier.controller.admin;

import com.mahavircourier.model.User;
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

    public AdminUserController(UserService userService) {
        this.userService = userService;
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
            redirectAttributes.addFlashAttribute("successMessage", "Password reset.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            User target = userService.findById(id).orElse(null);
            String email = target != null ? target.getEmail() : String.valueOf(id);
            userService.deleteUser(id, principal.getUser().getId());
            redirectAttributes.addFlashAttribute("successMessage", "User deleted.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }
}
