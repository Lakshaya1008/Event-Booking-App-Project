package com.event.tickets.controllers;

import static com.event.tickets.util.JwtUtil.parseUserId;

import com.event.tickets.domain.dtos.GenerateInviteCodeRequestDto;
import com.event.tickets.domain.dtos.InviteCodeResponseDto;
import com.event.tickets.domain.dtos.RedeemInviteCodeRequestDto;
import com.event.tickets.domain.dtos.RedeemInviteCodeResponseDto;
import com.event.tickets.services.AuthorizationService;
import com.event.tickets.services.InviteCodeService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invite Code Controller
 *
 * Role-specific invite code system for controlled onboarding.
 *
 * CRITICAL RULES:
 * - One invite code = one role (STAFF, ATTENDEE, or ORGANIZER)
 * - Invite codes CANNOT be reused for different roles
 * - Invite codes CANNOT be upgraded
 * - Redemption is atomic and audited
 *
 * Authorization:
 * - ADMIN: Can create invites for ANY role
 * - ORGANIZER: Can create STAFF invites ONLY for their own events
 * - Any authenticated user: Can redeem valid invites
 *
 * Security:
 * - Backend is sole authority for Keycloak Admin API calls
 * - All redemptions audited
 * - Single-use enforcement via optimistic locking
 * - Time-bound expiration
 *
 * Endpoints:
 * - POST /invites - Generate invite code
 * - POST /invites/redeem - Redeem invite code
 * - DELETE /invites/{codeId} - Revoke invite code
 * - GET /invites - List invite codes (creator's or all for ADMIN)
 */
@RestController
@RequestMapping("/api/v1/invites")
@RequiredArgsConstructor
@Slf4j
public class InviteCodeController {

  private final InviteCodeService inviteCodeService;
  private final AuthorizationService authorizationService;

  /**
   * Generate a new invite code.
   *
   * Authorization:
   * - ADMIN: Can generate invites for ANY role (ADMIN, ORGANIZER, ATTENDEE, STAFF)
   * - ORGANIZER: Can generate STAFF invites ONLY for their own events
   *
   * Validation:
   * - Role name must be valid (ADMIN, ORGANIZER, ATTENDEE, STAFF)
   * - STAFF role requires eventId
   * - ORGANIZER must own the event if eventId provided
   * - Expiration hours must be positive
   *
   * @param jwt JWT token containing user ID
   * @param request Invite code generation request
   * @return Generated invite code details
   */
  @PostMapping
  @PreAuthorize("hasRole('ADMIN') or hasRole('ORGANIZER')")
  public ResponseEntity<InviteCodeResponseDto> generateInviteCode(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody GenerateInviteCodeRequestDto request
  ) {
    UUID creatorId = parseUserId(jwt);
    String roleName = request.getRoleName();
    UUID eventId = request.getEventId();

    log.info("User '{}' generating invite code for role '{}', event '{}'",
        creatorId, roleName, eventId);

    // Validate role-specific rules
    validateInviteCreation(jwt, roleName, eventId, creatorId);

    // Generate invite code
    InviteCodeResponseDto response = inviteCodeService.generateInviteCode(
        creatorId,
        roleName,
        eventId,
        request.getExpirationHours()
    );

    log.info("Generated invite code '{}' for role '{}'", response.getCode(), roleName);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Redeem an invite code.
   *
   * Any authenticated user can redeem a valid invite code.
   *
   * Process:
   * 1. Validate invite code (exists, not used, not expired, not revoked)
   * 2. Assign role via Keycloak Admin API
   * 3. If STAFF role: Create event-staff assignment
   * 4. Mark invite as USED (optimistic locking prevents double redemption)
   * 5. Audit the action
   *
   * Atomic Operation:
   * - Uses optimistic locking to prevent concurrent redemption
   * - All-or-nothing transaction
   * - No partial states
   *
   * @param jwt JWT token containing user ID
   * @param request Redemption request containing invite code
   * @return Redemption result with assigned role
   */
  @PostMapping("/redeem")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<RedeemInviteCodeResponseDto> redeemInviteCode(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody RedeemInviteCodeRequestDto request
  ) {
    UUID userId = parseUserId(jwt);
    String code = request.getCode();

    log.info("User '{}' redeeming invite code '{}'", userId, code);

    // Redeem invite code (atomic operation with audit)
    RedeemInviteCodeResponseDto response = inviteCodeService.redeemInviteCode(
        userId,
        code
    );

    log.info("User '{}' successfully redeemed invite code '{}', assigned role '{}'",
        userId, code, response.getRoleAssigned());

    return ResponseEntity.ok(response);
  }

  /**
   * Revoke an invite code.
   *
   * Authorization:
   * - ADMIN: Can revoke any invite code
   * - Creator: Can revoke their own invite codes
   *
   * Only PENDING invite codes can be revoked.
   * Already used, expired, or revoked codes cannot be revoked again.
   *
   * @param jwt JWT token containing user ID
   * @param codeId The ID of the invite code
   * @param reason Reason for revocation (optional)
   * @return Success response
   */
  @DeleteMapping("/{codeId}")
  @PreAuthorize("hasRole('ADMIN') or hasRole('ORGANIZER')")
  public ResponseEntity<Void> revokeInviteCode(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID codeId,
      @RequestParam(required = false, defaultValue = "Revoked by creator") String reason
  ) {
    UUID revokerId = parseUserId(jwt);

    log.info("User '{}' revoking invite code '{}', reason: {}",
        revokerId, codeId, reason);

    // Revoke invite code (authorization checked in service)
    inviteCodeService.revokeInviteCode(revokerId, codeId, reason);

    log.info("Successfully revoked invite code '{}'", codeId);
    return ResponseEntity.noContent().build();
  }

  /**
   * List invite codes.
   *
   * Authorization:
   * - ADMIN: Can see all invite codes
   * - ORGANIZER: Can see only their own invite codes
   *
   * Pagination supported via Pageable parameters.
   *
   * @param jwt JWT token containing user ID
   * @param pageable Pagination parameters
   * @return Page of invite codes
   */
  @GetMapping
  @PreAuthorize("hasRole('ADMIN') or hasRole('ORGANIZER')")
  public ResponseEntity<Page<InviteCodeResponseDto>> listInviteCodes(
      @AuthenticationPrincipal Jwt jwt,
      Pageable pageable
  ) {
    UUID creatorId = parseUserId(jwt);

    log.debug("User '{}' listing invite codes", creatorId);

    // List invite codes (ADMIN sees all, ORGANIZER sees only theirs)
    Page<InviteCodeResponseDto> response = inviteCodeService.listInviteCodesByCreator(
        creatorId,
        pageable
    );

    return ResponseEntity.ok(response);
  }

  /**
   * List invite codes for a specific event.
   *
   * Authorization:
   * - ORGANIZER: Must own the event
   * - ADMIN: Can see any event's invites
   *
   * @param jwt JWT token containing user ID
   * @param eventId The event ID
   * @param pageable Pagination parameters
   * @return Page of invite codes for the event
   */
  @GetMapping("/events/{eventId}")
  @PreAuthorize("hasRole('ADMIN') or hasRole('ORGANIZER')")
  public ResponseEntity<Page<InviteCodeResponseDto>> listEventInviteCodes(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId,
      Pageable pageable
  ) {
    UUID userId = parseUserId(jwt);

    log.debug("User '{}' listing invite codes for event '{}'", userId, eventId);

    // Authorize: must be ADMIN or event organizer
    // Only check if not ADMIN (ADMINs can see all)
    if (!hasRole(jwt, "ADMIN")) {
      authorizationService.requireOrganizerAccess(userId, eventId);
    }

    Page<InviteCodeResponseDto> response = inviteCodeService.listInviteCodesByEvent(
        eventId,
        pageable
    );

    return ResponseEntity.ok(response);
  }

  /**
   * Validates invite creation based on role and user permissions.
   *
   * Rules:
   * - ADMIN can create invites for any role (ADMIN, ORGANIZER, ATTENDEE, STAFF)
   * - ORGANIZER can only create STAFF invites for their own events
   * - STAFF role requires eventId
   * - Non-STAFF roles must NOT have eventId
   *
   * @param jwt JWT token
   * @param roleName Role name in invite
   * @param eventId Event ID (nullable)
   * @param creatorId Creator user ID
   */
  private void validateInviteCreation(Jwt jwt, String roleName, UUID eventId, UUID creatorId) {
    boolean isAdmin = hasRole(jwt, "ADMIN");
    boolean isOrganizer = hasRole(jwt, "ORGANIZER");

    // ORGANIZER restrictions
    if (isOrganizer && !isAdmin) {
      // ORGANIZER can ONLY create STAFF invites
      if (!"STAFF".equals(roleName)) {
        throw new IllegalArgumentException(
            "Organizers can only create STAFF invites. " +
            "Contact an ADMIN to create invites for other roles."
        );
      }

      // STAFF invites require eventId
      if (eventId == null) {
        throw new IllegalArgumentException("Event ID is required for STAFF invites");
      }

      // ORGANIZER must own the event
      authorizationService.requireOrganizerAccess(creatorId, eventId);
    }

    // Role-specific validation
    if ("STAFF".equals(roleName)) {
      if (eventId == null) {
        throw new IllegalArgumentException("Event ID is required for STAFF invites");
      }
    } else {
      // Non-STAFF roles should NOT have eventId
      if (eventId != null) {
        throw new IllegalArgumentException(
            "Event ID should only be provided for STAFF invites"
        );
      }
    }

    // ADMIN role invites only by ADMIN
    if ("ADMIN".equals(roleName) && !isAdmin) {
      throw new IllegalArgumentException(
          "Only ADMINs can create ADMIN role invites"
      );
    }
  }

  /**
   * Checks if JWT has a specific role.
   *
   * @param jwt JWT token
   * @param role Role name to check
   * @return true if user has the role
   */
  private boolean hasRole(Jwt jwt, String role) {
    return jwt.getClaimAsStringList("roles") != null &&
           jwt.getClaimAsStringList("roles").contains(role);
  }
}
