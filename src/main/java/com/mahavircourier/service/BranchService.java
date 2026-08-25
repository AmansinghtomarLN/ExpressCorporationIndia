package com.mahavircourier.service;

import com.mahavircourier.dao.BillingTariffDao;
import com.mahavircourier.dao.BranchDao;
import com.mahavircourier.model.BillingTariff;
import com.mahavircourier.model.Branch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class BranchService {

    private final BranchDao branchDao;
    private final BillingTariffDao billingTariffDao;

    public BranchService(BranchDao branchDao, BillingTariffDao billingTariffDao) {
        this.branchDao = branchDao;
        this.billingTariffDao = billingTariffDao;
    }

    public List<Branch> findAll() {
        return branchDao.findAll();
    }

    public Optional<Branch> findById(Long id) {
        return branchDao.findById(id);
    }

    @Transactional
    public Branch create(Branch branch) {
        applyCategory(branch);
        Long id = branchDao.save(branch);
        branch.setId(id);
        return branch;
    }

    @Transactional
    public void update(Branch branch) {
        applyCategory(branch);
        branchDao.update(branch);
    }

    private void applyCategory(Branch branch) {
        if (branch.getBranchCategory() == null || branch.getBranchCategory().isBlank()
                || "AUTO".equalsIgnoreCase(branch.getBranchCategory())) {
            branch.setBranchCategory(BranchCategory.fromState(branch.getState()));
        } else {
            String lane = branch.getBranchCategory().trim().toUpperCase();
            if (!BranchCategory.DOMESTIC.equals(lane) && !BranchCategory.NATIONAL.equals(lane)) {
                throw new IllegalArgumentException("Branch category must be DOMESTIC or NATIONAL");
            }
            branch.setBranchCategory(lane);
        }
        applyDefaultRates(branch);
    }

    private void applyDefaultRates(Branch branch) {
        Optional<BillingTariff> tariff = billingTariffDao.findByLane(branch.getBranchCategory());
        if (branch.getPerKgRate() == null) {
            branch.setPerKgRate(tariff.map(BillingTariff::getPerKgRate).orElse(BigDecimal.ZERO));
        }
        if (branch.getPerBoxRate() == null) {
            branch.setPerBoxRate(tariff.map(BillingTariff::getPerBoxRate).orElse(BigDecimal.ZERO));
        }
    }

    @Transactional
    public void delete(Long id) {
        branchDao.deleteById(id);
    }
}
