package com.mahavircourier.service;

import com.mahavircourier.dao.InvoiceDao;
import com.mahavircourier.model.Invoice;
import com.mahavircourier.model.Shipment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class InvoiceService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InvoiceDao invoiceDao;

    public InvoiceService(InvoiceDao invoiceDao) {
        this.invoiceDao = invoiceDao;
    }

    public List<Invoice> list() {
        return invoiceDao.findAll();
    }

    public Optional<Invoice> findById(Long id) {
        return invoiceDao.findById(id);
    }

    public Optional<Invoice> findByShipmentId(Long shipmentId) {
        return invoiceDao.findByShipmentId(shipmentId);
    }

    public long countUnpaid() {
        return invoiceDao.countByStatus("UNPAID");
    }

    @Transactional
    public Invoice createForShipment(Shipment shipment) {
        Optional<Invoice> existing = invoiceDao.findByShipmentId(shipment.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        BigDecimal freight = shipment.getFreightCharge() != null
                ? shipment.getFreightCharge() : BigDecimal.ZERO;
        BigDecimal cod = shipment.getCodAmount() != null
                ? shipment.getCodAmount() : BigDecimal.ZERO;

        Invoice invoice = new Invoice();
        invoice.setShipmentId(shipment.getId());
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setFreightAmount(freight);
        invoice.setCodAmount(cod);
        invoice.setTotalAmount(freight.add(cod));
        invoice.setStatus(cod.compareTo(BigDecimal.ZERO) > 0 ? "UNPAID" : "UNPAID");
        invoice.setNotes("Auto-generated for " + shipment.getTrackingId());
        invoice.setTrackingId(shipment.getTrackingId());

        Long id = invoiceDao.save(invoice);
        invoice.setId(id);
        return invoice;
    }

    @Transactional
    public void markPaid(Long invoiceId) {
        invoiceDao.updateStatus(invoiceId, "PAID");
    }

    @Transactional
    public void markUnpaid(Long invoiceId) {
        invoiceDao.updateStatus(invoiceId, "UNPAID");
    }

    @Transactional
    public void markCodCollected(Long invoiceId) {
        invoiceDao.updateStatus(invoiceId, "COD_COLLECTED");
    }

    private String generateInvoiceNumber() {
        String day = LocalDate.now().format(DAY_FMT);
        String prefix = "INV-" + day + "-";
        int seq = invoiceDao.countTodayInvoices(prefix) + 1;
        return prefix + String.format("%04d", seq);
    }
}
