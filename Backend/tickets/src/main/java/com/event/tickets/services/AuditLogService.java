package com.event.tickets.services;

import com.event.tickets.domain.entities.AuditAction;
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

    /** Returns all audit logs — ADMIN use only. */
    @Transactional(readOnly = true)
    public Page<AuditLog> findAll(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    /**
     * Returns audit logs for a specific event.
     * Authorization enforced by the caller.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByEventId(UUID eventId, Pageable pageable) {
        return auditLogRepository.findByEventId(eventId, pageable);
    }

    /**
     * Returns audit logs where the actor performed the action.
     * Used for self-service: any user can see their own actions.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByActorId(UUID actorId, Pageable pageable) {
        return auditLogRepository.findByActorId(actorId, pageable);
    }

    /**
     * FIX A-7: Returns audit logs where a specific user was the TARGET.
     *
     * BEFORE: There was no way to query "all actions taken against user X" —
     * approvals, rejections, role assignments, staff assignments etc. all have
     * a targetUser field on AuditLog but it was never queryable via the API.
     * An admin investigating a user's history had to scroll through all logs.
     *
     * AFTER: Dedicated query by targetUser.id — ADMIN use only (enforced by controller).
     * Covers events like: USER_APPROVED, USER_REJECTED, ROLE_ASSIGNED, ROLE_REVOKED,
     * STAFF_ASSIGNED, ADMIN_ROLE_GRANTED_VIA_INVITE targeting a specific user.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByTargetUserId(UUID targetUserId, Pageable pageable) {
        return auditLogRepository.findByTargetUserId(targetUserId, pageable);
    }

    /**
     * FIX A-6: Returns audit logs filtered by action type.
     *
     * BEFORE: AuditLogRepository.findByAction() was declared but never surfaced
     * through the service or any controller endpoint. An admin could not filter
     * logs by action type (e.g. "show all TICKET_PURCHASE_FAILED" or
     * "show all ADMIN_ROLE_GRANTED_VIA_INVITE") — only client-side filtering of
     * the full paginated result was possible, which is impractical at scale.
     *
     * AFTER: Exposed via this method and via GET /audit?action={action} on
     * AuditController. Filtering happens in the DB, not in Java.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByAction(AuditAction action, Pageable pageable) {
        return auditLogRepository.findByAction(action, pageable);
    }
}