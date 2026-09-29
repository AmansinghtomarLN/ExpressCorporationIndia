package com.mahavircourier.controller.admin;

import com.mahavircourier.model.Branch;
import com.mahavircourier.model.BranchLaneRate;
import com.mahavircourier.service.AuditService;
import com.mahavircourier.service.BranchService;
import com.mahavircourier.service.CustomUserDetails;
import com.mahavircourier.service.RateCardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/branch-rates")
public class AdminBranchRateController {

    private final BranchService branchService;
    private final RateCardService rateCardService;
    private final AuditService auditService;

    public AdminBranchRateController(BranchService branchService,
                                     RateCardService rateCardService,
                                     AuditService auditService) {
        this.branchService = branchService;
        this.rateCardService = rateCardService;
        this.auditService = auditService;
    }

    @GetMapping
    public String page(@RequestParam(value = "originId", required = false) Long originId, Model model) {
        List<Branch> branches = branchService.findAll();
        model.addAttribute("branches", branches);
        Map<Long, Integer> counts = rateCardService.countLanesByOrigin();
        List<OriginCoverage> coverage = new ArrayList<>();
        for (Branch branch : branches) {
            int others = Math.max(0, branches.size() - 1);
            coverage.add(new OriginCoverage(
                    branch.getId(),
                    branch.getBranchName(),
                    branch.getCity(),
                    counts.getOrDefault(branch.getId(), 0),
                    others));
        }
        model.addAttribute("coverage", coverage);

        Branch origin = originId == null ? null : branchService.findById(originId).orElse(null);
        model.addAttribute("origin", origin);
        List<Branch> destinations = new ArrayList<>();
        Map<Long, BranchLaneRate> ratesByDest = new HashMap<>();
        int setCount = 0;
        if (origin != null) {
            for (Branch branch : branches) {
                if (!branch.getId().equals(origin.getId())) {
                    destinations.add(branch);
                }
            }
            for (BranchLaneRate rate : rateCardService.findLanesFrom(origin.getId())) {
                ratesByDest.put(rate.getDestBranchId(), rate);
                setCount++;
            }
        }
        model.addAttribute("destinations", destinations);
        model.addAttribute("ratesByDest", ratesByDest);
        model.addAttribute("setCount", setCount);
        model.addAttribute("missingCount", destinations.size() - setCount);
        return "admin/branch-rates";
    }

    @PostMapping("/apply")
    public String apply(@RequestParam Long originId,
                        @RequestParam BigDecimal perKgRate,
                        RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            int saved = rateCardService.applyLaneRate(originId, perKgRate);
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "BRANCH_LANE_APPLY", "BRANCH", String.valueOf(originId),
                    "per kg=" + perKgRate + ", branches=" + saved);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Set ₹" + perKgRate.toPlainString() + " per kg from this branch to "
                            + saved + (saved == 1 ? " branch." : " branches."));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect(originId);
    }

    @PostMapping("/save")
    public String save(@RequestParam Long originId,
                       @RequestParam(required = false) List<Long> removeId,
                       @RequestParam(required = false) List<Long> destBranchId,
                       @RequestParam(required = false) List<BigDecimal> perKgRate,
                       RedirectAttributes redirectAttributes) {
        CustomUserDetails principal = AdminAuth.requirePrincipal();
        try {
            int saved = rateCardService.saveLaneRates(originId, removeId, destBranchId, perKgRate);
            int removed = removeId == null ? 0 : (int) removeId.stream().filter(id -> id != null).count();
            auditService.log(principal.getUser().getId(), principal.getUsername(),
                    "BRANCH_LANE_SAVE", "BRANCH", String.valueOf(originId),
                    "saved=" + saved + ", removed=" + removed);
            if (saved == 0 && removed == 0) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Enter a per kg rate for at least one branch, then save.");
            } else if (saved == 0) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Removed " + removed + " branch rate" + (removed == 1 ? "." : "s."));
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Saved " + saved + " branch rate" + (saved == 1 ? "" : "s")
                                + (removed > 0 ? " and removed " + removed : "") + ".");
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect(originId);
    }

    private String redirect(Long originId) {
        return "redirect:/admin/branch-rates?originId=" + originId;
    }

    public static final class OriginCoverage {
        private final Long branchId;
        private final String branchName;
        private final String city;
        private final int set;
        private final int total;

        public OriginCoverage(Long branchId, String branchName, String city, int set, int total) {
            this.branchId = branchId;
            this.branchName = branchName;
            this.city = city;
            this.set = set;
            this.total = total;
        }

        public Long getBranchId() {
            return branchId;
        }

        public String getBranchName() {
            return branchName;
        }

        public String getCity() {
            return city;
        }

        public int getSet() {
            return set;
        }

        public int getTotal() {
            return total;
        }

        public int getMissing() {
            return Math.max(0, total - set);
        }
    }
}
