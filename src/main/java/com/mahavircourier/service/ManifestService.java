package com.mahavircourier.service;

import com.mahavircourier.dao.BranchDao;
import com.mahavircourier.dao.ManifestDao;
import com.mahavircourier.dao.ManifestItemDao;
import com.mahavircourier.dao.PartyDao;
import com.mahavircourier.dao.ShipmentDao;
import com.mahavircourier.dto.ManifestForm;
import com.mahavircourier.dto.ManifestItemForm;
import com.mahavircourier.model.Branch;
import com.mahavircourier.model.Manifest;
import com.mahavircourier.model.ManifestItem;
import com.mahavircourier.model.Party;
import com.mahavircourier.model.Shipment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ManifestService {

    private final ManifestDao manifestDao;
    private final ManifestItemDao manifestItemDao;
    private final PartyDao partyDao;
    private final PartyService partyService;
    private final ShipmentDao shipmentDao;
    private final ShipmentService shipmentService;
    private final RateCardService rateCardService;
    private final BranchDao branchDao;
    private final ManifestBillService manifestBillService;

    public ManifestService(ManifestDao manifestDao,
                           ManifestItemDao manifestItemDao,
                           PartyDao partyDao,
                           PartyService partyService,
                           ShipmentDao shipmentDao,
                           ShipmentService shipmentService,
                           RateCardService rateCardService,
                           BranchDao branchDao,
                           ManifestBillService manifestBillService) {
        this.manifestDao = manifestDao;
        this.manifestItemDao = manifestItemDao;
        this.partyDao = partyDao;
        this.partyService = partyService;
        this.shipmentDao = shipmentDao;
        this.shipmentService = shipmentService;
        this.rateCardService = rateCardService;
        this.branchDao = branchDao;
        this.manifestBillService = manifestBillService;
    }

    public List<Manifest> search(String query, Long partyId, LocalDate from, LocalDate to) {
        return search(query, partyId, from, to, null, null);
    }

    public List<Manifest> search(String query, Long partyId, LocalDate from, LocalDate to,
                                 String status, Long destinationBranchId) {
        return manifestDao.search(query, partyId, from, to, status, destinationBranchId);
    }

    public long countInProgress() {
        return manifestDao.countByStatus(Manifest.STATUS_IN_PROGRESS);
    }

    public long countCreated() {
        return manifestDao.countByStatus(Manifest.STATUS_CREATED);
    }

    public long countInProgressBetween(LocalDate from, LocalDate to) {
        return manifestDao.countByStatusCreatedBetween(Manifest.STATUS_IN_PROGRESS, from, to);
    }

    public long countCreatedBetween(LocalDate from, LocalDate to) {
        return manifestDao.countByStatusCreatedBetween(Manifest.STATUS_CREATED, from, to);
    }

    public Optional<Manifest> findById(Long id) {
        Optional<Manifest> opt = manifestDao.findById(id);
        opt.ifPresent(this::attachItemsAndBilling);
        return opt;
    }

    public String suggestNextNumber() {
        return manifestDao.nextManifestNumber();
    }

    public String validateConsignment(Long partyId, String consignmentNo) {
        Party party = partyDao.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("Party not found"));
        String normalized = normalizeConsignment(consignmentNo);
        Long number = PartyService.parseConsignment(normalized);
        if (number == null) {
            throw new IllegalArgumentException("Consignment number must be numeric");
        }
        Optional<com.mahavircourier.model.ConsignmentRange> owner = partyService.findOwner(number);
        if (owner.isPresent() && !owner.get().getPartyId().equals(party.getId())) {
            String name = owner.get().getPartyName() != null ? owner.get().getPartyName() : "another party";
            throw new IllegalArgumentException(
                    "C.No " + normalized + " is allocated to " + name + " and cannot be used");
        }
        if (manifestItemDao.existsByConsignmentNo(normalized) || shipmentDao.existsByTrackingId(normalized)) {
            throw new IllegalArgumentException("C.No " + normalized + " is already used on another manifest");
        }
        if (owner.isEmpty()) {
            return "C.No " + normalized + " is free and will be allocated to " + party.getPartyName();
        }
        return "C.No " + normalized + " is available for " + party.getPartyName();
    }

    @Transactional
    public Manifest create(ManifestForm form, Long bookedByUserId) {
        requireDestinationBranch(form.getDestinationBranchId());
        Optional<Manifest> open = manifestDao.findInProgressByBranch(form.getDestinationBranchId());
        if (open.isPresent()) {
            return appendLines(open.get().getId(), form, bookedByUserId);
        }

        List<ManifestItemForm> lines = collectFilledLines(form);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Add at least one open shipment or fill a consignment row");
        }
        Party party = form.getPartyId() != null ? requireEnabledParty(form.getPartyId()) : null;
        validateUniqueInForm(lines);

        Manifest manifest = new Manifest();
        applyHeader(manifest, form, party);
        manifest.setStatus(Manifest.STATUS_IN_PROGRESS);
        manifest.setManifestNumber(resolveManifestNumber(form.getManifestNumber()));
        applyTotals(manifest, lines);

        Long manifestId = manifestDao.save(manifest);
        manifest.setId(manifestId);

        int serial = 1;
        for (ManifestItemForm line : lines) {
            addOrAttachLine(manifest, party, line, serial++, bookedByUserId, form.isBillUnmappedBySender());
        }
        return findById(manifestId).orElse(manifest);
    }

    @Transactional
    public Manifest finalize(Long id) {
        Manifest manifest = manifestDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Manifest not found"));
        if (!manifest.isInProgress()) {
            throw new IllegalArgumentException("Manifest " + manifest.getManifestNumber() + " is already submitted");
        }
        List<ManifestItem> items = manifestItemDao.findByManifestId(id);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Add at least one consignment before submitting this manifest");
        }
        for (ManifestItem item : items) {
            if (item.getShipmentId() != null) {
                shipmentService.markDispatchedFromManifest(
                        item.getShipmentId(),
                        originOf(manifest, null),
                        "Created on manifest " + manifest.getManifestNumber());
            }
        }
        manifest.setStatus(Manifest.STATUS_CREATED);
        manifestDao.updateStatus(id, Manifest.STATUS_CREATED);
        manifestBillService.createForSubmittedManifest(id);
        return findById(id).orElse(manifest);
    }

    @Transactional
    public Manifest update(Long id, ManifestForm form) {
        Manifest existing = manifestDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Manifest not found"));
        List<ManifestItemForm> lines = collectFilledLines(form);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("A manifest must keep at least one consignment");
        }
        Party party = resolvePartyForEdit(form, existing);
        validateUniqueInForm(lines);

        applyHeader(existing, form, party);
        existing.setManifestNumber(existing.getManifestNumber());
        applyTotals(existing, lines);
        manifestDao.update(existing);

        List<ManifestItem> previousItems = manifestItemDao.findByManifestId(id);
        Set<Long> keepIds = new HashSet<>();
        for (ManifestItemForm line : lines) {
            if (line.getId() != null) {
                keepIds.add(line.getId());
            }
        }
        for (ManifestItem previous : previousItems) {
            if (!keepIds.contains(previous.getId())) {
                detachLine(previous);
            }
        }

        int serial = 1;
        for (ManifestItemForm line : lines) {
            if (line.getId() != null) {
                ManifestItem item = manifestItemDao.findById(line.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Manifest line not found"));
                if (!item.getManifestId().equals(id)) {
                    throw new IllegalArgumentException("Line does not belong to this manifest");
                }
                item.setSerialNo(serial++);
                item.setDestinationCity(resolveDestination(line, existing));
                item.setNumberOfBoxes(positiveBoxes(line.getNumberOfBoxes()));
                item.setWeightKg(positiveWeight(line.getWeightKg()));
                item.setReceiverName(required(line.getReceiverName(), "Receiver / details"));
                item.setReceiverPhone(trimToNull(line.getReceiverPhone()));
                manifestItemDao.update(item);
                if (item.getShipmentId() != null) {
                    shipmentService.syncFromManifestItem(
                            item.getShipmentId(),
                            item.getReceiverName(),
                            item.getReceiverPhone(),
                            item.getDestinationCity(),
                            item.getWeightKg(),
                            item.getNumberOfBoxes(),
                            existing.getServiceType(),
                            existing.getBillingLane(),
                            line.getSenderName(),
                            line.getSenderPhone());
                }
            } else {
                addOrAttachLine(existing, party, line, serial++, null, form.isBillUnmappedBySender());
            }
        }
        refreshTotals(id);
        if (Manifest.STATUS_CREATED.equals(existing.getStatus())) {
            manifestBillService.refreshPendingIfPresent(id);
        }
        return findById(id).orElse(existing);
    }

    @Transactional
    public void delete(Long id) {
        Manifest manifest = manifestDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Manifest not found"));
        List<ManifestItem> items = manifestItemDao.findByManifestId(id);
        boolean hasShipments = items.stream().anyMatch(i -> i.getShipmentId() != null);
        if (hasShipments) {
            throw new IllegalArgumentException(
                    "Cannot delete manifest " + manifest.getManifestNumber()
                            + " because shipments were already created from it");
        }
        manifestDao.deleteById(id);
    }

    private void attachItemsAndBilling(Manifest manifest) {
        List<ManifestItem> items = manifestItemDao.findByManifestId(manifest.getId());
        manifest.setItems(items);
        BigDecimal freight = BigDecimal.ZERO;
        for (ManifestItem item : items) {
            if (item.getFreightCharge() != null) {
                freight = freight.add(item.getFreightCharge());
            }
        }
        manifest.setTotalFreight(freight);
    }

    private void applyHeader(Manifest manifest, ManifestForm form, Party party) {
        if (form.getManifestDate() == null) {
            throw new IllegalArgumentException("Manifest date is required");
        }
        if (manifest.getDestinationBranchId() == null && form.getDestinationBranchId() != null) {
            requireDestinationBranch(form.getDestinationBranchId());
            manifest.setDestinationBranchId(form.getDestinationBranchId());
        }
        if (party != null) {
            manifest.setPartyId(party.getId());
            manifest.setPartyName(party.getPartyName());
        }
        manifest.setManifestDate(form.getManifestDate());
        manifest.setThroughName(trimToNull(form.getThroughName()));
        String origin = StringUtils.hasText(form.getOriginCity())
                ? form.getOriginCity().trim()
                : (party != null ? party.getCity() : manifest.getOriginCity());
        if (!StringUtils.hasText(origin)) {
            origin = "Indore";
        }
        manifest.setOriginCity(origin);
        String service = StringUtils.hasText(form.getServiceType())
                ? form.getServiceType().trim() : "DOMESTIC_STANDARD";
        manifest.setServiceType(service);
        manifest.setBillingLane(BranchCategory.normalizeLane(form.getBillingLane()));
        manifest.setRemarks(trimToNull(form.getRemarks()));
    }

    private Manifest appendLines(Long id, ManifestForm form, Long bookedByUserId) {
        Manifest existing = manifestDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Manifest not found"));
        List<ManifestItemForm> lines = collectFilledLines(form);
        if (lines.isEmpty()) {
            return findById(id).orElse(existing);
        }
        validateUniqueInForm(lines);
        Party party = resolvePartyForEdit(form, existing);
        applyHeader(existing, form, party);
        existing.setStatus(Manifest.STATUS_IN_PROGRESS);
        manifestDao.update(existing);
        int serial = manifestItemDao.nextSerial(id);
        for (ManifestItemForm line : lines) {
            if (line.getId() == null) {
                addOrAttachLine(existing, party, line, serial++, bookedByUserId, form.isBillUnmappedBySender());
            }
        }
        refreshTotals(id);
        return findById(id).orElse(existing);
    }

    public List<Map<String, String>> previewUnmapped(List<String> cnos) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (cnos == null) {
            return rows;
        }
        Set<String> seen = new HashSet<>();
        for (String raw : cnos) {
            String cno = normalizeConsignment(raw);
            if (!StringUtils.hasText(cno) || !seen.add(cno)) {
                continue;
            }
            Long number = PartyService.parseConsignment(cno);
            if (number == null || partyService.findOwner(number).isPresent()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("cno", String.valueOf(number));
            Optional<Shipment> shipment = shipmentDao.findByTrackingId(String.valueOf(number));
            if (shipment.isEmpty()) {
                shipment = shipmentDao.findByTrackingId(cno);
            }
            String sender = shipment.map(Shipment::getSenderName).orElse("");
            row.put("senderName", sender == null ? "" : sender.trim());
            rows.add(row);
        }
        return rows;
    }

    private void addOrAttachLine(Manifest manifest, Party party, ManifestItemForm line, int serial,
                                 Long bookedByUserId, boolean billUnmappedBySender) {
        if (line.getShipmentId() != null) {
            attachOpenShipment(manifest, line, serial, billUnmappedBySender);
            return;
        }
        String cno = normalizeConsignment(line.getConsignmentNo());
        if (StringUtils.hasText(cno)) {
            Long number = PartyService.parseConsignment(cno);
            boolean mapped = number != null && partyService.findOwner(number).isPresent();
            if (!mapped) {
                Optional<Shipment> existing = shipmentDao.findByTrackingId(cno);
                if (existing.isEmpty() && number != null) {
                    existing = shipmentDao.findByTrackingId(String.valueOf(number));
                }
                if (existing.isPresent()) {
                    line.setShipmentId(existing.get().getId());
                    attachOpenShipment(manifest, line, serial, billUnmappedBySender);
                    return;
                }
                if (billUnmappedBySender) {
                    createSenderShipment(manifest, line, serial, bookedByUserId);
                    return;
                }
            }
        }
        addDraftLine(manifest, party, line, serial, bookedByUserId);
    }

    /**
     * An unmapped C.No confirmed on the manifest becomes a shipment billed under the sender typed on the row.
     * Receiver, destination, weight, and boxes come from that same row.
     */
    private void createSenderShipment(Manifest manifest, ManifestItemForm line, int serial, Long bookedByUserId) {
        String cno = normalizeConsignment(line.getConsignmentNo());
        if (!StringUtils.hasText(line.getSenderName())) {
            throw new IllegalArgumentException(
                    "C.No " + cno + " is not assigned to a party and has no sender name. "
                            + "Enter the sender name on this row, or assign it on C.No mapping.");
        }
        if (manifestItemDao.existsByConsignmentNo(cno) || shipmentDao.existsByTrackingId(cno)) {
            throw new IllegalArgumentException("C.No " + cno + " is already used");
        }
        line.setPartyId(null);
        line.setConsignmentNo(cno);
        ManifestItem item = toItem(line, manifest.getId(), serial, manifest);
        Long itemId = manifestItemDao.save(item);
        item.setId(itemId);

        String origin = originOf(manifest, null);
        Shipment shipment = new Shipment();
        shipment.setTrackingId(cno);
        shipment.setSenderName(line.getSenderName().trim());
        shipment.setSenderPhone(phoneOrDash(line.getSenderPhone()));
        shipment.setSenderAddress(origin);
        shipment.setReceiverName(item.getReceiverName());
        shipment.setReceiverPhone(phoneOrDash(item.getReceiverPhone()));
        shipment.setReceiverAddress(item.getDestinationCity());
        shipment.setOriginCity(origin);
        shipment.setDestinationCity(item.getDestinationCity());
        shipment.setWeightKg(item.getWeightKg());
        shipment.setNumberOfBoxes(item.getNumberOfBoxes());
        shipment.setServiceType(manifest.getServiceType());
        shipment.setBookedByUserId(bookedByUserId);
        shipment.setCourierName(manifest.getThroughName());
        shipment.setPartyId(null);
        shipment.setManifestId(manifest.getId());
        shipment.setCodAmount(BigDecimal.ZERO);
        var quote = rateCardService.quote(
                item.getDestinationCity(), manifest.getBillingLane(),
                item.getWeightKg(), item.getNumberOfBoxes());
        shipment.setBillingLane(quote.getLane());
        shipment.setAssignedBranchId(quote.getBranchId());
        shipment.setFreightCharge(quote.getAmount());

        if (Manifest.STATUS_CREATED.equals(manifest.getStatus())) {
            shipment.setStatus("DISPATCHED");
            shipment = shipmentService.createFromManifest(
                    shipment, origin, "Dispatched on manifest " + manifest.getManifestNumber());
        } else {
            shipment.setStatus("BOOKED");
            shipment = shipmentService.persistNewShipment(
                    shipment, origin, "Booked on in-progress manifest " + manifest.getManifestNumber());
        }
        manifestItemDao.updateShipmentId(itemId, shipment.getId());
    }

    private void attachOpenShipment(Manifest manifest, ManifestItemForm line, int serial, boolean billUnmappedBySender) {
        Shipment shipment = shipmentDao.findById(line.getShipmentId())
                .orElseThrow(() -> new IllegalArgumentException("Open shipment not found"));
        if (!StringUtils.hasText(shipment.getSenderName()) && StringUtils.hasText(line.getSenderName())) {
            shipment.setSenderName(line.getSenderName().trim());
            shipment.setSenderPhone(phoneOrDash(line.getSenderPhone()));
        }
        if ("CANCELLED".equalsIgnoreCase(shipment.getStatus())) {
            throw new IllegalArgumentException("C.No " + shipment.getTrackingId() + " is cancelled");
        }
        if (shipment.getManifestId() != null && !shipment.getManifestId().equals(manifest.getId())) {
            throw new IllegalArgumentException(
                    "C.No " + shipment.getTrackingId() + " is already on another manifest");
        }
        if (manifestItemDao.existsByConsignmentNo(shipment.getTrackingId())) {
            throw new IllegalArgumentException("C.No " + shipment.getTrackingId() + " is already on a manifest");
        }

        if (!StringUtils.hasText(line.getConsignmentNo())) {
            line.setConsignmentNo(shipment.getTrackingId());
        }
        Long number = PartyService.parseConsignment(shipment.getTrackingId());
        if (number != null) {
            Optional<com.mahavircourier.model.ConsignmentRange> owner = partyService.findOwner(number);
            if (owner.isPresent()) {
                shipment.setPartyId(owner.get().getPartyId());
                line.setPartyId(owner.get().getPartyId());
            } else if (shipment.getPartyId() == null) {
                acceptSenderBill(shipment, line, billUnmappedBySender);
            } else {
                line.setPartyId(shipment.getPartyId());
            }
        } else if (shipment.getPartyId() != null) {
            line.setPartyId(shipment.getPartyId());
        } else {
            acceptSenderBill(shipment, line, billUnmappedBySender);
        }
        if (line.getDestinationBranchId() == null && shipment.getAssignedBranchId() != null) {
            line.setDestinationBranchId(shipment.getAssignedBranchId());
        }
        if (!StringUtils.hasText(line.getDestinationCity()) && StringUtils.hasText(shipment.getDestinationCity())) {
            line.setDestinationCity(shipment.getDestinationCity());
        }
        if (line.getNumberOfBoxes() == null) {
            line.setNumberOfBoxes(shipment.getNumberOfBoxes() != null ? shipment.getNumberOfBoxes() : 1);
        }
        if (line.getWeightKg() == null) {
            line.setWeightKg(shipment.getWeightKg());
        }
        if (!StringUtils.hasText(line.getReceiverName())) {
            line.setReceiverName(shipment.getReceiverName());
        }
        if (!StringUtils.hasText(line.getReceiverPhone())) {
            line.setReceiverPhone(shipment.getReceiverPhone());
        }

        ManifestItem item = toItem(line, manifest.getId(), serial, manifest);
        item.setConsignmentNo(shipment.getTrackingId());
        item.setShipmentId(shipment.getId());
        manifestItemDao.save(item);

        shipment.setManifestId(manifest.getId());
        shipmentDao.update(shipment);
    }

    private void acceptSenderBill(Shipment shipment, ManifestItemForm line, boolean billUnmappedBySender) {
        if (!billUnmappedBySender) {
            throw new IllegalArgumentException(
                    "C.No " + shipment.getTrackingId()
                            + " is not assigned to a party. Assign it on C.No mapping, then add it to the manifest.");
        }
        if (!StringUtils.hasText(shipment.getSenderName())) {
            throw new IllegalArgumentException(
                    "C.No " + shipment.getTrackingId()
                            + " has no sender name. Book the shipment with a sender, or assign the C.No to a party.");
        }
        shipment.setPartyId(null);
        line.setPartyId(null);
    }

    private void detachLine(ManifestItem item) {
        if (item.getShipmentId() != null) {
            shipmentDao.clearManifestId(item.getShipmentId());
        }
        manifestItemDao.deleteById(item.getId());
    }

    private void addDraftLine(Manifest manifest, Party party, ManifestItemForm line, int serial, Long bookedByUserId) {
        Party lineParty = resolveLineParty(line);
        String cno = partyService.resolveForBooking(lineParty.getId(), line.getConsignmentNo());
        line.setPartyId(lineParty.getId());
        line.setConsignmentNo(cno);
        ManifestItem item = toItem(line, manifest.getId(), serial, manifest);
        validateNewConsignment(lineParty.getId(), item.getConsignmentNo(), null);
        Long itemId = manifestItemDao.save(item);
        item.setId(itemId);

        Shipment shipment = buildShipment(manifest, lineParty, item, bookedByUserId);
        String location = originOf(manifest, lineParty);
        if (Manifest.STATUS_CREATED.equals(manifest.getStatus())) {
            shipment.setStatus("DISPATCHED");
            shipment = shipmentService.createFromManifest(
                    shipment, location, "Dispatched on manifest " + manifest.getManifestNumber());
            manifestBillService.refreshPendingIfPresent(manifest.getId());
        } else {
            shipment.setStatus("BOOKED");
            shipment = shipmentService.persistNewShipment(
                    shipment, location, "Booked on in-progress manifest " + manifest.getManifestNumber());
        }
        manifestItemDao.updateShipmentId(itemId, shipment.getId());
    }

    private Party resolvePartyForEdit(ManifestForm form, Manifest existing) {
        if (existing.getPartyId() != null && existing.getPartyId() <= 0) {
            existing.setPartyId(null);
        }
        Long partyId = form.getPartyId() != null && form.getPartyId() > 0
                ? form.getPartyId()
                : existing.getPartyId();
        if (partyId == null || partyId <= 0) {
            return null;
        }
        Party party = partyDao.findById(partyId).orElse(null);
        if (party == null) {
            existing.setPartyId(null);
            return null;
        }
        if (!party.isEnabled()) {
            throw new IllegalArgumentException("Party " + party.getPartyName() + " is disabled");
        }
        if (existing.getPartyId() != null && !party.getId().equals(existing.getPartyId())) {
            throw new IllegalArgumentException("Cannot change the sending party after a manifest is saved");
        }
        return party;
    }

    private Branch requireDestinationBranch(Long branchId) {
        if (branchId == null) {
            throw new IllegalArgumentException("Select the destination branch");
        }
        return branchDao.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Destination branch not found"));
    }

    private String resolveDestination(ManifestItemForm line, Manifest manifest) {
        if (StringUtils.hasText(line.getDestinationCity())) {
            return line.getDestinationCity().trim();
        }
        if (line.getDestinationBranchId() != null) {
            return requireDestinationBranch(line.getDestinationBranchId()).getCity();
        }
        throw new IllegalArgumentException("Destination city is required");
    }

    private void refreshTotals(Long manifestId) {
        int boxes = 0;
        BigDecimal weight = BigDecimal.ZERO;
        for (ManifestItem item : manifestItemDao.findByManifestId(manifestId)) {
            boxes += item.getNumberOfBoxes();
            if (item.getWeightKg() != null) {
                weight = weight.add(item.getWeightKg());
            }
        }
        manifestDao.updateTotals(manifestId, boxes, weight);
    }

    private String resolveManifestNumber(String requested) {
        if (!StringUtils.hasText(requested)) {
            return manifestDao.nextManifestNumber();
        }
        String number = requested.trim();
        if (manifestDao.existsByNumber(number)) {
            throw new IllegalArgumentException("Manifest number already exists: " + number);
        }
        return number;
    }

    private Party requireEnabledParty(Long partyId) {
        if (partyId == null) {
            throw new IllegalArgumentException("Select the sending party");
        }
        Party party = partyDao.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("Party not found"));
        if (!party.isEnabled()) {
            throw new IllegalArgumentException("Party " + party.getPartyName() + " is disabled");
        }
        return party;
    }

    private List<ManifestItemForm> collectFilledLines(ManifestForm form) {
        List<ManifestItemForm> filled = new ArrayList<>();
        if (form.getItems() == null) {
            return filled;
        }
        for (ManifestItemForm item : form.getItems()) {
            if (item == null) {
                continue;
            }
            boolean any = item.getShipmentId() != null
                    || StringUtils.hasText(item.getConsignmentNo())
                    || StringUtils.hasText(item.getDestinationCity())
                    || item.getDestinationBranchId() != null
                    || StringUtils.hasText(item.getReceiverName())
                    || item.getPartyId() != null
                    || item.getWeightKg() != null
                    || item.getNumberOfBoxes() != null;
            if (!any) {
                continue;
            }
            filled.add(item);
        }
        return filled;
    }

    private void validateUniqueInForm(List<ManifestItemForm> lines) {
        Set<String> seen = new HashSet<>();
        for (ManifestItemForm line : lines) {
            String cno = normalizeConsignment(line.getConsignmentNo());
            if (!StringUtils.hasText(cno)) {
                continue;
            }
            if (!seen.add(cno)) {
                throw new IllegalArgumentException("Duplicate consignment number on this manifest: " + cno);
            }
        }
    }

    private Party resolveLineParty(ManifestItemForm line) {
        if (!StringUtils.hasText(line.getConsignmentNo())) {
            throw new IllegalArgumentException(
                    "Enter the consignment number. The party is taken from the C.No mapping.");
        }
        Long number = PartyService.parseConsignment(line.getConsignmentNo().trim());
        if (number == null) {
            throw new IllegalArgumentException("Consignment number must be numeric");
        }
        Optional<com.mahavircourier.model.ConsignmentRange> owner = partyService.findOwner(number);
        if (owner.isEmpty()) {
            throw new IllegalArgumentException(
                    "C.No " + number + " is not assigned to a party. Assign it on C.No mapping, then add it here.");
        }
        return requireEnabledParty(owner.get().getPartyId());
    }

    private void validateNewConsignment(Long partyId, String consignmentNo, Long excludeItemId) {
        Long number = PartyService.parseConsignment(consignmentNo);
        if (number == null) {
            throw new IllegalArgumentException("Consignment number must be numeric: " + consignmentNo);
        }
        partyService.claimNumber(partyId, number);
        boolean usedOnManifest = excludeItemId == null
                ? manifestItemDao.existsByConsignmentNo(consignmentNo)
                : manifestItemDao.existsByConsignmentNoExcluding(consignmentNo, excludeItemId);
        if (usedOnManifest || shipmentDao.existsByTrackingId(consignmentNo)) {
            throw new IllegalArgumentException("C.No " + consignmentNo + " already belongs to another manifest");
        }
    }

    private ManifestItem toItem(ManifestItemForm line, Long manifestId, int serial, Manifest manifest) {
        ManifestItem item = new ManifestItem();
        item.setManifestId(manifestId);
        item.setSerialNo(serial);
        item.setConsignmentNo(normalizeConsignment(line.getConsignmentNo()));
        item.setDestinationCity(resolveDestination(line, manifest));
        item.setNumberOfBoxes(positiveBoxes(line.getNumberOfBoxes()));
        item.setWeightKg(positiveWeight(line.getWeightKg()));
        item.setReceiverName(required(line.getReceiverName(), "Receiver / details"));
        item.setReceiverPhone(trimToNull(line.getReceiverPhone()));
        return item;
    }

    private Shipment buildShipment(Manifest manifest, Party party, ManifestItem item, Long bookedByUserId) {
        String origin = originOf(manifest, party);
        String senderAddress = StringUtils.hasText(party.getAddress()) ? party.getAddress() : origin;
        String receiverPhone = StringUtils.hasText(item.getReceiverPhone())
                ? item.getReceiverPhone()
                : (StringUtils.hasText(party.getPhone()) ? party.getPhone() : "-");

        Shipment shipment = new Shipment();
        shipment.setTrackingId(item.getConsignmentNo());
        shipment.setSenderName(party.getPartyName());
        shipment.setSenderPhone(StringUtils.hasText(party.getPhone()) ? party.getPhone() : "-");
        shipment.setSenderAddress(senderAddress);
        shipment.setReceiverName(item.getReceiverName());
        shipment.setReceiverPhone(receiverPhone);
        shipment.setReceiverAddress(item.getDestinationCity());
        shipment.setOriginCity(origin);
        shipment.setDestinationCity(item.getDestinationCity());
        shipment.setWeightKg(item.getWeightKg());
        shipment.setServiceType(manifest.getServiceType());
        shipment.setStatus("DISPATCHED");
        shipment.setBookedByUserId(bookedByUserId);
        shipment.setCourierName(manifest.getThroughName());
        shipment.setPartyId(party.getId());
        shipment.setManifestId(manifest.getId());
        shipment.setNumberOfBoxes(item.getNumberOfBoxes());
        var quote = rateCardService.quote(
                item.getDestinationCity(), manifest.getBillingLane(),
                item.getWeightKg(), item.getNumberOfBoxes());
        shipment.setBillingLane(quote.getLane());
        shipment.setAssignedBranchId(quote.getBranchId());
        shipment.setFreightCharge(quote.getAmount());
        shipment.setCodAmount(BigDecimal.ZERO);
        return shipment;
    }

    private void applyTotals(Manifest manifest, List<ManifestItemForm> lines) {
        int boxes = 0;
        BigDecimal weight = BigDecimal.ZERO;
        for (ManifestItemForm line : lines) {
            boxes += positiveBoxes(line.getNumberOfBoxes());
            weight = weight.add(positiveWeight(line.getWeightKg()));
        }
        manifest.setTotalBoxes(boxes);
        manifest.setTotalWeight(weight);
    }

    private String originOf(Manifest manifest, Party party) {
        if (StringUtils.hasText(manifest.getOriginCity())) {
            return manifest.getOriginCity();
        }
        if (party != null && StringUtils.hasText(party.getCity())) {
            return party.getCity();
        }
        return manifest.getDestinationBranchCity() != null ? manifest.getDestinationBranchCity() : "Origin";
    }

    private String normalizeConsignment(String consignmentNo) {
        String value = required(consignmentNo, "Consignment number (C.No)");
        if (!value.matches("\\d+")) {
            throw new IllegalArgumentException("Consignment number must be numeric: " + value);
        }
        return value;
    }

    private String required(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private int positiveBoxes(Integer boxes) {
        if (boxes == null || boxes < 1) {
            throw new IllegalArgumentException("Number of boxes must be at least 1");
        }
        return boxes;
    }

    private BigDecimal positiveWeight(BigDecimal weight) {
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Weight must be greater than 0");
        }
        return weight;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String phoneOrDash(String phone) {
        return StringUtils.hasText(phone) ? phone.trim() : "-";
    }
}
