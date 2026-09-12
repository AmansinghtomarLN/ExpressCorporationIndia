package com.mahavircourier.controller.admin;

import com.mahavircourier.model.ManifestBill;
import com.mahavircourier.service.DashboardPeriod;
import com.mahavircourier.service.ManifestBillService;
import com.mahavircourier.service.ManifestService;
import com.mahavircourier.service.ShipmentService;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final ShipmentService shipmentService;
    private final ManifestService manifestService;
    private final ManifestBillService manifestBillService;

    public AdminDashboardController(ShipmentService shipmentService,
                                    ManifestService manifestService,
                                    ManifestBillService manifestBillService) {
        this.shipmentService = shipmentService;
        this.manifestService = manifestService;
        this.manifestBillService = manifestBillService;
    }

    @GetMapping
    public String dashboard(@RequestParam(value = "period", defaultValue = DashboardPeriod.TODAY) String period,
                            Model model, HttpServletResponse response) {
        preventCaching(response);
        DashboardPeriod.Range range = DashboardPeriod.resolve(period);
        model.addAttribute("period", range.key());
        model.addAttribute("periodLabel", range.label());
        model.addAttribute("periodFrom", range.from());
        model.addAttribute("periodTo", range.to());
        model.addAttribute("stats", shipmentService.countStats(range.from(), range.to()));
        model.addAttribute("recentShipments", shipmentService.findRecent(range.from(), range.to(), 10));
        model.addAttribute("inProgressManifests",
                manifestService.countInProgressBetween(range.from(), range.to()));
        model.addAttribute("submittedManifests",
                manifestService.countCreatedBetween(range.from(), range.to()));
        model.addAttribute("pendingPartyBills",
                manifestBillService.countPending(ManifestBill.TYPE_PARTY, range.from(), range.to()));
        model.addAttribute("pendingPartyAmount",
                manifestBillService.sumPending(ManifestBill.TYPE_PARTY, range.from(), range.to()));
        model.addAttribute("pendingBranchBills",
                manifestBillService.countPending(ManifestBill.TYPE_BRANCH, range.from(), range.to()));
        model.addAttribute("pendingBranchAmount",
                manifestBillService.sumPending(ManifestBill.TYPE_BRANCH, range.from(), range.to()));
        model.addAttribute("receivedPartyBills",
                manifestBillService.countReceived(ManifestBill.TYPE_PARTY, range.from(), range.to()));
        model.addAttribute("receivedPartyAmount",
                manifestBillService.sumReceived(ManifestBill.TYPE_PARTY, range.from(), range.to()));
        model.addAttribute("receivedBranchBills",
                manifestBillService.countReceived(ManifestBill.TYPE_BRANCH, range.from(), range.to()));
        model.addAttribute("receivedBranchAmount",
                manifestBillService.sumReceived(ManifestBill.TYPE_BRANCH, range.from(), range.to()));
        return "admin/dashboard";
    }

    private void preventCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setDateHeader(HttpHeaders.EXPIRES, 0);
    }
}
