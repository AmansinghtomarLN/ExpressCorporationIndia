package com.mahavircourier.service;

import com.mahavircourier.dao.RateCardDao;
import com.mahavircourier.model.RateCard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class RateCardService {

    private final RateCardDao rateCardDao;

    public RateCardService(RateCardDao rateCardDao) {
        this.rateCardDao = rateCardDao;
    }

    public List<RateCard> findAll() {
        return rateCardDao.findAll();
    }

    public Optional<RateCard> findById(Long id) {
        return rateCardDao.findById(id);
    }

    @Transactional
    public RateCard save(RateCard card) {
        Long id = rateCardDao.save(card);
        card.setId(id);
        return card;
    }

    @Transactional
    public void update(RateCard card) {
        rateCardDao.update(card);
    }

    @Transactional
    public void delete(Long id) {
        rateCardDao.delete(id);
    }

    /**
     * Calculates freight as base_rate + max(0, weight - min_weight) * per_kg_rate.
     * Falls back to a simple default schedule when no rate card matches.
     */
    public BigDecimal calculateFreight(String serviceType, BigDecimal weight) {
        BigDecimal safeWeight = weight != null ? weight : BigDecimal.ONE;
        Optional<RateCard> cardOpt = rateCardDao.findActiveFor(serviceType, safeWeight);
        if (cardOpt.isPresent()) {
            RateCard card = cardOpt.get();
            BigDecimal excess = safeWeight.subtract(card.getMinWeightKg());
            if (excess.compareTo(BigDecimal.ZERO) < 0) {
                excess = BigDecimal.ZERO;
            }
            BigDecimal perKg = card.getPerKgRate() != null ? card.getPerKgRate() : BigDecimal.ZERO;
            return card.getBaseRate().add(excess.multiply(perKg)).setScale(2, RoundingMode.HALF_UP);
        }
        return defaultFreight(serviceType, safeWeight);
    }

    private BigDecimal defaultFreight(String serviceType, BigDecimal weight) {
        BigDecimal base = switch (serviceType != null ? serviceType : "") {
            case "DOMESTIC_EXPRESS" -> new BigDecimal("120.00");
            case "INTERNATIONAL" -> new BigDecimal("500.00");
            default -> new BigDecimal("80.00");
        };
        BigDecimal perKg = switch (serviceType != null ? serviceType : "") {
            case "DOMESTIC_EXPRESS" -> new BigDecimal("25.00");
            case "INTERNATIONAL" -> new BigDecimal("80.00");
            default -> new BigDecimal("15.00");
        };
        BigDecimal billable = weight.max(BigDecimal.ONE);
        return base.add(billable.subtract(BigDecimal.ONE).max(BigDecimal.ZERO).multiply(perKg))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
