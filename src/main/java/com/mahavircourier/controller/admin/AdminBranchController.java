package com.mahavircourier.controller.admin;

import com.mahavircourier.model.Branch;
import com.mahavircourier.service.AuditService;
import com.mahavircourier.service.BranchService;
import com.mahavircourier.service.CustomUserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/branches")
public class AdminBranchController {

    private final BranchService branchService;
    private final AuditService auditService;

    public AdminBranchController(BranchService branchService, AuditService auditService) {
        this.branchService = branchService;
        this.auditService = auditService;
    }

    @GetMapping
    public String list(@RequestParam(value = "editId", required = false) Long editId, Model model) {
        model.addAttribute("branches", branchService.findAll());
        model.addAttribute("branch", new Branch());
        if (editId != null) {
            branchService.findById(editId).ifPresent(b -> model.addAttribute("editBranch", b));
        }
        return "admin/branches";
    }

    @PostMapping
    public String create(@ModelAttribute Branch branch, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        Branch created = branchService.create(branch);
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "BRANCH_CREATE", "BRANCH", String.valueOf(created.getId()),
                created.getBranchName());
        redirectAttributes.addFlashAttribute("successMessage", "Branch created.");
        return "redirect:/admin/branches";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @ModelAttribute Branch branch,
                       RedirectAttributes redirectAttributes) {
        return update(id, branch, redirectAttributes);
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id,
                         @ModelAttribute Branch branch,
                         RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        branch.setId(id);
        branchService.update(branch);
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "BRANCH_UPDATE", "BRANCH", String.valueOf(id), branch.getBranchName());
        redirectAttributes.addFlashAttribute("successMessage", "Branch updated.");
        return "redirect:/admin/branches";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        branchService.delete(id);
        auditService.log(principal.getUser().getId(), principal.getUsername(),
                "BRANCH_DELETE", "BRANCH", String.valueOf(id), "Deleted branch");
        redirectAttributes.addFlashAttribute("successMessage", "Branch deleted.");
        return "redirect:/admin/branches";
    }
}
