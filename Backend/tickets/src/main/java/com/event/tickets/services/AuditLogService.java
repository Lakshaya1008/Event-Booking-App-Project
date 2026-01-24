package com.event.tickets.services;

import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.repositories.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAuditLog(AuditLog auditLog) {
        auditLogRepository.save(auditLog);
    }
}
