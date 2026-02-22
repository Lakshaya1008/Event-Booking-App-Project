package com.event.tickets.services;

import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.repositories.AuditLogRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Saves an audit log in a NEW transaction so that audit failures never
     * roll back the business operation that triggered them.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAuditLog(AuditLog auditLog) {
        auditLogRepository.save(auditLog);
    }

    /**
     * Returns all audit logs — ADMIN use only.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findAll(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    /**
     * Returns audit logs for a specific event — ORGANIZER must own the event.
     * Authorization is enforced by the caller (AuditController / AuthorizationService).
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByEventId(UUID eventId, Pageable pageable) {
        return auditLogRepository.findByEventId(eventId, pageable);
    }

    /**
     * Returns audit logs for a specific actor/user — self-service, any authenticated user.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByActorId(UUID actorId, Pageable pageable) {
        return auditLogRepository.findByActorId(actorId, pageable);
    }
}
