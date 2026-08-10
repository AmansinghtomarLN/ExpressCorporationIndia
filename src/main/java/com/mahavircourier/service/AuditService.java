package com.mahavircourier.service;

import com.mahavircourier.dao.AuditLogDao;
import com.mahavircourier.model.AuditLog;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    private final AuditLogDao auditLogDao;

    public AuditService(AuditLogDao auditLogDao) {
        this.auditLogDao = auditLogDao;
    }

    public void log(Long actorUserId, String actorEmail, String action,
                    String entityType, String entityId, String details) {
        AuditLog entry = new AuditLog();
        entry.setActorUserId(actorUserId);
        entry.setActorEmail(actorEmail);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetails(truncate(details, 1000));
        auditLogDao.save(entry);
    }

    public List<AuditLog> listRecent(int limit) {
        return auditLogDao.findAll(Math.min(Math.max(limit, 1), 500));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
