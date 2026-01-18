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
 * READ-ONLY repository for audit logs.
 *
 * Security:
 * - No update or delete methods
 * - Append-only via service layer
 * - All queries return immutable data
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

  /**
   * Find all audit logs with pagination.
   * ADMIN only.
   */
  Page<AuditLog> findAll(Pageable pageable);

  /**
   * Find audit logs by event ID.
   * ORGANIZER must own the event.
   */
  Page<AuditLog> findByEventId(UUID eventId, Pageable pageable);

  /**
   * Find audit logs by actor (user who performed action).
   * User can see their own actions.
   */
  Page<AuditLog> findByActorId(UUID actorId, Pageable pageable);

  /**
   * Find audit logs by action type.
   * ADMIN only.
   */
  Page<AuditLog> findByAction(AuditAction action, Pageable pageable);
}
