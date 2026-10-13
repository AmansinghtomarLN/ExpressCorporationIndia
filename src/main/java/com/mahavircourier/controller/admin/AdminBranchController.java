package com.mahavircourier.controller.admin;

import com.mahavircourier.model.Branch;
import com.mahavircourier.service.BranchService;
import com.mahavircourier.service.CustomUserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/branches")
public class AdminBranchController {

    private final BranchService branchService;

    public AdminBranchController(BranchService branchService) {
        this.branchService = branchService;
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
        AdminAuth.requirePrincipal();
        try {
            branchService.create(branch);
            redirectAttributes.addFlashAttribute("successMessage", "Branch created.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/branches";
    }

    @PostMapping("/import")
    public String importExcel(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        AdminAuth.requirePrincipal();
        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Choose an Excel file");
            }
            BranchService.ImportOutcome outcome = branchService.importExcel(file.getInputStream(), file.getOriginalFilename());
            if (outcome.successText() != null) {
                redirectAttributes.addFlashAttribute("successMessage", outcome.successText());
            }
            if (outcome.problemText() != null) {
                redirectAttributes.addFlashAttribute("errorMessage", outcome.problemText());
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Could not read that Excel file. Use the columns SR NO., BranchName, CONT. PERSONAL, and MOBILE NO.");
        }
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
        AdminAuth.requirePrincipal();
        try {
            branch.setId(id);
            branchService.update(branch);
            redirectAttributes.addFlashAttribute("successMessage", "Branch updated.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/branches";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        branchService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Branch deleted.");
        return "redirect:/admin/branches";
    }
}
