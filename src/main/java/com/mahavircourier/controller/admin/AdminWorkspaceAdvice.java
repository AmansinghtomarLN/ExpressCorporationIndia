package com.mahavircourier.controller.admin;

import com.mahavircourier.model.Branch;
import com.mahavircourier.model.User;
import com.mahavircourier.service.BranchService;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.UserService;
import com.mahavircourier.service.WorkspaceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice
public class AdminWorkspaceAdvice {

    private final UserService userService;
    private final WorkspaceService workspaceService;
    private final BranchService branchService;

    public AdminWorkspaceAdvice(UserService userService,
                                WorkspaceService workspaceService,
                                BranchService branchService) {
        this.userService = userService;
        this.workspaceService = workspaceService;
        this.branchService = branchService;
    }

    @ModelAttribute
    public void addWorkspace(Model model, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return;
        }
        String role = details.getUser().getRole();
        if (!"ADMIN".equals(role) && !"STAFF".equals(role)) {
            return;
        }
        User user = userService.findById(details.getUser().getId()).orElse(details.getUser());
        try {
            Branch current = workspaceService.resolveCurrent(user);
            model.addAttribute("workspaceBranch", current);
            model.addAttribute("workspaceBranches", branchService.findAll());
        } catch (IllegalArgumentException ignored) {
            // no branches seeded yet
        }
    }
}
