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
import java.math.RoundingMode;
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
    private final PartyService partyService;

    public ManifestBillService(ManifestBillDao manifestBillDao,
                               ManifestDao manifestDao,
                               ManifestItemDao manifestItemDao,
                               ShipmentDao shipmentDao,
                               PartyDao partyDao,
                               BranchDao branchDao,
                               RateCardService rateCardService,
                               PartyService partyService) {
        this.manifestBillDao = manifestBillDao;
        this.manifestDao = manifestDao;
        this.manifestItemDao = manifestItemDao;
        this.shipmentDao = shipmentDao;
        this.partyDao = partyDao;
        this.branchDao = branchDao;
        this.rateCardService = rateCardService;
        this.partyService = partyService;
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
        Map<Long, List<ManifestItem>> byParty = groupItemsByParty(manifest, items);
        Map<String, List<ManifestItem>> bySender = groupUnmappedBySender(manifest, items);
        if (byParty.isEmpty() && bySender.isEmpty()) {
            throw new IllegalArgumentException("Cannot create a party bill without a sending party");
        }

        List<ManifestBill> bills = new ArrayList<>();
        for (Map.Entry<Long, List<ManifestItem>> entry : byParty.entrySet()) {
            Party party = partyDao.findById(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Party not found"));
            List<ManifestItem> partyItems = entry.getValue();
            LineTotals totals = new LineTotals();
            BigDecimal amount = BigDecimal.ZERO;
            BigDecimal commonRate = null;
            boolean mixedRates = false;
            boolean minimumApplied = false;
            for (ManifestItem item : partyItems) {
                totals.add(item);
                FreightQuote lineQuote = rateCardService.quoteForPartyCity(
                        party, item.getDestinationCity(), manifest.getBillingLane(),
                        item.getWeightKg(), item.getNumberOfBoxes());
                amount = amount.add(lineQuote.getAmount());
                if (item.getWeightKg() != null && lineQuote.getBillableWeightKg() != null
                        && lineQuote.getBillableWeightKg().compareTo(item.getWeightKg()) > 0) {
                    minimumApplied = true;
                }
                if (commonRate == null) {
                    commonRate = lineQuote.getPerKgRate();
                } else if (lineQuote.getPerKgRate() != null
                        && commonRate.compareTo(lineQuote.getPerKgRate()) != 0) {
                    mixedRates = true;
                }
            }
            FreightQuote quote = new FreightQuote();
            quote.setPerKgRate(commonRate != null ? commonRate : BigDecimal.ZERO);
            quote.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
            String notes = "Party bill for " + party.getPartyName()
                    + " on MF " + manifest.getManifestNumber();
            if (minimumApplied || mixedRates) {
                notes += ". Billed per city rate; minimum kg applied.";
            }
            if (notes.length() > 255) {
                notes = notes.substring(0, 252) + "...";
            }
            bills.add(upsert(buildBill(
                    ManifestBill.TYPE_PARTY,
                    manifest,
                    party.getId(),
                    destBranchId,
                    totals,
                    quote,
                    notes)));
        }

        for (Map.Entry<String, List<ManifestItem>> entry : bySender.entrySet()) {
            List<ManifestItem> senderItems = entry.getValue();
            LineTotals totals = new LineTotals();
            BigDecimal amount = BigDecimal.ZERO;
            BigDecimal commonRate = null;
            boolean mixedRates = false;
            for (ManifestItem item : senderItems) {
                totals.add(item);
                FreightQuote lineQuote = rateCardService.quote(
                        item.getDestinationCity(), manifest.getBillingLane(),
                        item.getWeightKg(), item.getNumberOfBoxes());
                amount = amount.add(lineQuote.getAmount());
                if (commonRate == null) {
                    commonRate = lineQuote.getPerKgRate();
                } else if (lineQuote.getPerKgRate() != null
                        && commonRate.compareTo(lineQuote.getPerKgRate()) != 0) {
                    mixedRates = true;
                }
            }
            FreightQuote quote = new FreightQuote();
            quote.setPerKgRate(commonRate != null ? commonRate : BigDecimal.ZERO);
            quote.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
            String senderName = entry.getKey();
            String notes = "Sender bill for " + senderName + " on MF " + manifest.getManifestNumber();
            if (mixedRates) {
                notes += ". Billed at the destination rate.";
            }
            if (notes.length() > 255) {
                notes = notes.substring(0, 252) + "...";
            }
            ManifestBill senderBill = buildBill(
                    ManifestBill.TYPE_PARTY,
                    manifest,
                    null,
                    destBranchId,
                    totals,
                    quote,
                    notes);
            senderBill.setBillToName(senderName);
            bills.add(upsert(senderBill));
        }

        LineTotals branchTotals = sumAll(items);
        Long originBranchId = originBranchId(manifest);
        BigDecimal branchAmount = BigDecimal.ZERO;
        BigDecimal branchRate = null;
        boolean mixedBranchRates = false;
        for (ManifestItem item : items) {
            Long itemDest = destinationBranchId(item, destBranchId);
            FreightQuote lineQuote = rateCardService.quoteBranchLane(
                    originBranchId, itemDest, item.getWeightKg(), item.getNumberOfBoxes());
            branchAmount = branchAmount.add(lineQuote.getAmount());
            if (branchRate == null) {
                branchRate = lineQuote.getPerKgRate();
            } else if (lineQuote.getPerKgRate() != null && branchRate.compareTo(lineQuote.getPerKgRate()) != 0) {
                mixedBranchRates = true;
            }
        }
        FreightQuote branchQuote = new FreightQuote();
        branchQuote.setPerKgRate(branchRate != null ? branchRate : BigDecimal.ZERO);
        branchQuote.setAmount(branchAmount.setScale(2, RoundingMode.HALF_UP));
        String branchNotes = "Branch bill for MF " + manifest.getManifestNumber();
        if (mixedBranchRates) {
            branchNotes += ". Billed from the origin branch to each destination branch.";
        }
        Long branchPartyId = byParty.size() == 1 ? byParty.keySet().iterator().next() : null;
        bills.add(upsert(buildBill(
                ManifestBill.TYPE_BRANCH,
                manifest,
                branchPartyId,
                destBranchId,
                branchTotals,
                branchQuote,
                branchNotes)));
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

    public List<ManifestItem> linesFor(ManifestBill bill) {
        List<ManifestItem> items = manifestItemDao.findByManifestId(bill.getManifestId());
        List<ManifestItem> result = items;
        Manifest manifest = manifestDao.findById(bill.getManifestId()).orElse(null);
        if (bill.isPartyBill() && bill.getPartyId() != null) {
            result = new ArrayList<>();
            for (ManifestItem item : items) {
                Long partyId = resolvePartyId(manifest, item);
                if (bill.getPartyId().equals(partyId)) {
                    result.add(item);
                }
            }
        } else if (bill.isSenderNameBill()) {
            result = new ArrayList<>();
            String wanted = bill.getBillToName().trim();
            for (ManifestItem item : items) {
                if (resolvePartyId(manifest, item) != null) {
                    continue;
                }
                String sender = senderName(item);
                if (sender != null && sender.equalsIgnoreCase(wanted)) {
                    result.add(item);
                }
            }
        }
        applyLineAmounts(result, bill, manifest);
        return result;
    }

    public LocalDate manifestDateFor(Long manifestId) {
        if (manifestId == null) {
            return null;
        }
        return manifestDao.findById(manifestId).map(Manifest::getManifestDate).orElse(null);
    }

    @Transactional
    public ManifestBill updateDetails(Long id, BigDecimal weightKg, BigDecimal perKgRate,
                                      String notes, String status, LocalDate billDate) {
        ManifestBill bill = requireBill(id);
        if (weightKg != null && weightKg.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Weight cannot be negative");
        }
        if (perKgRate != null && perKgRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Per kg rate cannot be negative");
        }
        boolean amountChanged = (weightKg != null && bill.getWeightKg() != null
                        && weightKg.compareTo(bill.getWeightKg()) != 0)
                || (perKgRate != null && bill.getPerKgRate() != null
                        && perKgRate.compareTo(bill.getPerKgRate()) != 0);
        if (weightKg != null) {
            bill.setWeightKg(weightKg);
        }
        if (perKgRate != null) {
            bill.setPerKgRate(perKgRate);
        }
        bill.setPerBoxRate(BigDecimal.ZERO);
        if (amountChanged) {
            bill.setFreightAmount(rateCardService.computeFreight(
                    bill.getWeightKg(), bill.getNumberOfBoxes(), bill.getPerKgRate(), BigDecimal.ZERO));
        }
        if (billDate != null) {
            bill.setBillDate(billDate);
        } else if (bill.getBillDate() == null) {
            bill.setBillDate(bill.resolveBillDate(manifestDateFor(bill.getManifestId())));
        }
        if (notes != null) {
            bill.setNotes(StringUtils.hasText(notes) ? notes.trim() : null);
        }
        if (ManifestBill.STATUS_RECEIVED.equalsIgnoreCase(status)
                || ManifestBill.STATUS_PENDING.equalsIgnoreCase(status)) {
            bill.setStatus(status.trim().toUpperCase());
        }
        manifestBillDao.updateAmounts(bill);
        return bill;
    }

    private ManifestBill requireBill(Long id) {
        return manifestBillDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
    }

    private ManifestBill upsert(ManifestBill incoming) {
        Optional<ManifestBill> existing = ManifestBill.TYPE_BRANCH.equals(incoming.getBillType())
                ? manifestBillDao.findByManifestAndType(incoming.getManifestId(), ManifestBill.TYPE_BRANCH)
                : manifestBillDao.findExisting(
                        incoming.getManifestId(), incoming.getBillType(), incoming.getPartyId(),
                        incoming.getBranchId(), incoming.getBillToName());
        if (existing.isPresent()) {
            ManifestBill current = existing.get();
            if (current.isPending()) {
                incoming.setId(current.getId());
                incoming.setBillNumber(current.getBillNumber());
                incoming.setStatus(current.getStatus());
                if (current.getBillDate() != null) {
                    incoming.setBillDate(current.getBillDate());
                }
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
        bill.setPerBoxRate(BigDecimal.ZERO);
        bill.setFreightAmount(quote.getAmount());
        bill.setNotes(notes);
        bill.setBillDate(ManifestBill.monthEnd(manifest.getManifestDate()));
        return bill;
    }

    private Map<Long, List<ManifestItem>> groupItemsByParty(Manifest manifest, List<ManifestItem> items) {
        Map<Long, List<ManifestItem>> byParty = new LinkedHashMap<>();
        for (ManifestItem item : items) {
            Long partyId = resolvePartyId(manifest, item);
            if (partyId == null) {
                continue;
            }
            byParty.computeIfAbsent(partyId, id -> new ArrayList<>()).add(item);
        }
        return byParty;
    }

    private Long resolvePartyId(Manifest manifest, ManifestItem item) {
        Long number = PartyService.parseConsignment(item.getConsignmentNo());
        if (number != null) {
            Optional<com.mahavircourier.model.ConsignmentRange> owner = partyService.findOwner(number);
            if (owner.isPresent()) {
                return owner.get().getPartyId();
            }
        }
        if (item.getShipmentId() != null) {
            Optional<Shipment> shipment = shipmentDao.findById(item.getShipmentId());
            if (shipment.isPresent()) {
                return positiveId(shipment.get().getPartyId());
            }
        }
        return positiveId(manifest.getPartyId());
    }

    private static Long positiveId(Long id) {
        return id != null && id > 0 ? id : null;
    }

    private Map<String, List<ManifestItem>> groupUnmappedBySender(Manifest manifest, List<ManifestItem> items) {
        Map<String, List<ManifestItem>> bySender = new LinkedHashMap<>();
        Map<String, String> display = new LinkedHashMap<>();
        for (ManifestItem item : items) {
            if (resolvePartyId(manifest, item) != null) {
                continue;
            }
            String sender = senderName(item);
            if (!StringUtils.hasText(sender)) {
                throw new IllegalArgumentException(
                        "C.No " + item.getConsignmentNo()
                                + " is not assigned to a party and has no sender name to bill.");
            }
            String key = sender.trim().toLowerCase();
            display.putIfAbsent(key, sender.trim());
            bySender.computeIfAbsent(display.get(key), name -> new ArrayList<>()).add(item);
        }
        return bySender;
    }

    private String senderName(ManifestItem item) {
        if (item.getShipmentId() == null) {
            return null;
        }
        return shipmentDao.findById(item.getShipmentId()).map(Shipment::getSenderName).orElse(null);
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

    private void applyLineAmounts(List<ManifestItem> items, ManifestBill bill, Manifest manifest) {
        if (bill.isPartyBill() && bill.getPartyId() != null && manifest != null) {
            Party party = partyDao.findById(bill.getPartyId()).orElse(null);
            if (party != null && applyQuotedPartyLines(items, bill, manifest, party)) {
                return;
            }
        }
        if (bill.isSenderNameBill() && manifest != null && applyQuotedSenderLines(items, bill, manifest)) {
            return;
        }
        if (!bill.isPartyBill() && manifest != null && applyQuotedBranchLines(items, bill, manifest)) {
            return;
        }
        BigDecimal rate = bill.getPerKgRate() != null ? bill.getPerKgRate() : BigDecimal.ZERO;
        for (ManifestItem item : items) {
            item.setBillableWeightKg(item.getWeightKg());
            item.setLineRate(rate);
            item.setFreightCharge(rateCardService.computeFreight(
                    item.getWeightKg(), item.getNumberOfBoxes(), rate, BigDecimal.ZERO));
        }
    }

    /**
     * Uses live city rates when they still add up to the stored bill.
     * A manual weight × per kg edit keeps the single rate instead.
     */
    private boolean applyQuotedPartyLines(List<ManifestItem> items, ManifestBill bill,
                                          Manifest manifest, Party party) {
        List<FreightQuote> quotes = new ArrayList<>();
        BigDecimal quoted = BigDecimal.ZERO;
        for (ManifestItem item : items) {
            FreightQuote line = rateCardService.quoteForPartyCity(
                    party, item.getDestinationCity(), manifest.getBillingLane(),
                    item.getWeightKg(), item.getNumberOfBoxes());
            quotes.add(line);
            quoted = quoted.add(line.getAmount());
        }
        BigDecimal stored = bill.getFreightAmount() != null ? bill.getFreightAmount() : BigDecimal.ZERO;
        if (quoted.setScale(2, RoundingMode.HALF_UP).compareTo(stored.setScale(2, RoundingMode.HALF_UP)) != 0) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            ManifestItem item = items.get(i);
            FreightQuote line = quotes.get(i);
            item.setBillableWeightKg(line.getBillableWeightKg());
            item.setLineRate(line.getPerKgRate());
            item.setFreightCharge(line.getAmount());
        }
        return true;
    }

    private boolean applyQuotedSenderLines(List<ManifestItem> items, ManifestBill bill, Manifest manifest) {
        List<FreightQuote> quotes = new ArrayList<>();
        BigDecimal quoted = BigDecimal.ZERO;
        for (ManifestItem item : items) {
            FreightQuote line = rateCardService.quote(
                    item.getDestinationCity(), manifest.getBillingLane(),
                    item.getWeightKg(), item.getNumberOfBoxes());
            quotes.add(line);
            quoted = quoted.add(line.getAmount());
        }
        BigDecimal stored = bill.getFreightAmount() != null ? bill.getFreightAmount() : BigDecimal.ZERO;
        if (quoted.setScale(2, RoundingMode.HALF_UP).compareTo(stored.setScale(2, RoundingMode.HALF_UP)) != 0) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            ManifestItem item = items.get(i);
            FreightQuote line = quotes.get(i);
            item.setBillableWeightKg(item.getWeightKg());
            item.setLineRate(line.getPerKgRate());
            item.setFreightCharge(line.getAmount());
        }
        return true;
    }

    private boolean applyQuotedBranchLines(List<ManifestItem> items, ManifestBill bill, Manifest manifest) {
        Long originBranchId = originBranchId(manifest);
        List<FreightQuote> quotes = new ArrayList<>();
        BigDecimal quoted = BigDecimal.ZERO;
        for (ManifestItem item : items) {
            FreightQuote line = rateCardService.quoteBranchLane(
                    originBranchId, destinationBranchId(item, bill.getBranchId()),
                    item.getWeightKg(), item.getNumberOfBoxes());
            quotes.add(line);
            quoted = quoted.add(line.getAmount());
        }
        BigDecimal stored = bill.getFreightAmount() != null ? bill.getFreightAmount() : BigDecimal.ZERO;
        if (quoted.setScale(2, RoundingMode.HALF_UP).compareTo(stored.setScale(2, RoundingMode.HALF_UP)) != 0) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            ManifestItem item = items.get(i);
            item.setBillableWeightKg(item.getWeightKg());
            item.setLineRate(quotes.get(i).getPerKgRate());
            item.setFreightCharge(quotes.get(i).getAmount());
        }
        return true;
    }

    private Long originBranchId(Manifest manifest) {
        if (manifest == null || !StringUtils.hasText(manifest.getOriginCity())) {
            return null;
        }
        return branchDao.findByCityIgnoreCase(manifest.getOriginCity().trim()).map(b -> b.getId()).orElse(null);
    }

    private Long destinationBranchId(ManifestItem item, Long fallback) {
        if (item != null && StringUtils.hasText(item.getDestinationCity())) {
            Optional<Long> found = branchDao.findByCityIgnoreCase(item.getDestinationCity().trim()).map(b -> b.getId());
            if (found.isPresent()) {
                return found.get();
            }
        }
        return fallback;
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
