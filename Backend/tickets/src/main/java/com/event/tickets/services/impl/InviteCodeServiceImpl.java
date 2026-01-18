package com.event.tickets.services.impl;

import com.event.tickets.domain.dtos.InviteCodeResponseDto;
import com.event.tickets.domain.dtos.RedeemInviteCodeResponseDto;
import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.InviteCode;
import com.event.tickets.domain.entities.InviteCodeStatus;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.InvalidInviteCodeException;
import com.event.tickets.exceptions.InviteCodeNotFoundException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.AuditLogRepository;
import com.event.tickets.repositories.EventRepository;
import com.event.tickets.repositories.InviteCodeRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.InviteCodeService;
import com.event.tickets.services.KeycloakAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;

/**
 * Invite Code Service Implementation
 *
 * Implements invite-code based role onboarding with:
 * - Backend-generated codes
 * - Single-use enforcement
 * - Time-bound expiration
 * - Full validation and audit trail
 *
 * Security Model:
 * - No frontend trust
 * - All operations validated
 * - No silent failures
 * - Complete auditability
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InviteCodeServiceImpl implements InviteCodeService {

  private final InviteCodeRepository inviteCodeRepository;
  private final UserRepository userRepository;
  private final EventRepository eventRepository;
  private final KeycloakAdminService keycloakAdminService;
  private final AuditLogRepository auditLogRepository;

  private static final String CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int CODE_LENGTH = 16;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  @Override
  @Transactional
  public InviteCodeResponseDto generateInviteCode(
      UUID creatorId,
      String roleName,
      UUID eventId,
      int expirationHours
  ) {
    log.info("Generating invite code: creator={}, role={}, event={}, expiresInHours={}",
        creatorId, roleName, eventId, expirationHours);

    // Validate creator exists
    User creator = userRepository.findById(creatorId)
        .orElseThrow(() -> new UserNotFoundException(
            String.format("Creator with ID '%s' not found", creatorId)
        ));

    // Validate event if provided
    Event event = null;
    if (eventId != null) {
      event = eventRepository.findById(eventId)
          .orElseThrow(() -> new EventNotFoundException(
              String.format("Event with ID '%s' not found", eventId)
          ));

      // STAFF role requires event
      if ("STAFF".equals(roleName) && event == null) {
        throw new IllegalArgumentException("Event ID is required for STAFF role invites");
      }
    }

    // Generate unique code
    String code = generateUniqueCode();

    // Calculate expiration
    LocalDateTime expiresAt = LocalDateTime.now().plusHours(expirationHours);

    // Create invite code entity
    InviteCode inviteCode = InviteCode.builder()
        .code(code)
        .roleName(roleName)
        .event(event)
        .status(InviteCodeStatus.PENDING)
        .createdBy(creator)
        .expiresAt(expiresAt)
        .build();

    inviteCode = inviteCodeRepository.save(inviteCode);

    log.info("Generated invite code '{}' for role '{}', expires at {}",
        code, roleName, expiresAt);

    return mapToResponseDto(inviteCode);
  }

  @Override
  @Transactional
  public RedeemInviteCodeResponseDto redeemInviteCode(UUID userId, String code) {
    log.info("User '{}' redeeming invite code '{}'", userId, code);

    // Validate user exists
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(
            String.format("User with ID '%s' not found", userId)
        ));

    // Find invite code
    InviteCode inviteCode = inviteCodeRepository.findByCode(code)
        .orElseThrow(() -> new InviteCodeNotFoundException(
            String.format("Invite code '%s' not found", code)
        ));

    // Check and mark if expired
    inviteCode.checkAndMarkExpired();

    // Validate code is redeemable
    validateCodeForRedemption(inviteCode);

    // Assign role via Keycloak Admin API
    try {
      keycloakAdminService.assignRoleToUser(userId, inviteCode.getRoleName());
      log.info("Assigned role '{}' to user '{}' via Keycloak",
          inviteCode.getRoleName(), userId);
    } catch (Exception e) {
      log.error("Failed to assign role '{}' to user '{}' in Keycloak",
          inviteCode.getRoleName(), userId, e);
      throw new IllegalStateException(
          String.format(
              "Failed to assign role '%s' in Keycloak: %s",
              inviteCode.getRoleName(), e.getMessage()
          ),
          e
      );
    }

    // Create event-staff assignment if STAFF role
    String eventName = null;
    if ("STAFF".equals(inviteCode.getRoleName()) && inviteCode.getEvent() != null) {
      Event event = inviteCode.getEvent();
      event.getStaff().add(user);
      eventRepository.save(event);
      eventName = event.getName();
      log.info("Assigned user '{}' as staff to event '{}'",
          user.getName(), event.getName());
    }

    // Mark code as redeemed
    inviteCode.setStatus(InviteCodeStatus.REDEEMED);
    inviteCode.setRedeemedBy(user);
    inviteCode.setRedeemedAt(LocalDateTime.now());
    inviteCodeRepository.save(inviteCode);

    log.info("Successfully redeemed invite code '{}' for user '{}'", code, user.getName());

    // Get current roles
    List<String> currentRoles = keycloakAdminService.getUserRoles(userId);

    return new RedeemInviteCodeResponseDto(
        "Invite code redeemed successfully",
        inviteCode.getRoleName(),
        eventName,
        currentRoles
    );
  }

  @Override
  @Transactional
  public void revokeInviteCode(UUID revokerId, UUID codeId, String reason) {
    log.info("User '{}' revoking invite code '{}', reason: {}",
        revokerId, codeId, reason);

    // Validate revoker exists
    if (!userRepository.existsById(revokerId)) {
      throw new UserNotFoundException(
          String.format("Revoker with ID '%s' not found", revokerId)
      );
    }

    // Find invite code
    InviteCode inviteCode = inviteCodeRepository.findById(codeId)
        .orElseThrow(() -> new InviteCodeNotFoundException(
            String.format("Invite code with ID '%s' not found", codeId)
        ));

    // Check if already redeemed/revoked/expired
    if (inviteCode.getStatus() != InviteCodeStatus.PENDING) {
      throw new InvalidInviteCodeException(
          String.format(
              "Cannot revoke invite code: current status is %s",
              inviteCode.getStatus()
          )
      );
    }

    // Revoke code
    inviteCode.setStatus(InviteCodeStatus.REVOKED);
    inviteCode.setRevokedAt(LocalDateTime.now());
    inviteCode.setRevokedReason(reason);
    inviteCodeRepository.save(inviteCode);

    log.info("Successfully revoked invite code '{}'", inviteCode.getCode());
  }

  @Override
  public InviteCodeResponseDto getInviteCode(UUID codeId) {
    InviteCode inviteCode = inviteCodeRepository.findById(codeId)
        .orElseThrow(() -> new InviteCodeNotFoundException(
            String.format("Invite code with ID '%s' not found", codeId)
        ));

    // Check and mark if expired
    inviteCode.checkAndMarkExpired();
    if (inviteCode.getStatus() == InviteCodeStatus.EXPIRED) {
      inviteCodeRepository.save(inviteCode);
    }

    return mapToResponseDto(inviteCode);
  }

  @Override
  public Page<InviteCodeResponseDto> listInviteCodesByCreator(
      UUID creatorId,
      Pageable pageable
  ) {
    return inviteCodeRepository.findByCreatedById(creatorId, pageable)
        .map(this::mapToResponseDto);
  }

  @Override
  public Page<InviteCodeResponseDto> listInviteCodesByEvent(
      UUID eventId,
      Pageable pageable
  ) {
    return inviteCodeRepository.findByEventId(eventId, pageable)
        .map(this::mapToResponseDto);
  }

  @Override
  @Transactional
  public int markExpiredCodes() {
    log.debug("Marking expired invite codes");
    int count = inviteCodeRepository.markExpiredCodes(LocalDateTime.now());
    log.info("Marked {} invite codes as expired", count);
    return count;
  }

  /**
   * Generates a unique, cryptographically secure invite code.
   *
   * @return Unique invite code string
   */
  private String generateUniqueCode() {
    String code;
    int attempts = 0;
    int maxAttempts = 10;

    do {
      code = generateRandomCode();
      attempts++;

      if (attempts > maxAttempts) {
        throw new IllegalStateException(
            "Failed to generate unique invite code after " + maxAttempts + " attempts"
        );
      }
    } while (inviteCodeRepository.existsByCode(code));

    return code;
  }

  /**
   * Generates a random code string.
   *
   * @return Random code string
   */
  private String generateRandomCode() {
    StringBuilder code = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) {
      int index = SECURE_RANDOM.nextInt(CODE_CHARACTERS.length());
      code.append(CODE_CHARACTERS.charAt(index));

      // Add hyphen every 4 characters for readability
      if ((i + 1) % 4 == 0 && i < CODE_LENGTH - 1) {
        code.append('-');
      }
    }
    return code.toString();
  }

  /**
   * Validates that an invite code can be redeemed.
   *
   * @param inviteCode The invite code to validate
   * @throws InvalidInviteCodeException if code cannot be redeemed
   */
  private void validateCodeForRedemption(InviteCode inviteCode) {
    if (inviteCode.getStatus() == InviteCodeStatus.REDEEMED) {
      throw new InvalidInviteCodeException(
          String.format(
              "Invite code '%s' has already been redeemed by %s on %s",
              inviteCode.getCode(),
              inviteCode.getRedeemedBy().getName(),
              inviteCode.getRedeemedAt()
          )
      );
    }

    if (inviteCode.getStatus() == InviteCodeStatus.EXPIRED) {
      throw new InvalidInviteCodeException(
          String.format(
              "Invite code '%s' expired on %s",
              inviteCode.getCode(),
              inviteCode.getExpiresAt()
          )
      );
    }

    if (inviteCode.getStatus() == InviteCodeStatus.REVOKED) {
      throw new InvalidInviteCodeException(
          String.format(
              "Invite code '%s' has been revoked. Reason: %s",
              inviteCode.getCode(),
              inviteCode.getRevokedReason()
          )
      );
    }

    if (!inviteCode.isValid()) {
      throw new InvalidInviteCodeException(
          String.format(
              "Invite code '%s' is not valid for redemption",
              inviteCode.getCode()
          )
      );
    }
  }

  /**
   * Maps InviteCode entity to response DTO.
   *
   * @param inviteCode The invite code entity
   * @return Response DTO
   */
  private InviteCodeResponseDto mapToResponseDto(InviteCode inviteCode) {
    return InviteCodeResponseDto.builder()
        .id(inviteCode.getId())
        .code(inviteCode.getCode())
        .roleName(inviteCode.getRoleName())
        .eventId(inviteCode.getEvent() != null ? inviteCode.getEvent().getId() : null)
        .eventName(inviteCode.getEvent() != null ? inviteCode.getEvent().getName() : null)
        .status(inviteCode.getStatus().name())
        .createdBy(inviteCode.getCreatedBy().getName())
        .createdAt(inviteCode.getCreatedAt())
        .expiresAt(inviteCode.getExpiresAt())
        .redeemedBy(inviteCode.getRedeemedBy() != null ? inviteCode.getRedeemedBy().getName() : null)
        .redeemedAt(inviteCode.getRedeemedAt())
        .build();
  }
}
