package com.mahavircourier.service;

import com.mahavircourier.dao.ShipmentDao;
import com.mahavircourier.dao.TrackingEventDao;
import com.mahavircourier.dto.AdminShipmentForm;
import com.mahavircourier.dto.BookingForm;
import com.mahavircourier.dto.DashboardStats;
import com.mahavircourier.dto.PageResult;
import com.mahavircourier.model.Branch;
import com.mahavircourier.model.City;
import com.mahavircourier.model.Party;
import com.mahavircourier.model.Shipment;
import com.mahavircourier.model.TrackingEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Service
public class ShipmentService {

    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ShipmentDao shipmentDao;
    private final TrackingEventDao trackingEventDao;
    private final RateCardService rateCardService;
    private final NotificationService notificationService;
    private final ContactService contactService;
    private final BranchService branchService;
    private final PartyService partyService;
    private final CityService cityService;

    public ShipmentService(ShipmentDao shipmentDao,
                           TrackingEventDao trackingEventDao,
                           RateCardService rateCardService,
                           NotificationService notificationService,
                           ContactService contactService,
                           BranchService branchService,
                           PartyService partyService,
                           CityService cityService) {
        this.shipmentDao = shipmentDao;
        this.trackingEventDao = trackingEventDao;
        this.rateCardService = rateCardService;
        this.notificationService = notificationService;
        this.contactService = contactService;
        this.branchService = branchService;
        this.partyService = partyService;
        this.cityService = cityService;
    }

    /**
     * Books a new shipment: generates a unique tracking ID, persists the
     * shipment, and records the initial "BOOKED" tracking event - all in one
     * transaction so a shipment never exists without its first event.
     */
    @Transactional
    public Shipment bookShipment(BookingForm form, Long userId) {
        Branch branch = requireBranch(form.getDestinationBranchId());
        Shipment shipment = new Shipment();
        shipment.setTrackingId(nextGeneratedTrackingId());
        shipment.setSenderName(form.getSenderName().trim());
        shipment.setSenderPhone(form.getSenderPhone().trim());
        shipment.setSenderAddress(form.getSenderAddress().trim());
        shipment.setReceiverName(form.getReceiverName().trim());
        shipment.setReceiverPhone(form.getReceiverPhone().trim());
        shipment.setReceiverAddress(StringUtils.hasText(form.getReceiverAddress())
                ? form.getReceiverAddress().trim() : branch.getCity());
        shipment.setOriginCity(form.getOriginCity().trim());
        applyDestinationBranch(shipment, branch);
        shipment.setWeightKg(form.getWeightKg());
        shipment.setNumberOfBoxes(form.getNumberOfBoxes() != null && form.getNumberOfBoxes() > 0
                ? form.getNumberOfBoxes() : 1);
        shipment.setServiceType(form.getServiceType());
        shipment.setStatus("BOOKED");
        shipment.setBookedByUserId(userId);
        shipment.setCodAmount(form.getCodAmount() != null ? form.getCodAmount() : BigDecimal.ZERO);
        applyFreight(shipment);
        return persistNewShipment(shipment, shipment.getOriginCity(),
                "Booked to " + branch.getCity() + " — open until added to a manifest");
    }

    @Transactional
    public Shipment adminCreateShipment(AdminShipmentForm form, Long bookedByUserId) {
        String destinationCity = requireDestinationCity(form.getDestinationCity());
        form.setDestinationCity(destinationCity);
        if (form.isWalkIn()) {
            return bookWalkIn(form, bookedByUserId);
        }
        if (form.getPartyId() == null) {
            throw new IllegalArgumentException("Select the sending party, or choose Live booking");
        }
        form.setConsignmentNo(null);
        Party party = partyService.findById(form.getPartyId())
                .orElseThrow(() -> new IllegalArgumentException("Party not found"));
        String cno = partyService.resolveForBooking(party.getId(), form.getConsignmentNo());
        if (shipmentDao.existsByTrackingId(cno)) {
            throw new IllegalArgumentException("C.No " + cno + " is already used");
        }

        Shipment shipment = new Shipment();
        shipment.setTrackingId(cno);
        shipment.setSenderName(StringUtils.hasText(form.getSenderName()) ? form.getSenderName().trim() : party.getPartyName());
        String senderPhone = StringUtils.hasText(form.getSenderPhone()) ? form.getSenderPhone().trim() : party.getPhone();
        shipment.setSenderPhone(StringUtils.hasText(senderPhone) ? senderPhone.trim() : "-");
        String senderAddress = StringUtils.hasText(form.getSenderAddress()) ? form.getSenderAddress().trim()
                : (StringUtils.hasText(party.getAddress()) ? party.getAddress() : party.getCity());
        shipment.setSenderAddress(StringUtils.hasText(senderAddress) ? senderAddress : destinationCity);
        shipment.setReceiverName(form.getReceiverName().trim());
        shipment.setReceiverPhone(form.getReceiverPhone().trim());
        shipment.setReceiverAddress(StringUtils.hasText(form.getReceiverAddress())
                ? form.getReceiverAddress().trim() : destinationCity);
        shipment.setOriginCity(StringUtils.hasText(form.getOriginCity())
                ? form.getOriginCity().trim()
                : (StringUtils.hasText(party.getCity()) ? party.getCity() : destinationCity));
        applyDestinationCity(shipment, destinationCity);
        shipment.setWeightKg(form.getWeightKg());
        shipment.setNumberOfBoxes(form.getNumberOfBoxes() != null && form.getNumberOfBoxes() > 0
                ? form.getNumberOfBoxes() : 1);
        shipment.setServiceType(form.getServiceType());
        shipment.setStatus("BOOKED");
        shipment.setBookedByUserId(bookedByUserId);
        shipment.setPartyId(party.getId());
        shipment.setAssignedHub(form.getAssignedHub());
        shipment.setCourierName(form.getCourierName());
        shipment.setCourierPhone(form.getCourierPhone());
        shipment.setCodAmount(form.getCodAmount() != null ? form.getCodAmount() : BigDecimal.ZERO);
        shipment.setExpectedDelivery(form.getExpectedDelivery());
        applyFreight(shipment);
        return persistNewShipment(shipment, shipment.getOriginCity(),
                "Booked to " + destinationCity + " — open until added to a manifest");
    }

    private Shipment bookWalkIn(AdminShipmentForm form, Long bookedByUserId) {
        Shipment shipment = new Shipment();
        shipment.setTrackingId(partyService.requireUnassignedConsignment(form.getConsignmentNo()));
        shipment.setSenderName(form.getSenderName().trim());
        shipment.setSenderPhone(form.getSenderPhone().trim());
        shipment.setSenderAddress(form.getSenderAddress().trim());
        shipment.setReceiverName(form.getReceiverName().trim());
        shipment.setReceiverPhone(form.getReceiverPhone().trim());
        shipment.setReceiverAddress(StringUtils.hasText(form.getReceiverAddress())
                ? form.getReceiverAddress().trim() : form.getDestinationCity());
        shipment.setOriginCity(form.getOriginCity().trim());
        applyDestinationCity(shipment, form.getDestinationCity());
        shipment.setWeightKg(form.getWeightKg());
        shipment.setNumberOfBoxes(form.getNumberOfBoxes() != null && form.getNumberOfBoxes() > 0
                ? form.getNumberOfBoxes() : 1);
        shipment.setServiceType(form.getServiceType());
        shipment.setStatus("BOOKED");
        shipment.setBookedByUserId(bookedByUserId);
        shipment.setAssignedHub(form.getAssignedHub());
        shipment.setCourierName(form.getCourierName());
        shipment.setCourierPhone(form.getCourierPhone());
        shipment.setCodAmount(form.getCodAmount() != null ? form.getCodAmount() : BigDecimal.ZERO);
        shipment.setExpectedDelivery(form.getExpectedDelivery());
        applyFreight(shipment);
        return persistNewShipment(shipment, shipment.getOriginCity(),
                "Live booking — open until added to a manifest. Assign this C.No to a party before billing.");
    }

    public List<Shipment> findOpenUnmanifested() {
        return shipmentDao.findOpenUnmanifested();
    }

    @Transactional
    public Shipment persistNewShipment(Shipment shipment, String eventLocation, String remarks) {
        if (!StringUtils.hasText(shipment.getTrackingId())) {
            throw new IllegalArgumentException("Consignment / tracking number is required");
        }
        String trackingId = shipment.getTrackingId().trim();
        if (!trackingId.matches("\\d+")) {
            throw new IllegalArgumentException("Shipment number must be numeric");
        }
        if (shipmentDao.existsByTrackingId(trackingId)) {
            throw new IllegalArgumentException("Consignment number already used as a shipment: " + trackingId);
        }
        shipment.setTrackingId(trackingId);
        if (!StringUtils.hasText(shipment.getStatus())) {
            shipment.setStatus("BOOKED");
        }
        if (!StringUtils.hasText(shipment.getServiceType())) {
            shipment.setServiceType("DOMESTIC_STANDARD");
        }
        if (shipment.getExpectedDelivery() == null) {
            shipment.setExpectedDelivery(estimateDelivery(shipment.getServiceType()));
        }
        applyFreight(shipment);
        if (shipment.getCodAmount() == null) {
            shipment.setCodAmount(BigDecimal.ZERO);
        }

        Long id = shipmentDao.save(shipment);
        shipment.setId(id);

        TrackingEvent event = new TrackingEvent();
        event.setShipmentId(id);
        event.setStatus(shipment.getStatus());
        event.setLocation(StringUtils.hasText(eventLocation) ? eventLocation.trim() : shipment.getOriginCity());
        event.setRemarks(remarks);
        trackingEventDao.save(event);
        return shipment;
    }

    @Transactional
    public Shipment createFromManifest(Shipment shipment, String eventLocation, String remarks) {
        if (shipment.getManifestId() == null) {
            throw new IllegalArgumentException("Shipment cannot be created without a manifest number");
        }
        shipment = persistNewShipment(shipment, eventLocation, remarks);
        return shipment;
    }

    @Transactional
    public void markDispatchedFromManifest(Long shipmentId, String location, String remarks) {
        Shipment shipment = shipmentDao.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found"));
        if (!"DISPATCHED".equals(shipment.getStatus())) {
            shipment.setStatus("DISPATCHED");
            shipmentDao.update(shipment);
            TrackingEvent event = new TrackingEvent();
            event.setShipmentId(shipmentId);
            event.setStatus("DISPATCHED");
            event.setLocation(StringUtils.hasText(location) ? location.trim() : shipment.getOriginCity());
            event.setRemarks(remarks);
            trackingEventDao.save(event);
        }
    }

    @Transactional
    public void syncFromManifestItem(Long shipmentId, String receiverName, String receiverPhone,
                                     String destinationCity, BigDecimal weightKg, Integer boxes,
                                     String serviceType, String manifestLane,
                                     String senderName, String senderPhone) {
        Shipment existing = shipmentDao.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found"));
        if (StringUtils.hasText(senderName)) {
            existing.setSenderName(senderName.trim());
        }
        if (StringUtils.hasText(senderPhone)) {
            existing.setSenderPhone(senderPhone.trim());
        }
        existing.setReceiverName(receiverName);
        if (StringUtils.hasText(receiverPhone)) {
            existing.setReceiverPhone(receiverPhone.trim());
        }
        existing.setDestinationCity(destinationCity);
        existing.setReceiverAddress(destinationCity);
        existing.setWeightKg(weightKg);
        existing.setNumberOfBoxes(boxes);
        if (StringUtils.hasText(serviceType)) {
            existing.setServiceType(serviceType.trim());
        }
        var quote = rateCardService.quote(destinationCity, manifestLane, weightKg, boxes);
        existing.setBillingLane(quote.getLane());
        existing.setAssignedBranchId(quote.getBranchId());
        existing.setFreightCharge(quote.getAmount());
        shipmentDao.update(existing);
    }

    /**
     * Looks up a shipment by its public tracking ID and attaches the full
     * event history, most recent last, for display on the tracking page.
     */
    public Optional<Shipment> trackByTrackingId(String trackingId) {
        String raw = trackingId.trim();
        Optional<Shipment> shipmentOpt = shipmentDao.findByTrackingId(raw);
        if (shipmentOpt.isEmpty() && !raw.equals(raw.toUpperCase())) {
            shipmentOpt = shipmentDao.findByTrackingId(raw.toUpperCase());
        }
        shipmentOpt.ifPresent(s -> s.setEvents(trackingEventDao.findByShipmentId(s.getId())));
        return shipmentOpt;
    }

    public List<Shipment> findByUser(Long userId) {
        return shipmentDao.findByBookedByUserId(userId);
    }

    public List<Shipment> findAll() {
        return shipmentDao.findAll();
    }

    public List<Shipment> findRecent(int limit) {
        return shipmentDao.findRecent(limit);
    }

    /**
     * Admin shipment list with optional search, status filter, and pagination.
     * {@code page} is 1-based; {@code size} is clamped to a safe range.
     */
    public PageResult<Shipment> searchForAdmin(String query, String status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 5), 100);
        int safePage = Math.max(page, 1);
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        String normalizedStatus = StringUtils.hasText(status) ? status.trim() : null;

        long total = shipmentDao.countSearch(normalizedQuery, normalizedStatus);
        int totalPages = safeSize == 0 ? 0 : (int) Math.ceil((double) total / (double) safeSize);
        if (totalPages > 0 && safePage > totalPages) {
            safePage = totalPages;
        }
        int offset = (safePage - 1) * safeSize;
        List<Shipment> content = total == 0
                ? List.of()
                : shipmentDao.search(normalizedQuery, normalizedStatus, safeSize, offset);
        return new PageResult<>(content, safePage, safeSize, total);
    }

    public Optional<Shipment> findById(Long id) {
        Optional<Shipment> shipmentOpt = shipmentDao.findById(id);
        shipmentOpt.ifPresent(s -> s.setEvents(trackingEventDao.findByShipmentId(s.getId())));
        return shipmentOpt;
    }

    @Transactional
    public void updateShipmentDetails(Shipment fields) {
        Shipment existing = shipmentDao.findById(fields.getId())
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found"));
        if (StringUtils.hasText(fields.getSenderName())) {
            existing.setSenderName(fields.getSenderName().trim());
        }
        if (StringUtils.hasText(fields.getSenderPhone())) {
            existing.setSenderPhone(fields.getSenderPhone().trim());
        }
        if (StringUtils.hasText(fields.getSenderAddress())) {
            existing.setSenderAddress(fields.getSenderAddress().trim());
        }
        if (StringUtils.hasText(fields.getReceiverName())) {
            existing.setReceiverName(fields.getReceiverName().trim());
        }
        if (StringUtils.hasText(fields.getReceiverPhone())) {
            existing.setReceiverPhone(fields.getReceiverPhone().trim());
        }
        if (StringUtils.hasText(fields.getReceiverAddress())) {
            existing.setReceiverAddress(fields.getReceiverAddress().trim());
        }
        if (StringUtils.hasText(fields.getOriginCity())) {
            existing.setOriginCity(fields.getOriginCity().trim());
        }
        if (StringUtils.hasText(fields.getDestinationCity())) {
            existing.setDestinationCity(fields.getDestinationCity().trim());
        }
        if (fields.getWeightKg() != null) {
            existing.setWeightKg(fields.getWeightKg());
        }
        if (StringUtils.hasText(fields.getServiceType())) {
            existing.setServiceType(fields.getServiceType().trim());
        }
        if (fields.getExpectedDelivery() != null) {
            existing.setExpectedDelivery(fields.getExpectedDelivery());
        }
        if (fields.getFreightCharge() != null) {
            existing.setFreightCharge(fields.getFreightCharge());
        }
        if (fields.getCodAmount() != null) {
            existing.setCodAmount(fields.getCodAmount());
        }
        shipmentDao.update(existing);
    }

    @Transactional
    public void assignResources(Long shipmentId, Long branchId, String hub,
                                String courierName, String courierPhone) {
        Shipment shipment = shipmentDao.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found"));
        shipment.setAssignedBranchId(branchId);
        shipment.setAssignedHub(trimToNull(hub));
        shipment.setCourierName(trimToNull(courierName));
        shipment.setCourierPhone(trimToNull(courierPhone));
        shipmentDao.update(shipment);
    }

    /**
     * Admin action: append a new tracking event and update the shipment's
     * current status accordingly.
     */
    @Transactional
    public boolean addTrackingUpdate(Long shipmentId, String status, String location, String remarks) {
        if (!StringUtils.hasText(location)) {
            throw new IllegalArgumentException("Location is required");
        }
        String normalizedStatus = status != null ? status.trim().toUpperCase() : "";
        if (!StatusTransitions.ALL_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }

        Shipment shipment = shipmentDao.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found"));
        StatusTransitions.validate(shipment.getStatus(), normalizedStatus);
        if (isDuplicateTrackingUpdate(shipmentId, normalizedStatus, location.trim(), remarks)) {
            return false;
        }

        TrackingEvent event = new TrackingEvent();
        event.setShipmentId(shipmentId);
        event.setStatus(normalizedStatus);
        event.setLocation(location.trim());
        event.setRemarks(remarks);
        trackingEventDao.save(event);
        shipmentDao.updateStatus(shipmentId, normalizedStatus);

        shipment.setStatus(normalizedStatus);
        notificationService.notifyStatusChange(shipment, normalizedStatus);
        return true;
    }

    /**
     * A retried save (double click or a network retry) must not add the same
     * journey step again.
     */
    private boolean isDuplicateTrackingUpdate(Long shipmentId, String status, String location, String remarks) {
        List<TrackingEvent> events = trackingEventDao.findByShipmentId(shipmentId);
        if (events.isEmpty()) {
            return false;
        }
        TrackingEvent latest = events.get(events.size() - 1);
        if (latest.getEventTime() == null
                || latest.getEventTime().isBefore(java.time.LocalDateTime.now().minusMinutes(10))) {
            return false;
        }
        String latestRemarks = latest.getRemarks() == null ? "" : latest.getRemarks().trim();
        String incomingRemarks = remarks == null ? "" : remarks.trim();
        return status.equalsIgnoreCase(latest.getStatus())
                && location.equalsIgnoreCase(latest.getLocation() == null ? "" : latest.getLocation().trim())
                && latestRemarks.equalsIgnoreCase(incomingRemarks);
    }

    @Transactional
    public void cancelShipment(Long id) {
        Shipment shipment = shipmentDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found"));
        if ("DELIVERED".equals(shipment.getStatus())) {
            throw new IllegalArgumentException("Cannot cancel a delivered shipment");
        }
        if ("CANCELLED".equals(shipment.getStatus())) {
            return;
        }
        TrackingEvent event = new TrackingEvent();
        event.setShipmentId(id);
        event.setStatus("CANCELLED");
        event.setLocation(shipment.getOriginCity() != null ? shipment.getOriginCity() : "Origin");
        event.setRemarks("Shipment cancelled by admin.");
        trackingEventDao.save(event);
        shipmentDao.updateStatus(id, "CANCELLED");
        shipment.setStatus("CANCELLED");
        notificationService.notifyStatusChange(shipment, "CANCELLED");
    }

    @Transactional
    public void updateTrackingEvent(Long shipmentId, Long eventId, String status,
                                    String location, String remarks) {
        TrackingEvent event = trackingEventDao.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        if (!event.getShipmentId().equals(shipmentId)) {
            throw new IllegalArgumentException("Event does not belong to this shipment");
        }
        if (!StringUtils.hasText(location)) {
            throw new IllegalArgumentException("Location is required");
        }
        String normalizedStatus = status != null ? status.trim().toUpperCase() : "";
        if (!StatusTransitions.ALL_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
        event.setStatus(normalizedStatus);
        event.setLocation(location.trim());
        event.setRemarks(remarks);
        trackingEventDao.update(event);

        List<TrackingEvent> events = trackingEventDao.findByShipmentId(shipmentId);
        if (!events.isEmpty()) {
            TrackingEvent latest = events.get(events.size() - 1);
            if (latest.getId().equals(eventId)) {
                shipmentDao.updateStatus(shipmentId, normalizedStatus);
            }
        }
    }

    @Transactional
    public void deleteTrackingEvent(Long shipmentId, Long eventId) {
        TrackingEvent event = trackingEventDao.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        if (!event.getShipmentId().equals(shipmentId)) {
            throw new IllegalArgumentException("Event does not belong to this shipment");
        }
        List<TrackingEvent> events = trackingEventDao.findByShipmentId(shipmentId);
        boolean isLatest = !events.isEmpty() && events.get(events.size() - 1).getId().equals(eventId);
        trackingEventDao.deleteById(eventId);

        if (isLatest) {
            List<TrackingEvent> remaining = trackingEventDao.findByShipmentId(shipmentId);
            String newStatus = remaining.isEmpty() ? "BOOKED" : remaining.get(remaining.size() - 1).getStatus();
            shipmentDao.updateStatus(shipmentId, newStatus);
        }
    }

    public DashboardStats countStats() {
        DashboardStats stats = new DashboardStats();
        stats.setTotalShipments(shipmentDao.countAll());
        stats.setBooked(shipmentDao.countByStatus("BOOKED"));
        stats.setInTransit(shipmentDao.countByStatus("IN_TRANSIT")
                + shipmentDao.countByStatus("PICKED_UP")
                + shipmentDao.countByStatus("AT_HUB"));
        stats.setOutForDelivery(shipmentDao.countByStatus("OUT_FOR_DELIVERY"));
        stats.setDelivered(shipmentDao.countByStatus("DELIVERED"));
        stats.setCancelled(shipmentDao.countByStatus("CANCELLED"));
        stats.setDelayed(shipmentDao.countDelayed());
        stats.setUnreadContacts(contactService.countUnread());
        return stats;
    }

    public DashboardStats countStats(LocalDate from, LocalDate to) {
        DashboardStats stats = new DashboardStats();
        stats.setTotalShipments(shipmentDao.countCreatedBetween(from, to));
        stats.setBooked(shipmentDao.countByStatusCreatedBetween("BOOKED", from, to));
        stats.setInTransit(shipmentDao.countByStatusCreatedBetween("IN_TRANSIT", from, to)
                + shipmentDao.countByStatusCreatedBetween("PICKED_UP", from, to)
                + shipmentDao.countByStatusCreatedBetween("AT_HUB", from, to));
        stats.setOutForDelivery(shipmentDao.countByStatusCreatedBetween("OUT_FOR_DELIVERY", from, to));
        stats.setDelivered(shipmentDao.countByStatusCreatedBetween("DELIVERED", from, to));
        stats.setCancelled(shipmentDao.countByStatusCreatedBetween("CANCELLED", from, to));
        stats.setDelayed(shipmentDao.countDelayedCreatedBetween(from, to));
        stats.setUnreadContacts(contactService.countUnreadBetween(from, to));
        return stats;
    }

    public List<Shipment> findRecent(LocalDate from, LocalDate to, int limit) {
        return shipmentDao.findRecentBetween(from, to, limit);
    }

    /**
     * Bulk update from CSV lines. Expected format:
     * trackingId,status,location,remarks
     * Returns number of successfully updated rows.
     */
    @Transactional
    public int bulkUpdateFromCsv(List<String> lines) {
        int updated = 0;
        for (String line : lines) {
            if (!StringUtils.hasText(line) || line.trim().startsWith("#")
                    || line.toLowerCase().startsWith("tracking")) {
                continue;
            }
            String[] parts = line.split(",", -1);
            if (parts.length < 3) {
                continue;
            }
            String trackingId = parts[0].trim().toUpperCase();
            String status = parts[1].trim().toUpperCase();
            String location = parts[2].trim();
            String remarks = parts.length > 3 ? parts[3].trim() : null;
            Optional<Shipment> opt = shipmentDao.findByTrackingId(trackingId);
            if (opt.isEmpty()) {
                continue;
            }
            try {
                addTrackingUpdate(opt.get().getId(), status, location, remarks);
                updated++;
            } catch (IllegalArgumentException ignored) {
                // skip invalid transitions
            }
        }
        return updated;
    }

    public String exportCsv() {
        List<Shipment> shipments = shipmentDao.findAllForExport();
        StringBuilder sb = new StringBuilder();
        sb.append("tracking_id,status,sender_name,sender_phone,receiver_name,receiver_phone,")
                .append("origin_city,destination_city,weight_kg,service_type,expected_delivery,")
                .append("freight_charge,cod_amount,assigned_hub,courier_name,created_at\n");
        for (Shipment s : shipments) {
            sb.append(csv(s.getTrackingId())).append(',')
                    .append(csv(s.getStatus())).append(',')
                    .append(csv(s.getSenderName())).append(',')
                    .append(csv(s.getSenderPhone())).append(',')
                    .append(csv(s.getReceiverName())).append(',')
                    .append(csv(s.getReceiverPhone())).append(',')
                    .append(csv(s.getOriginCity())).append(',')
                    .append(csv(s.getDestinationCity())).append(',')
                    .append(s.getWeightKg() != null ? s.getWeightKg() : "").append(',')
                    .append(csv(s.getServiceType())).append(',')
                    .append(s.getExpectedDelivery() != null ? s.getExpectedDelivery().format(CSV_DATE) : "").append(',')
                    .append(s.getFreightCharge() != null ? s.getFreightCharge() : "").append(',')
                    .append(s.getCodAmount() != null ? s.getCodAmount() : "").append(',')
                    .append(csv(s.getAssignedHub())).append(',')
                    .append(csv(s.getCourierName())).append(',')
                    .append(s.getCreatedAt() != null ? s.getCreatedAt() : "")
                    .append('\n');
        }
        return sb.toString();
    }

    public LocalDate parseOptionalDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date: " + value);
        }
    }

    private Branch requireBranch(Long branchId) {
        if (branchId == null) {
            throw new IllegalArgumentException("Select a destination branch");
        }
        return branchService.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Destination branch not found"));
    }

    private String requireDestinationCity(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("Select a destination city");
        }
        return cityService.findByName(raw.trim())
                .map(City::getCityName)
                .orElseThrow(() -> new IllegalArgumentException("Select a destination city from the list"));
    }

    private void applyDestinationCity(Shipment shipment, String city) {
        shipment.setDestinationCity(city);
        branchService.findByCity(city).ifPresent(branch -> {
            shipment.setAssignedBranchId(branch.getId());
            shipment.setBillingLane(StringUtils.hasText(branch.getBranchCategory())
                    ? branch.getBranchCategory() : "DOMESTIC");
        });
    }

    private void applyDestinationBranch(Shipment shipment, Branch branch) {
        shipment.setAssignedBranchId(branch.getId());
        shipment.setDestinationCity(branch.getCity());
        shipment.setBillingLane(StringUtils.hasText(branch.getBranchCategory())
                ? branch.getBranchCategory() : "DOMESTIC");
    }

    private void applyFreight(Shipment shipment) {
        if (shipment.getFreightCharge() != null) {
            return;
        }
        var quote = rateCardService.quote(
                shipment.getDestinationCity(),
                shipment.getBillingLane(),
                shipment.getWeightKg(),
                shipment.getNumberOfBoxes());
        shipment.setFreightCharge(quote.getAmount());
        if (shipment.getAssignedBranchId() == null) {
            shipment.setAssignedBranchId(quote.getBranchId());
        }
        if (!StringUtils.hasText(shipment.getBillingLane())) {
            shipment.setBillingLane(quote.getLane());
        }
    }

    private String nextGeneratedTrackingId() {
        String prefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        for (int i = 1; i < 10000; i++) {
            String candidate = prefix + String.format("%04d", i);
            if (!shipmentDao.existsByTrackingId(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Could not generate a tracking number for today");
    }

    private LocalDate estimateDelivery(String serviceType) {
        int days = switch (serviceType) {
            case "DOMESTIC_EXPRESS" -> 2;
            case "INTERNATIONAL" -> 7;
            default -> 5;
        };
        return LocalDate.now().plusDays(days);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
