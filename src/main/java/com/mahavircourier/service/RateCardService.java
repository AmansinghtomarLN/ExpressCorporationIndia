package com.mahavircourier.service;

import com.mahavircourier.dao.BillingTariffDao;
import com.mahavircourier.dao.BranchDao;
import com.mahavircourier.dao.BranchLaneRateDao;
import com.mahavircourier.dao.PartyCityRateDao;
import com.mahavircourier.dto.FreightQuote;
import com.mahavircourier.model.BillingTariff;
import com.mahavircourier.model.Branch;
import com.mahavircourier.model.BranchLaneRate;
import com.mahavircourier.model.Party;
import com.mahavircourier.model.PartyCityRate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RateCardService {

    private final BillingTariffDao billingTariffDao;
    private final BranchDao branchDao;
    private final PartyCityRateDao partyCityRateDao;
    private final BranchLaneRateDao branchLaneRateDao;

    public RateCardService(BillingTariffDao billingTariffDao,
                           BranchDao branchDao,
                           PartyCityRateDao partyCityRateDao,
                           BranchLaneRateDao branchLaneRateDao) {
        this.billingTariffDao = billingTariffDao;
        this.branchDao = branchDao;
        this.partyCityRateDao = partyCityRateDao;
        this.branchLaneRateDao = branchLaneRateDao;
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
     * Freight = weight × per-kg. Boxes are stored for operations but not billed.
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
     * Party bill rates: use the party's optional per-kg when set.
     * Otherwise fall back to the destination branch, then category tariff.
     * A tagged party's minimum kg is applied to this weight.
     */
    public FreightQuote quoteForParty(Party party, Long destBranchId, String lane,
                                      BigDecimal weight, Integer boxes) {
        String city = null;
        if (destBranchId != null) {
            city = branchDao.findById(destBranchId).map(Branch::getCity).orElse(null);
        }
        return quoteForPartyCity(party, city, lane, weight, boxes);
    }

    /**
     * Per-consignment party price. City rate wins, then the party default per kg,
     * then the destination branch rate. Tagged parties bill at least their minimum kg.
     * A city row can set its own minimum kg; otherwise the party minimum is used.
     */
    public FreightQuote quoteForPartyCity(Party party, String destinationCity, String manifestLane,
                                          BigDecimal weight, Integer boxes) {
        FreightQuote base = quote(destinationCity, manifestLane, weight, boxes);
        PartyCityRate cityRate = null;
        if (party != null && party.getId() != null && StringUtils.hasText(destinationCity)) {
            cityRate = partyCityRateDao.findByPartyAndCity(party.getId(), destinationCity.trim()).orElse(null);
        }
        BigDecimal perKg = base.getPerKgRate();
        if (cityRate != null && cityRate.getPerKgRate() != null) {
            perKg = cityRate.getPerKgRate();
        } else if (party != null && party.hasCustomRates()) {
            perKg = party.getPerKgRate();
        }
        BigDecimal actual = weight != null ? weight : BigDecimal.ZERO;
        BigDecimal billable = actual;
        BigDecimal minimum = resolveMinimumKg(party, cityRate);
        if (minimum != null && actual.compareTo(minimum) < 0) {
            billable = minimum;
        }
        FreightQuote quote = new FreightQuote();
        quote.setLane(base.getLane());
        quote.setBranchId(base.getBranchId());
        quote.setBranchName(base.getBranchName());
        quote.setBranchCity(base.getBranchCity());
        quote.setPerKgRate(nvl(perKg));
        quote.setPerBoxRate(BigDecimal.ZERO);
        quote.setBillableWeightKg(billable);
        quote.setAmount(billable.multiply(nvl(perKg)).setScale(2, RoundingMode.HALF_UP));
        return quote;
    }

    private BigDecimal resolveMinimumKg(Party party, PartyCityRate cityRate) {
        if (party == null || !party.isMinRateEligible()) {
            return null;
        }
        if (cityRate != null && cityRate.getMinWeightKg() != null) {
            return cityRate.getMinWeightKg();
        }
        return party.getMinWeightKg();
    }

    public List<BranchLaneRate> findLanesFrom(Long originBranchId) {
        return branchLaneRateDao.findByOrigin(originBranchId);
    }

    public Map<Long, Integer> countLanesByOrigin() {
        return branchLaneRateDao.countByOrigin();
    }

    @Transactional
    public int applyLaneRate(Long originBranchId, BigDecimal perKgRate) {
        Branch origin = branchDao.findById(originBranchId)
                .orElseThrow(() -> new IllegalArgumentException("Base branch not found"));
        if (perKgRate == null || perKgRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Per kg rate cannot be negative");
        }
        int saved = 0;
        for (Branch dest : branchDao.findAll()) {
            if (dest.getId().equals(origin.getId())) {
                continue;
            }
            BranchLaneRate rate = new BranchLaneRate();
            rate.setOriginBranchId(origin.getId());
            rate.setDestBranchId(dest.getId());
            rate.setPerKgRate(perKgRate);
            branchLaneRateDao.upsert(rate);
            saved++;
        }
        return saved;
    }

    @Transactional
    public int saveLaneRates(Long originBranchId, List<Long> removeIds,
                             List<Long> destBranchIds, List<BigDecimal> perKgRates) {
        branchDao.findById(originBranchId)
                .orElseThrow(() -> new IllegalArgumentException("Base branch not found"));
        if (removeIds != null) {
            for (Long rateId : removeIds) {
                if (rateId == null) {
                    continue;
                }
                BranchLaneRate existing = branchLaneRateDao.findById(rateId)
                        .orElseThrow(() -> new IllegalArgumentException("Branch rate not found"));
                if (!originBranchId.equals(existing.getOriginBranchId())) {
                    throw new IllegalArgumentException("Branch rate does not belong to this base branch");
                }
                branchLaneRateDao.deleteById(rateId);
            }
        }
        if (destBranchIds == null || destBranchIds.isEmpty()) {
            return 0;
        }
        int saved = 0;
        for (int i = 0; i < destBranchIds.size(); i++) {
            Long destId = destBranchIds.get(i);
            BigDecimal perKg = perKgRates != null && i < perKgRates.size() ? perKgRates.get(i) : null;
            if (destId == null || perKg == null) {
                continue;
            }
            if (destId.equals(originBranchId)) {
                throw new IllegalArgumentException("A branch rate is set to another branch, not back to itself");
            }
            if (perKg.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Per kg rate cannot be negative");
            }
            if (branchDao.findById(destId).isEmpty()) {
                throw new IllegalArgumentException("Destination branch not found");
            }
            BranchLaneRate rate = new BranchLaneRate();
            rate.setOriginBranchId(originBranchId);
            rate.setDestBranchId(destId);
            rate.setPerKgRate(perKg);
            branchLaneRateDao.upsert(rate);
            saved++;
        }
        return saved;
    }

    /**
     * Branch bill price from the manifest's origin branch to a destination branch.
     * A saved lane rate wins. Otherwise the destination branch's own per kg rate is used.
     */
    public FreightQuote quoteBranchLane(Long originBranchId, Long destBranchId,
                                        BigDecimal weight, Integer boxes) {
        if (destBranchId == null) {
            return quote(null, BranchCategory.DOMESTIC, weight, boxes);
        }
        Branch dest = branchDao.findById(destBranchId)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found"));
        BigDecimal perKg = nvl(dest.getPerKgRate());
        if (originBranchId != null && !originBranchId.equals(destBranchId)) {
            Optional<BranchLaneRate> lane = branchLaneRateDao.findByOriginAndDest(originBranchId, destBranchId);
            if (lane.isPresent() && lane.get().getPerKgRate() != null) {
                perKg = lane.get().getPerKgRate();
            }
        }
        FreightQuote quote = new FreightQuote();
        quote.setBranchId(dest.getId());
        quote.setBranchName(dest.getBranchName());
        quote.setBranchCity(dest.getCity());
        quote.setLane(StringUtils.hasText(dest.getBranchCategory())
                ? dest.getBranchCategory() : BranchCategory.DOMESTIC);
        quote.setPerKgRate(perKg);
        quote.setPerBoxRate(BigDecimal.ZERO);
        quote.setBillableWeightKg(weight != null ? weight : BigDecimal.ZERO);
        quote.setAmount(nvl(weight).multiply(perKg).setScale(2, RoundingMode.HALF_UP));
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
        return safeWeight.multiply(nvl(perKgRate)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
