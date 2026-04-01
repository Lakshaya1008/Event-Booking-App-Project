package com.event.tickets.repositories;

import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

    Page<AuditLog> findByTargetUserId(UUID targetUserId, Pageable pageable);

    Page<AuditLog> findByAction(AuditAction action, Pageable pageable);
}