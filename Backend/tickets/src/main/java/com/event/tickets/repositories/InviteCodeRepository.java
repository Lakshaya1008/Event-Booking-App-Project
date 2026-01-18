package com.event.tickets.repositories;

import com.event.tickets.domain.entities.InviteCode;
import com.event.tickets.domain.entities.InviteCodeStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Invite Code Repository
 *
 * Handles persistence operations for invite codes.
 */
@Repository
public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {

  /**
   * Finds an invite code by its code string.
   *
   * @param code The invite code string
   * @return Optional containing the invite code if found
   */
  Optional<InviteCode> findByCode(String code);

  /**
   * Finds all invite codes created by a specific user.
   *
   * @param createdById The ID of the creator
   * @param pageable Pagination parameters
   * @return Page of invite codes
   */
  Page<InviteCode> findByCreatedById(UUID createdById, Pageable pageable);

  /**
   * Finds all invite codes for a specific event.
   *
   * @param eventId The ID of the event
   * @param pageable Pagination parameters
   * @return Page of invite codes
   */
  Page<InviteCode> findByEventId(UUID eventId, Pageable pageable);

  /**
   * Finds all invite codes with a specific status.
   *
   * @param status The invite code status
   * @param pageable Pagination parameters
   * @return Page of invite codes
   */
  Page<InviteCode> findByStatus(InviteCodeStatus status, Pageable pageable);

  /**
   * Finds pending invite codes that have expired.
   * Used for batch expiration processing.
   *
   * @param now Current timestamp
   * @return List of expired invite codes
   */
  @Query("SELECT ic FROM InviteCode ic WHERE ic.status = 'PENDING' AND ic.expiresAt < :now")
  List<InviteCode> findExpiredCodes(LocalDateTime now);

  /**
   * Marks expired codes as EXPIRED in bulk.
   *
   * @param now Current timestamp
   * @return Number of codes marked as expired
   */
  @Modifying
  @Query("UPDATE InviteCode ic SET ic.status = 'EXPIRED' WHERE ic.status = 'PENDING' AND ic.expiresAt < :now")
  int markExpiredCodes(LocalDateTime now);

  /**
   * Checks if a code string already exists.
   *
   * @param code The code string to check
   * @return true if code exists
   */
  boolean existsByCode(String code);
}
