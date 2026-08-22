package com.mahavircourier.service;

import com.mahavircourier.dao.ShipmentDao;
import com.mahavircourier.dao.TrackingEventDao;
import com.mahavircourier.dto.AdminShipmentForm;
import com.mahavircourier.dto.BookingForm;
import com.mahavircourier.dto.DashboardStats;
import com.mahavircourier.dto.PageResult;
import com.mahavircourier.model.Shipment;
import com.mahavircourier.model.TrackingEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Service
public class ShipmentService {

    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShipmentDao shipmentDao;
    private final TrackingEventDao trackingEventDao;
    private final RateCardService rateCardService;
    private final InvoiceService invoiceService;
    private final NotificationService notificationService;
    private final ContactService contactService;

    public ShipmentService(ShipmentDao shipmentDao,
                           TrackingEventDao trackingEventDao,
                           RateCardService rateCardService,
                           InvoiceService invoiceService,
                           NotificationService notificationService,
                           ContactService contactService) {
        this.shipmentDao = shipmentDao;
        this.trackingEventDao = trackingEventDao;
        this.rateCardService = rateCardService;
        this.invoiceService = invoiceService;
        this.notificationService = notificationService;
        this.contactService = contactService;
    }

    /**
     * Books a new shipment: generates a unique tracking ID, persists the
     * shipment, and records the initial "BOOKED" tracking event - all in one
     * transaction so a shipment never exists without its first event.
     */
    @Transactional
    public Shipment bookShipment(BookingForm form, Long userId) {
        Shipment shipment = new Shipment();
        shipment.setTrackingId(generateUniqueTrackingId());
        shipment.setSenderName(form.getSenderName().trim());
        shipment.setSenderPhone(form.getSenderPhone().trim());
        shipment.setSenderAddress(form.getSenderAddress().trim());
        shipment.setReceiverName(form.getReceiverName().trim());
        shipment.setReceiverPhone(form.getReceiverPhone().trim());
        shipment.setReceiverAddress(form.getReceiverAddress().trim());
        shipment.setOriginCity(form.getOriginCity().trim());
        shipment.setDestinationCity(form.getDestinationCity().trim());
        shipment.setWeightKg(form.getWeightKg());
        shipment.setServiceType(form.getServiceType());
        shipment.setStatus("BOOKED");
        shipment.setBookedByUserId(userId);
        shipment.setExpectedDelivery(estimateDelivery(form.getServiceType()));

        BigDecimal freight = rateCardService.calculateFreight(form.getServiceType(), form.getWeightKg());
        shipment.setFreightCharge(freight);
        BigDecimal cod = form.getCodAmount() != null ? form.getCodAmount() : BigDecimal.ZERO;
        if (cod.compareTo(BigDecimal.ZERO) < 0) {
            cod = BigDecimal.ZERO;
        }
        shipment.setCodAmount(cod);

        Long id = shipmentDao.save(shipment);
        shipment.setId(id);

        TrackingEvent event = new TrackingEvent();
        event.setShipmentId(id);
        event.setStatus("BOOKED");
        event.setLocation(form.getOriginCity().trim());
        event.setRemarks("Shipment booked online. Awaiting pickup.");
        trackingEventDao.save(event);

        invoiceService.createForShipment(shipment);
        return shipment;
    }

    @Transactional
    public Shipment adminCreateShipment(AdminShipmentForm form, Long bookedByUserId) {
        Shipment shipment = new Shipment();
        shipment.setTrackingId(generateUniqueTrackingId());
        shipment.setSenderName(form.getSenderName().trim());
        shipment.setSenderPhone(form.getSenderPhone().trim());
        shipment.setSenderAddress(form.getSenderAddress().trim());
        shipment.setReceiverName(form.getReceiverName().trim());
        shipment.setReceiverPhone(form.getReceiverPhone().trim());
        shipment.setReceiverAddress(form.getReceiverAddress().trim());
        shipment.setOriginCity(form.getOriginCity().trim());
        shipment.setDestinationCity(form.getDestinationCity().trim());
        shipment.setWeightKg(form.getWeightKg());
        shipment.setServiceType(form.getServiceType());
        shipment.setStatus("BOOKED");
        shipment.setBookedByUserId(bookedByUserId);
        shipment.setExpectedDelivery(form.getExpectedDelivery() != null
                ? form.getExpectedDelivery()
                : estimateDelivery(form.getServiceType()));
        shipment.setAssignedBranchId(form.getAssignedBranchId());
        shipment.setAssignedHub(trimToNull(form.getAssignedHub()));
        shipment.setCourierName(trimToNull(form.getCourierName()));
        shipment.setCourierPhone(trimToNull(form.getCourierPhone()));

        BigDecimal freight = rateCardService.calculateFreight(form.getServiceType(), form.getWeightKg());
        shipment.setFreightCharge(freight);
        BigDecimal cod = form.getCodAmount() != null ? form.getCodAmount() : BigDecimal.ZERO;
        shipment.setCodAmount(cod.max(BigDecimal.ZERO));

        Long id = shipmentDao.save(shipment);
        shipment.setId(id);

        TrackingEvent event = new TrackingEvent();
        event.setShipmentId(id);
        event.setStatus("BOOKED");
        event.setLocation(form.getOriginCity().trim());
        event.setRemarks("Shipment created by admin.");
        trackingEventDao.save(event);

        invoiceService.createForShipment(shipment);
        return shipment;
    }

    /**
     * Creates a shipment from a saved manifest line. Manifest + consignment
     * number are required — operational shipments are not created without them.
     */
    @Transactional
    public Shipment createFromManifest(Shipment shipment, String eventLocation, String remarks) {
        if (shipment.getManifestId() == null) {
            throw new IllegalArgumentException("Shipment cannot be created without a manifest number");
        }
        if (shipment.getPartyId() == null) {
            throw new IllegalArgumentException("Shipment must belong to a sending party");
        }
        if (!StringUtils.hasText(shipment.getTrackingId())) {
            throw new IllegalArgumentException("Consignment / tracking number is required");
        }
        String trackingId = shipment.getTrackingId().trim();
        if (shipmentDao.existsByTrackingId(trackingId)) {
            throw new IllegalArgumentException("Consignment number already used as a shipment: " + trackingId);
        }
        shipment.setTrackingId(trackingId);
        if (!StringUtils.hasText(shipment.getStatus())) {
            shipment.setStatus("DISPATCHED");
        }
        if (!StringUtils.hasText(shipment.getServiceType())) {
            shipment.setServiceType("DOMESTIC_STANDARD");
        }
        if (shipment.getExpectedDelivery() == null) {
            shipment.setExpectedDelivery(estimateDelivery(shipment.getServiceType()));
        }
        if (shipment.getFreightCharge() == null) {
            shipment.setFreightCharge(rateCardService.calculateFreight(
                    shipment.getServiceType(), shipment.getWeightKg()));
        }
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

        invoiceService.createForShipment(shipment);
        return shipment;
    }

    @Transactional
    public void syncFromManifestItem(Long shipmentId, String receiverName, String receiverPhone,
                                     String destinationCity, BigDecimal weightKg, Integer boxes,
                                     String serviceType) {
        Shipment existing = shipmentDao.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found"));
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
            existing.setFreightCharge(rateCardService.calculateFreight(serviceType.trim(), weightKg));
        }
        shipmentDao.update(existing);
    }

    /**
     * Looks up a shipment by its public tracking ID and attaches the full
     * event history, most recent last, for display on the tracking page.
     */
    public Optional<Shipment> trackByTrackingId(String trackingId) {
        Optional<Shipment> shipmentOpt = shipmentDao.findByTrackingId(trackingId.trim().toUpperCase());
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
    public void addTrackingUpdate(Long shipmentId, String status, String location, String remarks) {
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

        TrackingEvent event = new TrackingEvent();
        event.setShipmentId(shipmentId);
        event.setStatus(normalizedStatus);
        event.setLocation(location.trim());
        event.setRemarks(remarks);
        trackingEventDao.save(event);
        shipmentDao.updateStatus(shipmentId, normalizedStatus);

        shipment.setStatus(normalizedStatus);
        notificationService.notifyStatusChange(shipment, normalizedStatus);
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
        stats.setUnpaidInvoices(invoiceService.countUnpaid());
        return stats;
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

    private String generateUniqueTrackingId() {
        String trackingId;
        do {
            trackingId = "MH" + String.format("%010d", Math.abs(RANDOM.nextInt(999_999_999)));
        } while (shipmentDao.existsByTrackingId(trackingId));
        return trackingId;
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
