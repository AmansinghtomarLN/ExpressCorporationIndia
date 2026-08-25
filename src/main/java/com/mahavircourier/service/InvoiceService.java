package com.mahavircourier.service;

import com.mahavircourier.dao.InvoiceDao;
import com.mahavircourier.dto.FreightQuote;
import com.mahavircourier.model.Invoice;
import com.mahavircourier.model.Shipment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class InvoiceService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InvoiceDao invoiceDao;
    private final RateCardService rateCardService;

    public InvoiceService(InvoiceDao invoiceDao, RateCardService rateCardService) {
        this.invoiceDao = invoiceDao;
        this.rateCardService = rateCardService;
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

        Invoice invoice = new Invoice();
        invoice.setShipmentId(shipment.getId());
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setStatus("UNPAID");
        applyQuote(invoice, shipment);
        Long id = invoiceDao.save(invoice);
        invoice.setId(id);
        return invoice;
    }

    @Transactional
    public Invoice syncFromShipment(Shipment shipment) {
        Optional<Invoice> existing = invoiceDao.findByShipmentId(shipment.getId());
        if (existing.isEmpty()) {
            return createForShipment(shipment);
        }
        Invoice invoice = existing.get();
        applyQuote(invoice, shipment);
        invoiceDao.updateAmounts(invoice);
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

    private void applyQuote(Invoice invoice, Shipment shipment) {
        FreightQuote quote = rateCardService.quote(
                shipment.getDestinationCity(),
                shipment.getBillingLane(),
                shipment.getWeightKg(),
                shipment.getNumberOfBoxes());

        BigDecimal freight = shipment.getFreightCharge() != null
                ? shipment.getFreightCharge() : quote.getAmount();
        BigDecimal cod = shipment.getCodAmount() != null
                ? shipment.getCodAmount() : BigDecimal.ZERO;

        invoice.setFreightAmount(freight);
        invoice.setCodAmount(cod);
        invoice.setTotalAmount(freight.add(cod));
        invoice.setBilledBranchId(shipment.getAssignedBranchId() != null
                ? shipment.getAssignedBranchId() : quote.getBranchId());
        invoice.setWeightKg(shipment.getWeightKg());
        invoice.setNumberOfBoxes(shipment.getNumberOfBoxes());
        invoice.setPerKgRate(quote.getPerKgRate());
        invoice.setPerBoxRate(quote.getPerBoxRate());
        invoice.setTrackingId(shipment.getTrackingId());
        invoice.setNotes(buildNotes(shipment, quote));
    }

    private String buildNotes(Shipment shipment, FreightQuote quote) {
        String cno = StringUtils.hasText(shipment.getTrackingId())
                ? "C.No " + shipment.getTrackingId() : "Shipment";
        String branch = quote.getBranchName() != null
                ? quote.getBranchName()
                : (quote.getLane() != null ? quote.getLane() + " default" : "branch");
        BigDecimal kg = shipment.getWeightKg() != null ? shipment.getWeightKg() : BigDecimal.ZERO;
        int boxes = shipment.getNumberOfBoxes() != null ? shipment.getNumberOfBoxes() : 0;
        return cno + " · " + branch
                + " · " + kg.stripTrailingZeros().toPlainString() + " kg × Rs." + nvl(quote.getPerKgRate())
                + " + " + boxes + " box × Rs." + nvl(quote.getPerBoxRate());
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String generateInvoiceNumber() {
        String day = LocalDate.now().format(DAY_FMT);
        String prefix = "INV-" + day + "-";
        int seq = invoiceDao.countTodayInvoices(prefix) + 1;
        return prefix + String.format("%04d", seq);
    }
}
