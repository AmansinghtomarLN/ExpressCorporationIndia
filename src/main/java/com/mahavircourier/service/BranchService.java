package com.mahavircourier.service;

import com.mahavircourier.dao.BranchDao;
import com.mahavircourier.model.Branch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class BranchService {

    private final BranchDao branchDao;

    public BranchService(BranchDao branchDao) {
        this.branchDao = branchDao;
    }

    public List<Branch> findAll() {
        return branchDao.findAll();
    }

    public Optional<Branch> findById(Long id) {
        return branchDao.findById(id);
    }

    @Transactional
    public Branch create(Branch branch) {
        Long id = branchDao.save(branch);
        branch.setId(id);
        return branch;
    }

    @Transactional
    public void update(Branch branch) {
        branchDao.update(branch);
    }

    @Transactional
    public void delete(Long id) {
        branchDao.deleteById(id);
    }
}
