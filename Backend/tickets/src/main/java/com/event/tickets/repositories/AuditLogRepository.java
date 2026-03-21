package com.event.tickets.repositories;

import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Audit Log Repository
 *
 * Append-only in practice — the service layer only ever calls save() and read methods.
 * The delete methods inherited from JpaRepository are never called by AuditLogService.
 *
 * FIX A-7: findByTargetUserId() added — allows querying all actions targeting a user.
 * FIX A-6: findByAction() was already declared; now exposed via AuditLogService.
 *
 * NOTE on BUG A-10: The repository extends JpaRepository which technically exposes
 * delete methods. In practice, AuditLogService never calls them and @PreAuthorize
 * blocks non-admin access. A stricter solution would extend a read-only base
 * repository interface, but that requires a custom JPA base class — deferred as a
 * future architectural improvement.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** All audit logs — delegated from JpaRepository.findAll(Pageable). */
    Page<AuditLog> findAll(Pageable pageable);

    /**
     * Audit logs for a specific event.
     * Spring Data derives: WHERE event.id = :eventId
     */
    Page<AuditLog> findByEventId(UUID eventId, Pageable pageable);

    /**
     * Audit logs by actor (user who performed the action).
     * Spring Data derives: WHERE actor.id = :actorId
     */
    Page<AuditLog> findByActorId(UUID actorId, Pageable pageable);

    /**
     * FIX A-7: Audit logs by target user (user the action was taken against).
     * Spring Data derives: WHERE targetUser.id = :targetUserId
     *
     * Covers: USER_APPROVED, USER_REJECTED, ROLE_ASSIGNED, ROLE_REVOKED,
     * STAFF_ASSIGNED, STAFF_REMOVED, ADMIN_ROLE_GRANTED_VIA_INVITE.
     * ADMIN-only access enforced in AuditController.
     */
    Page<AuditLog> findByTargetUserId(UUID targetUserId, Pageable pageable);

    /**
     * Audit logs filtered by action type.
     * FIX A-6: previously declared but never surfaced via service or controller.
     * Now called by AuditLogService.findByAction() and exposed via GET /audit?action=.
     */
    Page<AuditLog> findByAction(AuditAction action, Pageable pageable);
}