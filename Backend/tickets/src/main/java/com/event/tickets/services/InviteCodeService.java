package com.event.tickets.services;

import com.event.tickets.domain.dtos.InviteCodeResponseDto;
import com.event.tickets.domain.dtos.RedeemInviteCodeResponseDto;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Invite Code Service
 *
 * Manages invite-code based role onboarding.
 *
 * Features:
 * - Backend-generated invite codes
 * - Single-use enforcement
 * - Time-bound expiration
 * - Role assignment via Keycloak Admin API
 * - Event-staff assignment for STAFF role
 * - Full audit trail
 *
 * Security:
 * - No frontend trust
 * - All operations validated
 * - No silent failures
 * - Complete auditability
 */
public interface InviteCodeService {

  /**
   * Generates a new invite code.
   *
   * ADMIN: Can generate codes for any role
   * ORGANIZER: Can generate STAFF codes for their events only
   *
   * @param creatorId The ID of the user generating the code
   * @param roleName The role to assign when code is redeemed
   * @param eventId The event ID (required for STAFF role, null otherwise)
   * @param expirationHours Hours until code expires
   * @return Generated invite code details
   */
  InviteCodeResponseDto generateInviteCode(
      UUID creatorId,
      String roleName,
      UUID eventId,
      int expirationHours
  );

  /**
   * Redeems an invite code.
   *
   * Process:
   * 1. Validate code (exists, not redeemed, not expired)
   * 2. Assign role via Keycloak Admin API
   * 3. Create event-staff assignment if STAFF role
   * 4. Mark code as redeemed
   * 5. Record audit trail
   *
   * @param userId The ID of the user redeeming the code
   * @param code The invite code string
   * @return Redemption result with assigned role details
   * @throws com.event.tickets.exceptions.InviteCodeNotFoundException if code doesn't exist
   * @throws com.event.tickets.exceptions.InvalidInviteCodeException if code invalid
   */
  RedeemInviteCodeResponseDto redeemInviteCode(UUID userId, String code);

  /**
   * Revokes an invite code before it's redeemed.
   *
   * ADMIN: Can revoke any code
   * ORGANIZER: Can revoke codes they created
   *
   * @param revokerId The ID of the user revoking the code
   * @param codeId The ID of the invite code
   * @param reason Reason for revocation
   */
  void revokeInviteCode(UUID revokerId, UUID codeId, String reason);

  /**
   * Gets details of an invite code.
   *
   * @param codeId The ID of the invite code
   * @return Invite code details
   */
  InviteCodeResponseDto getInviteCode(UUID codeId);

  /**
   * Lists invite codes created by a user.
   *
   * @param creatorId The ID of the creator
   * @param pageable Pagination parameters
   * @return Page of invite codes
   */
  Page<InviteCodeResponseDto> listInviteCodesByCreator(UUID creatorId, Pageable pageable);

  /**
   * Lists invite codes for an event.
   *
   * @param eventId The ID of the event
   * @param pageable Pagination parameters
   * @return Page of invite codes
   */
  Page<InviteCodeResponseDto> listInviteCodesByEvent(UUID eventId, Pageable pageable);

  /**
   * Batch process to mark expired codes.
   * Should be run periodically (e.g., via scheduled task).
   *
   * @return Number of codes marked as expired
   */
  int markExpiredCodes();
}
