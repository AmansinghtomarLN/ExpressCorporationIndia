package com.mahavircourier.service;

import com.mahavircourier.dao.ShipmentDao;
import com.mahavircourier.dao.TrackingEventDao;
import com.mahavircourier.dto.BookingForm;
import com.mahavircourier.dto.PageResult;
import com.mahavircourier.model.Shipment;
import com.mahavircourier.model.TrackingEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ShipmentService {

    private final ShipmentDao shipmentDao;
    private final TrackingEventDao trackingEventDao;
    private static final SecureRandom RANDOM = new SecureRandom();

    public ShipmentService(ShipmentDao shipmentDao, TrackingEventDao trackingEventDao) {
        this.shipmentDao = shipmentDao;
        this.trackingEventDao = trackingEventDao;
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

        Long id = shipmentDao.save(shipment);
        shipment.setId(id);

        TrackingEvent event = new TrackingEvent();
        event.setShipmentId(id);
        event.setStatus("BOOKED");
        event.setLocation(form.getOriginCity().trim());
        event.setRemarks("Shipment booked online. Awaiting pickup.");
        trackingEventDao.save(event);

        return shipment;
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

    /**
     * Admin action: append a new tracking event and update the shipment's
     * current status accordingly.
     */
    @Transactional
    public void addTrackingUpdate(Long shipmentId, String status, String location, String remarks) {
        TrackingEvent event = new TrackingEvent();
        event.setShipmentId(shipmentId);
        event.setStatus(status);
        event.setLocation(location);
        event.setRemarks(remarks);
        trackingEventDao.save(event);
        shipmentDao.updateStatus(shipmentId, status);
    }

    private String generateUniqueTrackingId() {
        String trackingId;
        do {
            // MH + 10 digits, matches the docket format customers already recognise
            trackingId = "MH" + String.format("%010d", Math.abs(RANDOM.nextInt(999_999_999)));
        } while (shipmentDao.existsByTrackingId(trackingId));
        return trackingId;
    }

    private LocalDate estimateDelivery(String serviceType) {
        int days = switch (serviceType) {
            case "DOMESTIC_EXPRESS" -> 2;
            case "INTERNATIONAL" -> 7;
            default -> 5; // DOMESTIC_STANDARD
        };
        return LocalDate.now().plusDays(days);
    }
}
