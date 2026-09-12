package com.mahavircourier.service;

import com.mahavircourier.dao.BranchDao;
import com.mahavircourier.dao.ManifestBillDao;
import com.mahavircourier.dao.ManifestDao;
import com.mahavircourier.dao.ManifestItemDao;
import com.mahavircourier.dao.PartyDao;
import com.mahavircourier.dao.ShipmentDao;
import com.mahavircourier.dto.FreightQuote;
import com.mahavircourier.model.Manifest;
import com.mahavircourier.model.ManifestBill;
import com.mahavircourier.model.ManifestItem;
import com.mahavircourier.model.Party;
import com.mahavircourier.model.Shipment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ManifestBillService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ManifestBillDao manifestBillDao;
    private final ManifestDao manifestDao;
    private final ManifestItemDao manifestItemDao;
    private final ShipmentDao shipmentDao;
    private final PartyDao partyDao;
    private final BranchDao branchDao;
    private final RateCardService rateCardService;

    public ManifestBillService(ManifestBillDao manifestBillDao,
                               ManifestDao manifestDao,
                               ManifestItemDao manifestItemDao,
                               ShipmentDao shipmentDao,
                               PartyDao partyDao,
                               BranchDao branchDao,
                               RateCardService rateCardService) {
        this.manifestBillDao = manifestBillDao;
        this.manifestDao = manifestDao;
        this.manifestItemDao = manifestItemDao;
        this.shipmentDao = shipmentDao;
        this.partyDao = partyDao;
        this.branchDao = branchDao;
        this.rateCardService = rateCardService;
    }

    public List<ManifestBill> searchPartyBills(Long partyId, Long branchId, String status,
                                               LocalDate from, LocalDate to) {
        return manifestBillDao.search(ManifestBill.TYPE_PARTY, partyId, branchId, status, from, to);
    }

    public List<ManifestBill> searchBranchBills(Long branchId, Long partyId, String status,
                                                LocalDate from, LocalDate to) {
        return manifestBillDao.search(ManifestBill.TYPE_BRANCH, partyId, branchId, status, from, to);
    }

    public List<ManifestBill> findByManifestId(Long manifestId) {
        return manifestBillDao.findByManifestId(manifestId);
    }

    public Optional<ManifestBill> findById(Long id) {
        return manifestBillDao.findById(id);
    }

    public long countPending(String billType) {
        return manifestBillDao.countByTypeAndStatus(billType, ManifestBill.STATUS_PENDING);
    }

    public BigDecimal sumPending(String billType) {
        return manifestBillDao.sumByTypeAndStatus(billType, ManifestBill.STATUS_PENDING);
    }

    public long countReceived(String billType) {
        return manifestBillDao.countByTypeAndStatus(billType, ManifestBill.STATUS_RECEIVED);
    }

    public BigDecimal sumReceived(String billType) {
        return manifestBillDao.sumByTypeAndStatus(billType, ManifestBill.STATUS_RECEIVED);
    }

    public long countPending(String billType, LocalDate from, LocalDate to) {
        return manifestBillDao.countByTypeAndStatusBetween(
                billType, ManifestBill.STATUS_PENDING, from, to);
    }

    public BigDecimal sumPending(String billType, LocalDate from, LocalDate to) {
        return manifestBillDao.sumByTypeAndStatusBetween(
                billType, ManifestBill.STATUS_PENDING, from, to);
    }

    public long countReceived(String billType, LocalDate from, LocalDate to) {
        return manifestBillDao.countByTypeAndStatusBetween(
                billType, ManifestBill.STATUS_RECEIVED, from, to);
    }

    public BigDecimal sumReceived(String billType, LocalDate from, LocalDate to) {
        return manifestBillDao.sumByTypeAndStatusBetween(
                billType, ManifestBill.STATUS_RECEIVED, from, to);
    }

    /**
     * Creates one party bill per distinct sending party and one destination-branch bill.
     * Called only when a manifest is submitted. Pending bills can be refreshed if lines change.
     */
    @Transactional
    public List<ManifestBill> createForSubmittedManifest(Long manifestId) {
        Manifest manifest = manifestDao.findById(manifestId)
                .orElseThrow(() -> new IllegalArgumentException("Manifest not found"));
        List<ManifestItem> items = manifestItemDao.findByManifestId(manifestId);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Cannot bill a manifest with no consignments");
        }

        Long destBranchId = resolveDestinationBranchId(manifest, items);
        Map<Long, LineTotals> byParty = groupByParty(manifest, items);
        if (byParty.isEmpty()) {
            throw new IllegalArgumentException("Cannot create a party bill without a sending party");
        }

        List<ManifestBill> bills = new ArrayList<>();
        for (Map.Entry<Long, LineTotals> entry : byParty.entrySet()) {
            Party party = partyDao.findById(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Party not found"));
            LineTotals totals = entry.getValue();
            FreightQuote quote = rateCardService.quoteForParty(
                    party, destBranchId, manifest.getBillingLane(), totals.weight, totals.boxes);
            bills.add(upsert(buildBill(
                    ManifestBill.TYPE_PARTY,
                    manifest,
                    party.getId(),
                    destBranchId,
                    totals,
                    quote,
                    "Party bill for " + party.getPartyName()
                            + " on MF " + manifest.getManifestNumber())));
        }

        LineTotals branchTotals = sumAll(items);
        FreightQuote branchQuote = destBranchId != null
                ? rateCardService.quoteBranch(destBranchId, branchTotals.weight, branchTotals.boxes)
                : rateCardService.quote(null, manifest.getBillingLane(), branchTotals.weight, branchTotals.boxes);
        Long branchPartyId = byParty.size() == 1 ? byParty.keySet().iterator().next() : null;
        bills.add(upsert(buildBill(
                ManifestBill.TYPE_BRANCH,
                manifest,
                branchPartyId,
                destBranchId,
                branchTotals,
                branchQuote,
                "Branch bill for MF " + manifest.getManifestNumber())));
        return bills;
    }

    @Transactional
    public void refreshPendingIfPresent(Long manifestId) {
        if (manifestBillDao.existsForManifest(manifestId)) {
            createForSubmittedManifest(manifestId);
        }
    }

    @Transactional
    public void markReceived(Long id) {
        ManifestBill bill = requireBill(id);
        manifestBillDao.updateStatus(bill.getId(), ManifestBill.STATUS_RECEIVED);
    }

    @Transactional
    public void markPending(Long id) {
        ManifestBill bill = requireBill(id);
        manifestBillDao.updateStatus(bill.getId(), ManifestBill.STATUS_PENDING);
    }

    private ManifestBill requireBill(Long id) {
        return manifestBillDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
    }

    private ManifestBill upsert(ManifestBill incoming) {
        Optional<ManifestBill> existing = ManifestBill.TYPE_BRANCH.equals(incoming.getBillType())
                ? manifestBillDao.findByManifestAndType(incoming.getManifestId(), ManifestBill.TYPE_BRANCH)
                : manifestBillDao.findExisting(
                        incoming.getManifestId(), incoming.getBillType(), incoming.getPartyId(), incoming.getBranchId());
        if (existing.isPresent()) {
            ManifestBill current = existing.get();
            if (current.isPending()) {
                incoming.setId(current.getId());
                incoming.setBillNumber(current.getBillNumber());
                incoming.setStatus(current.getStatus());
                manifestBillDao.updateAmounts(incoming);
            }
            incoming.setId(current.getId());
            incoming.setBillNumber(current.getBillNumber());
            incoming.setStatus(current.getStatus());
            return incoming;
        }
        incoming.setStatus(ManifestBill.STATUS_PENDING);
        incoming.setBillNumber(nextBillNumber(incoming.getBillType()));
        Long id = manifestBillDao.save(incoming);
        incoming.setId(id);
        return incoming;
    }

    private ManifestBill buildBill(String type, Manifest manifest, Long partyId, Long branchId,
                                   LineTotals totals, FreightQuote quote, String notes) {
        ManifestBill bill = new ManifestBill();
        bill.setBillType(type);
        bill.setManifestId(manifest.getId());
        bill.setPartyId(partyId);
        bill.setBranchId(branchId);
        bill.setWeightKg(totals.weight);
        bill.setNumberOfBoxes(totals.boxes);
        bill.setPerKgRate(quote.getPerKgRate());
        bill.setPerBoxRate(quote.getPerBoxRate());
        bill.setFreightAmount(quote.getAmount());
        bill.setNotes(notes);
        return bill;
    }

    private Map<Long, LineTotals> groupByParty(Manifest manifest, List<ManifestItem> items) {
        Map<Long, LineTotals> byParty = new LinkedHashMap<>();
        for (ManifestItem item : items) {
            Long partyId = resolvePartyId(manifest, item);
            if (partyId == null) {
                continue;
            }
            byParty.computeIfAbsent(partyId, id -> new LineTotals()).add(item);
        }
        return byParty;
    }

    private Long resolvePartyId(Manifest manifest, ManifestItem item) {
        if (item.getShipmentId() != null) {
            Optional<Shipment> shipment = shipmentDao.findById(item.getShipmentId());
            if (shipment.isPresent() && shipment.get().getPartyId() != null) {
                return shipment.get().getPartyId();
            }
        }
        return manifest.getPartyId();
    }

    private Long resolveDestinationBranchId(Manifest manifest, List<ManifestItem> items) {
        if (manifest.getDestinationBranchId() != null) {
            return manifest.getDestinationBranchId();
        }
        for (ManifestItem item : items) {
            if (StringUtils.hasText(item.getDestinationCity())) {
                Optional<Long> branchId = branchDao.findByCityIgnoreCase(item.getDestinationCity().trim())
                        .map(b -> b.getId());
                if (branchId.isPresent()) {
                    return branchId.get();
                }
            }
        }
        throw new IllegalArgumentException("Cannot create a branch bill without a destination branch");
    }

    private LineTotals sumAll(List<ManifestItem> items) {
        LineTotals totals = new LineTotals();
        for (ManifestItem item : items) {
            totals.add(item);
        }
        return totals;
    }

    private String nextBillNumber(String billType) {
        String prefix = ManifestBill.TYPE_PARTY.equals(billType) ? "PTY-" : "BRN-";
        String dayPrefix = prefix + LocalDate.now().format(DAY_FMT) + "-";
        int seq = manifestBillDao.countToday(dayPrefix) + 1;
        return dayPrefix + String.format("%04d", seq);
    }

    private static final class LineTotals {
        private BigDecimal weight = BigDecimal.ZERO;
        private int boxes;

        private void add(ManifestItem item) {
            if (item.getWeightKg() != null) {
                weight = weight.add(item.getWeightKg());
            }
            boxes += item.getNumberOfBoxes();
        }
    }
}
