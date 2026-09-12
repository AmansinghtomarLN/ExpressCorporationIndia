package com.mahavircourier.controller.admin;

import com.mahavircourier.service.AuditService;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.WorkspaceService;
import com.mahavircourier.model.Branch;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

@Controller
@RequestMapping("/admin/workspace")
public class AdminWorkspaceController {

    private final WorkspaceService workspaceService;
    private final AuditService auditService;

    public AdminWorkspaceController(WorkspaceService workspaceService, AuditService auditService) {
        this.workspaceService = workspaceService;
        this.auditService = auditService;
    }

    @PostMapping("/branch")
    public String changeBranch(@RequestParam Long branchId,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            Branch branch = workspaceService.setCurrent(principal.getUser().getId(), branchId);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "WORKSPACE_BRANCH", "USER", String.valueOf(principal.getUser().getId()),
                    "Working from " + branch.getCity() + " — " + branch.getBranchName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Working from " + branch.getCity() + " — " + branch.getBranchName());
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + safeAdminReturn(request);
    }

    private String safeAdminReturn(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return "/admin";
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getPath();
            if (path != null && path.startsWith("/admin")) {
                return path + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        return "/admin";
    }
}
