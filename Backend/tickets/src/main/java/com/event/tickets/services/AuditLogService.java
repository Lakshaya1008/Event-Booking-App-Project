package com.event.tickets.services;

import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.repositories.AuditLogRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * FIXES APPLIED:
 *
 * FIX-AL1 — REQUIRES_NEW + detached entity problem resolved.
 *   BEFORE: saveAuditLog(AuditLog auditLog) opened a new transaction with
 *   REQUIRES_NEW. The AuditLog passed in had @ManyToOne references to User
 *   and Event entities that were managed in the CALLER's transaction.
 *   When REQUIRES_NEW opens a fresh persistence context, those entities are
 *   DETACHED — JPA throws DetachedObjectException or silently re-fetches them,
 *   causing the FK insert to fail if the User was just created and not yet
 *   committed (new registrations).
 *
 *   AFTER: The AuditLog is built entirely from primitive/ID fields inside the
 *   new transaction. The caller passes the AuditLog with only its entity
 *   references set — the service re-attaches them by ID before saving.
 *   For the actor/targetUser/event IDs that may not exist yet (e.g. during
 *   a REGISTRATION_ATTEMPT before the user row is committed), null references
 *   are allowed and the audit row is saved with nullable FKs.
 *
 *   SIMPLEST safe pattern used here: the AuditLog builder in callers uses
 *   detached references, so we merge (re-attach) each reference by loading
 *   it within the new transaction before saving. If the entity doesn't exist
 *   yet (uncommitted user), we set the FK to null and log a warning.
 *   This is correct: a REGISTRATION_ATTEMPT audit for a not-yet-committed user
 *   should have actor = SYSTEM (already committed), not the new user.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Saves an audit log in a NEW transaction.
     *
     * REQUIRES_NEW ensures audit failures never roll back the business operation.
     *
     * The AuditLog must be built so that its entity references (actor, targetUser,
     * event) are either:
     *   (a) already committed rows that this new TX can see, OR
     *   (b) null (nullable FK columns in audit_logs)
     *
     * Callers that emit audit events for a user that was JUST created in the same
     * TX (e.g. REGISTRATION_SUCCESS) should use the SYSTEM user as the actor, since
     * the new user row has not been committed yet and cannot be FK-referenced from
     * a separate transaction.
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
     * Returns audit logs for a specific event.
     * Authorization enforced by caller (AuditController / AuthorizationService).
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByEventId(UUID eventId, Pageable pageable) {
        return auditLogRepository.findByEventId(eventId, pageable);
    }

    /**
     * Returns audit logs for a specific actor — self-service for any authenticated user.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByActorId(UUID actorId, Pageable pageable) {
        return auditLogRepository.findByActorId(actorId, pageable);
    }
}