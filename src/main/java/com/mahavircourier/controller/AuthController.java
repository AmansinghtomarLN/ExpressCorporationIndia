package com.mahavircourier.controller;

import com.mahavircourier.dto.SignupForm;
import com.mahavircourier.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                             @RequestParam(value = "logout", required = false) String logout,
                             Model model) {
        if (error != null) {
            model.addAttribute("errorMessage", "Invalid email or password. Please try again.");
        }
        if (logout != null) {
            model.addAttribute("infoMessage", "You have been logged out successfully.");
        }
        return "login";
    }

    @GetMapping("/signup")
    public String signupPage(Model model) {
        if (!model.containsAttribute("signupForm")) {
            model.addAttribute("signupForm", new SignupForm());
        }
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("signupForm") SignupForm form,
                          BindingResult bindingResult,
                          Model model) {

        if (form.getEmail() != null && userService.emailTaken(form.getEmail().trim().toLowerCase())) {
            bindingResult.rejectValue("email", "email.exists", "An account with this email already exists");
        }
        if (!form.isPasswordConfirmed()) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match");
        }

        if (bindingResult.hasErrors()) {
            return "signup";
        }

        userService.registerCustomer(form);
        model.addAttribute("registered", true);
        return "login";
    }
}
