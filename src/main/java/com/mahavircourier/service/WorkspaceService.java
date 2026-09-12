package com.mahavircourier.service;

import com.mahavircourier.dao.BranchDao;
import com.mahavircourier.dao.UserDao;
import com.mahavircourier.model.Branch;
import com.mahavircourier.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class WorkspaceService {

    public static final String DEFAULT_CITY = "Indore";

    private final BranchDao branchDao;
    private final UserDao userDao;

    public WorkspaceService(BranchDao branchDao, UserDao userDao) {
        this.branchDao = branchDao;
        this.userDao = userDao;
    }

    public Branch resolveCurrent(User user) {
        User effective = user;
        if (user != null && user.getId() != null) {
            effective = userDao.findById(user.getId()).orElse(user);
        }
        if (effective != null && effective.getCurrentBranchId() != null) {
            Optional<Branch> saved = branchDao.findById(effective.getCurrentBranchId());
            if (saved.isPresent()) {
                return saved.get();
            }
        }
        return defaultBranch();
    }

    public Branch defaultBranch() {
        return branchDao.findByCityIgnoreCase(DEFAULT_CITY)
                .orElseGet(() -> {
                    List<Branch> all = branchDao.findAll();
                    if (all.isEmpty()) {
                        throw new IllegalArgumentException("No branches configured");
                    }
                    return all.get(0);
                });
    }

    @Transactional
    public Branch setCurrent(Long userId, Long branchId) {
        Branch branch = branchDao.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found"));
        userDao.updateCurrentBranch(userId, branch.getId());
        return branch;
    }
}
