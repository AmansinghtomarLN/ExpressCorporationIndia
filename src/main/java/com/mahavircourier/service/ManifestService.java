package com.mahavircourier.service;

import com.mahavircourier.dao.ConsignmentRangeDao;
import com.mahavircourier.dao.ManifestDao;
import com.mahavircourier.dao.ManifestItemDao;
import com.mahavircourier.dao.PartyDao;
import com.mahavircourier.dao.ShipmentDao;
import com.mahavircourier.dto.ManifestForm;
import com.mahavircourier.dto.ManifestItemForm;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ManifestService {

    private final ManifestDao manifestDao;
    private final ManifestItemDao manifestItemDao;
    private final PartyDao partyDao;
    private final ConsignmentRangeDao consignmentRangeDao;
    private final ShipmentDao shipmentDao;
    private final ShipmentService shipmentService;
    private final RateCardService rateCardService;

    public ManifestService(ManifestDao manifestDao,
                           ManifestItemDao manifestItemDao,
                           PartyDao partyDao,
                           ConsignmentRangeDao consignmentRangeDao,
                           ShipmentDao shipmentDao,
                           ShipmentService shipmentService,
                           RateCardService rateCardService) {
        this.manifestDao = manifestDao;
        this.manifestItemDao = manifestItemDao;
        this.partyDao = partyDao;
        this.consignmentRangeDao = consignmentRangeDao;
        this.shipmentDao = shipmentDao;
        this.shipmentService = shipmentService;
        this.rateCardService = rateCardService;
    }

    public List<Manifest> search(String query, Long partyId, LocalDate from, LocalDate to) {
        return manifestDao.search(query, partyId, from, to);
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
        if (!consignmentRangeDao.covers(party.getId(), number)) {
            throw new IllegalArgumentException(
                    "C.No " + normalized + " is not in " + party.getPartyName() + "'s allocated range");
        }
        if (manifestItemDao.existsByConsignmentNo(normalized) || shipmentDao.existsByTrackingId(normalized)) {
            throw new IllegalArgumentException("C.No " + normalized + " is already used on another manifest");
        }
        return "C.No " + normalized + " is available for " + party.getPartyName();
    }

    @Transactional
    public Manifest create(ManifestForm form, Long bookedByUserId) {
        List<ManifestItemForm> lines = collectFilledLines(form);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Add at least one consignment with C.No, destination, boxes, weight, and receiver");
        }
        Party party = requireEnabledParty(form.getPartyId());
        validateUniqueInForm(lines);

        Manifest manifest = new Manifest();
        applyHeader(manifest, form, party);
        manifest.setManifestNumber(resolveManifestNumber(form.getManifestNumber()));
        applyTotals(manifest, lines);

        Long manifestId = manifestDao.save(manifest);
        manifest.setId(manifestId);

        int serial = 1;
        BigDecimal freightTotal = BigDecimal.ZERO;
        List<ManifestItem> savedItems = new ArrayList<>();
        for (ManifestItemForm line : lines) {
            ManifestItem item = toItem(line, manifestId, serial++);
            validateNewConsignment(party.getId(), item.getConsignmentNo(), null);
            Long itemId = manifestItemDao.save(item);
            item.setId(itemId);

            Shipment shipment = buildShipment(manifest, party, item, bookedByUserId);
            shipment = shipmentService.createFromManifest(
                    shipment,
                    originOf(manifest, party),
                    "Dispatched on manifest " + manifest.getManifestNumber()
                            + (StringUtils.hasText(manifest.getThroughName())
                            ? " via " + manifest.getThroughName() : ""));
            manifestItemDao.updateShipmentId(itemId, shipment.getId());
            item.setShipmentId(shipment.getId());
            item.setFreightCharge(shipment.getFreightCharge());
            item.setShipmentStatus(shipment.getStatus());
            if (shipment.getFreightCharge() != null) {
                freightTotal = freightTotal.add(shipment.getFreightCharge());
            }
            savedItems.add(item);
        }
        manifest.setItems(savedItems);
        manifest.setTotalFreight(freightTotal);
        return manifest;
    }

    @Transactional
    public Manifest update(Long id, ManifestForm form) {
        Manifest existing = manifestDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Manifest not found"));
        List<ManifestItemForm> lines = collectFilledLines(form);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("A manifest must keep at least one consignment");
        }
        Party party = requireEnabledParty(form.getPartyId() != null ? form.getPartyId() : existing.getPartyId());
        if (!party.getId().equals(existing.getPartyId())) {
            throw new IllegalArgumentException("Cannot change the sending party after a manifest is saved");
        }
        validateUniqueInForm(lines);

        applyHeader(existing, form, party);
        existing.setManifestNumber(existing.getManifestNumber());
        applyTotals(existing, lines);
        manifestDao.update(existing);

        Set<Long> seenIds = new HashSet<>();
        int serial = 1;
        for (ManifestItemForm line : lines) {
            if (line.getId() != null) {
                ManifestItem item = manifestItemDao.findById(line.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Manifest line not found"));
                if (!item.getManifestId().equals(id)) {
                    throw new IllegalArgumentException("Line does not belong to this manifest");
                }
                item.setSerialNo(serial++);
                item.setDestinationCity(required(line.getDestinationCity(), "Destination"));
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
                            existing.getBillingLane());
                }
                seenIds.add(item.getId());
            } else {
                ManifestItem item = toItem(line, id, serial++);
                validateNewConsignment(party.getId(), item.getConsignmentNo(), null);
                Long itemId = manifestItemDao.save(item);
                item.setId(itemId);
                Shipment shipment = buildShipment(existing, party, item, null);
                shipment = shipmentService.createFromManifest(
                        shipment,
                        originOf(existing, party),
                        "Dispatched on manifest " + existing.getManifestNumber());
                manifestItemDao.updateShipmentId(itemId, shipment.getId());
                seenIds.add(itemId);
            }
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
        manifest.setPartyId(party.getId());
        manifest.setManifestDate(form.getManifestDate());
        manifest.setThroughName(trimToNull(form.getThroughName()));
        String origin = StringUtils.hasText(form.getOriginCity()) ? form.getOriginCity().trim() : party.getCity();
        manifest.setOriginCity(origin);
        String service = StringUtils.hasText(form.getServiceType())
                ? form.getServiceType().trim() : "DOMESTIC_STANDARD";
        manifest.setServiceType(service);
        manifest.setBillingLane(BranchCategory.normalizeLane(form.getBillingLane()));
        manifest.setRemarks(trimToNull(form.getRemarks()));
        manifest.setPartyName(party.getPartyName());
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
            throw new IllegalArgumentException("Select the sending party (M/S)");
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
            boolean any = StringUtils.hasText(item.getConsignmentNo())
                    || StringUtils.hasText(item.getDestinationCity())
                    || StringUtils.hasText(item.getReceiverName())
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
            if (!seen.add(cno)) {
                throw new IllegalArgumentException("Duplicate consignment number on this manifest: " + cno);
            }
        }
    }

    private void validateNewConsignment(Long partyId, String consignmentNo, Long excludeItemId) {
        Long number = PartyService.parseConsignment(consignmentNo);
        if (number == null) {
            throw new IllegalArgumentException("Consignment number must be numeric: " + consignmentNo);
        }
        if (!consignmentRangeDao.covers(partyId, number)) {
            throw new IllegalArgumentException("C.No " + consignmentNo + " is not allocated to this party");
        }
        boolean usedOnManifest = excludeItemId == null
                ? manifestItemDao.existsByConsignmentNo(consignmentNo)
                : manifestItemDao.existsByConsignmentNoExcluding(consignmentNo, excludeItemId);
        if (usedOnManifest || shipmentDao.existsByTrackingId(consignmentNo)) {
            throw new IllegalArgumentException("C.No " + consignmentNo + " already belongs to another manifest");
        }
    }

    private ManifestItem toItem(ManifestItemForm line, Long manifestId, int serial) {
        ManifestItem item = new ManifestItem();
        item.setManifestId(manifestId);
        item.setSerialNo(serial);
        item.setConsignmentNo(normalizeConsignment(line.getConsignmentNo()));
        item.setDestinationCity(required(line.getDestinationCity(), "Destination"));
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
                ? item.getReceiverPhone() : party.getPhone();

        Shipment shipment = new Shipment();
        shipment.setTrackingId(item.getConsignmentNo());
        shipment.setSenderName(party.getPartyName());
        shipment.setSenderPhone(party.getPhone());
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
        return party.getCity();
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
}
