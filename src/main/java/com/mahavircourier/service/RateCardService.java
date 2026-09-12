package com.mahavircourier.service;

import com.mahavircourier.dao.BillingTariffDao;
import com.mahavircourier.dao.BranchDao;
import com.mahavircourier.dao.RateCardDao;
import com.mahavircourier.dto.FreightQuote;
import com.mahavircourier.model.BillingTariff;
import com.mahavircourier.model.Branch;
import com.mahavircourier.model.Party;
import com.mahavircourier.model.RateCard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class RateCardService {

    private final RateCardDao rateCardDao;
    private final BillingTariffDao billingTariffDao;
    private final BranchDao branchDao;

    public RateCardService(RateCardDao rateCardDao,
                           BillingTariffDao billingTariffDao,
                           BranchDao branchDao) {
        this.rateCardDao = rateCardDao;
        this.billingTariffDao = billingTariffDao;
        this.branchDao = branchDao;
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

    public List<BillingTariff> findTariffs() {
        return billingTariffDao.findAll();
    }

    public Optional<BillingTariff> findTariff(String lane) {
        return billingTariffDao.findByLane(lane);
    }

    @Transactional
    public void saveTariff(BillingTariff tariff) {
        String lane = tariff.getLaneType() != null ? tariff.getLaneType().trim().toUpperCase() : "";
        if (!BranchCategory.DOMESTIC.equals(lane) && !BranchCategory.NATIONAL.equals(lane)) {
            throw new IllegalArgumentException("Tariff lane must be DOMESTIC or NATIONAL");
        }
        tariff.setLaneType(lane);
        tariff.setMinCharge(BigDecimal.ZERO);
        tariff.setBaseRate(BigDecimal.ZERO);
        tariff.setPerKgRate(nvl(tariff.getPerKgRate()));
        tariff.setPerBoxRate(nvl(tariff.getPerBoxRate()));
        billingTariffDao.upsert(tariff);
        branchDao.applyRatesByCategory(lane, tariff.getPerKgRate(), tariff.getPerBoxRate());
    }

    @Transactional
    public void saveBranchRates(Long branchId, BigDecimal perKgRate, BigDecimal perBoxRate) {
        if (branchDao.findById(branchId).isEmpty()) {
            throw new IllegalArgumentException("Branch not found");
        }
        branchDao.updateRates(branchId, nvl(perKgRate), nvl(perBoxRate));
    }

    /**
     * Resolves billing lane from destination city → matching branch category.
     * Unmatched cities default to Domestic (MP).
     */
    public String resolveLane(String destinationCity) {
        if (!StringUtils.hasText(destinationCity)) {
            return BranchCategory.DOMESTIC;
        }
        Optional<Branch> branch = branchDao.findByCityIgnoreCase(destinationCity.trim());
        if (branch.isPresent() && StringUtils.hasText(branch.get().getBranchCategory())) {
            return branch.get().getBranchCategory();
        }
        return BranchCategory.DOMESTIC;
    }

    public String resolveLaneForManifest(String manifestLane, String destinationCity) {
        String lane = BranchCategory.normalizeLane(manifestLane);
        if (BranchCategory.AUTO.equals(lane)) {
            return resolveLane(destinationCity);
        }
        return lane;
    }

    /**
     * Freight = weight × branch per-kg + boxes × branch per-box.
     * Destination city maps to that branch's rates. Unmatched cities use the
     * Domestic/National category defaults. Service type is never billed.
     */
    public FreightQuote quote(String destinationCity, String manifestLane, BigDecimal weight, Integer boxes) {
        Optional<Branch> destBranch = StringUtils.hasText(destinationCity)
                ? branchDao.findByCityIgnoreCase(destinationCity.trim())
                : Optional.empty();

        String requested = BranchCategory.normalizeLane(manifestLane);
        String lane;
        if (BranchCategory.AUTO.equals(requested)) {
            lane = destBranch
                    .map(b -> StringUtils.hasText(b.getBranchCategory())
                            ? b.getBranchCategory() : BranchCategory.DOMESTIC)
                    .orElse(BranchCategory.DOMESTIC);
        } else {
            lane = requested;
        }

        FreightQuote quote = new FreightQuote();
        quote.setLane(lane);

        if (destBranch.isPresent()) {
            Branch branch = destBranch.get();
            quote.setBranchId(branch.getId());
            quote.setBranchName(branch.getBranchName());
            quote.setBranchCity(branch.getCity());
            quote.setPerKgRate(nvl(branch.getPerKgRate()));
            quote.setPerBoxRate(nvl(branch.getPerBoxRate()));
        } else {
            BillingTariff tariff = billingTariffDao.findByLane(lane).orElse(null);
            quote.setPerKgRate(tariff != null ? nvl(tariff.getPerKgRate()) : BigDecimal.ZERO);
            quote.setPerBoxRate(tariff != null ? nvl(tariff.getPerBoxRate()) : BigDecimal.ZERO);
        }
        quote.setAmount(computeFreight(weight, boxes, quote.getPerKgRate(), quote.getPerBoxRate()));
        return quote;
    }

    /**
     * Party bill rates: use the party's optional per-kg / per-box when either is set.
     * Otherwise fall back to the destination branch, then category tariff.
     */
    public FreightQuote quoteForParty(Party party, Long destBranchId, String lane,
                                      BigDecimal weight, Integer boxes) {
        FreightQuote fallback = destBranchId != null
                ? quoteBranch(destBranchId, weight, boxes)
                : quote(null, lane, weight, boxes);
        if (party == null || !party.hasCustomRates()) {
            return fallback;
        }
        FreightQuote quote = new FreightQuote();
        quote.setLane(fallback.getLane());
        quote.setBranchId(fallback.getBranchId());
        quote.setBranchName(fallback.getBranchName());
        quote.setBranchCity(fallback.getBranchCity());
        quote.setPerKgRate(nvl(party.getPerKgRate()));
        quote.setPerBoxRate(nvl(party.getPerBoxRate()));
        quote.setAmount(computeFreight(weight, boxes, quote.getPerKgRate(), quote.getPerBoxRate()));
        return quote;
    }

    public FreightQuote quoteBranch(Long branchId, BigDecimal weight, Integer boxes) {
        Branch branch = branchDao.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found"));
        FreightQuote quote = new FreightQuote();
        quote.setBranchId(branch.getId());
        quote.setBranchName(branch.getBranchName());
        quote.setBranchCity(branch.getCity());
        quote.setLane(StringUtils.hasText(branch.getBranchCategory())
                ? branch.getBranchCategory() : BranchCategory.DOMESTIC);
        quote.setPerKgRate(nvl(branch.getPerKgRate()));
        quote.setPerBoxRate(nvl(branch.getPerBoxRate()));
        quote.setAmount(computeFreight(weight, boxes, quote.getPerKgRate(), quote.getPerBoxRate()));
        return quote;
    }

    public BigDecimal calculateLaneFreight(String lane, BigDecimal weight, Integer boxes) {
        return quote(null, lane, weight, boxes).getAmount();
    }

    public BigDecimal computeFreight(BigDecimal weight, Integer boxes, BigDecimal perKgRate, BigDecimal perBoxRate) {
        BigDecimal safeWeight = weight != null ? weight : BigDecimal.ZERO;
        int boxCount = boxes != null && boxes > 0 ? boxes : 0;
        return safeWeight.multiply(nvl(perKgRate))
                .add(BigDecimal.valueOf(boxCount).multiply(nvl(perBoxRate)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
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
