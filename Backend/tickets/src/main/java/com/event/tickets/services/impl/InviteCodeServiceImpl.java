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
import com.event.tickets.exceptions.InvalidBusinessStateException;
import com.event.tickets.exceptions.InvalidInputException;
import com.event.tickets.exceptions.InvalidInviteCodeException;
import com.event.tickets.exceptions.InviteCodeNotFoundException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.EventRepository;
import com.event.tickets.repositories.InviteCodeRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuditLogService;
import com.event.tickets.services.InviteCodeService;
import com.event.tickets.services.KeycloakAdminService;
import com.event.tickets.services.SystemUserProvider;
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
 *
 * Business Logic Applied:
 * - When an ADMIN role is granted via invite code, a high-severity
 *   ADMIN_ROLE_GRANTED_VIA_INVITE audit event is emitted in addition
 *   to the standard INVITE_REDEEMED event. This makes every ADMIN
 *   promotion via invite code visible and auditable by other ADMINs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InviteCodeServiceImpl implements InviteCodeService {

    private final InviteCodeRepository inviteCodeRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final AuditLogService auditLogService;
    private final SystemUserProvider systemUserProvider;

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

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("Creator with ID '%s' not found", creatorId)
                ));

        Event event = null;
        if (eventId != null) {
            event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new EventNotFoundException(
                            String.format("Event with ID '%s' not found", eventId)
                    ));

            if ("STAFF".equals(roleName) && event == null) {
                throw new InvalidInputException("Event ID is required for STAFF role invites");
            }
        }

        String code = generateUniqueCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(expirationHours);

        InviteCode inviteCode = InviteCode.builder()
                .code(code)
                .roleName(roleName)
                .event(event)
                .status(InviteCodeStatus.PENDING)
                .createdBy(creator)
                .expiresAt(expiresAt)
                .build();

        inviteCode = inviteCodeRepository.save(inviteCode);

        log.info("Generated invite code '{}' for role '{}', expires at {}", code, roleName, expiresAt);

        return mapToResponseDto(inviteCode);
    }

    @Override
    @Transactional
    public RedeemInviteCodeResponseDto redeemInviteCode(UUID userId, String code) {
        log.info("User '{}' redeeming invite code '{}'", userId, code);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID '%s' not found", userId)
                ));

        try {
            InviteCode inviteCode = inviteCodeRepository.findByCode(code)
                    .orElseThrow(() -> new InviteCodeNotFoundException(
                            String.format("Invite code '%s' not found", code)
                    ));

            inviteCode.checkAndMarkExpired();
            validateCodeForRedemption(inviteCode);

            // Assign role via Keycloak Admin API
            try {
                keycloakAdminService.assignRoleToUser(userId, inviteCode.getRoleName());
                log.info("Assigned role '{}' to user '{}' via Keycloak", inviteCode.getRoleName(), userId);
            } catch (Exception e) {
                log.error("Failed to assign role '{}' to user '{}' in Keycloak",
                        inviteCode.getRoleName(), userId, e);
                emitFailedInviteRedemption(user, inviteCode, "ROLE_ASSIGNMENT_FAILED: " + e.getMessage());
                throw new InvalidBusinessStateException(
                        String.format("Failed to assign role '%s' in Keycloak: %s",
                                inviteCode.getRoleName(), e.getMessage()),
                        e
                );
            }

            // ── Business Rule: High-severity audit for ADMIN promotion via invite ─────
            // Allowed — ADMINs are trusted to vouch for new ADMINs. But every ADMIN
            // promotion via invite code must be visible in the audit trail so other
            // ADMINs can review it. This is in addition to the regular INVITE_REDEEMED event.
            if ("ADMIN".equals(inviteCode.getRoleName())) {
                log.warn("HIGH-SEVERITY: ADMIN role granted to user '{}' via invite code '{}' created by '{}'",
                        userId, inviteCode.getCode(), inviteCode.getCreatedBy().getName());
                emitAdminRoleGrantedAudit(user, inviteCode);
            }

            // Create event-staff assignment if STAFF role
            String eventName = null;
            if ("STAFF".equals(inviteCode.getRoleName()) && inviteCode.getEvent() != null) {
                Event event = inviteCode.getEvent();
                event.getStaff().add(user);
                eventRepository.save(event);
                eventName = event.getName();
                log.info("Assigned user '{}' as staff to event '{}'", user.getName(), event.getName());
            }

            // Mark code as redeemed
            inviteCode.setStatus(InviteCodeStatus.REDEEMED);
            inviteCode.setRedeemedBy(user);
            inviteCode.setRedeemedAt(LocalDateTime.now());
            inviteCodeRepository.save(inviteCode);

            log.info("Successfully redeemed invite code '{}' for user '{}'", code, user.getName());

            List<String> currentRoles = keycloakAdminService.getUserRoles(userId);

            return new RedeemInviteCodeResponseDto(
                    "Invite code redeemed successfully",
                    inviteCode.getRoleName(),
                    eventName,
                    currentRoles
            );

        } catch (InvalidInviteCodeException | InviteCodeNotFoundException e) {
            emitFailedInviteRedemption(user, null, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional
    public void revokeInviteCode(UUID revokerId, UUID codeId, String reason) {
        log.info("User '{}' revoking invite code '{}', reason: {}", revokerId, codeId, reason);

        if (!userRepository.existsById(revokerId)) {
            throw new UserNotFoundException(
                    String.format("Revoker with ID '%s' not found", revokerId)
            );
        }

        InviteCode inviteCode = inviteCodeRepository.findById(codeId)
                .orElseThrow(() -> new InviteCodeNotFoundException(
                        String.format("Invite code with ID '%s' not found", codeId)
                ));

        if (inviteCode.getStatus() != InviteCodeStatus.PENDING) {
            throw new InvalidInviteCodeException(
                    String.format("Cannot revoke invite code: current status is %s", inviteCode.getStatus())
            );
        }

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

        inviteCode.checkAndMarkExpired();
        if (inviteCode.getStatus() == InviteCodeStatus.EXPIRED) {
            inviteCodeRepository.save(inviteCode);
        }

        return mapToResponseDto(inviteCode);
    }

    @Override
    public Page<InviteCodeResponseDto> listInviteCodesByCreator(UUID creatorId, Pageable pageable) {
        return inviteCodeRepository.findByCreatedById(creatorId, pageable).map(this::mapToResponseDto);
    }

    @Override
    public Page<InviteCodeResponseDto> listInviteCodesByEvent(UUID eventId, Pageable pageable) {
        return inviteCodeRepository.findByEventId(eventId, pageable).map(this::mapToResponseDto);
    }

    public Page<InviteCodeResponseDto> listAllInviteCodes(Pageable pageable) {
        log.debug("ADMIN listing all invite codes");
        return inviteCodeRepository.findAll(pageable).map(this::mapToResponseDto);
    }

    @Override
    @Transactional
    public int markExpiredCodes() {
        log.debug("Marking expired invite codes");
        int count = inviteCodeRepository.markExpiredCodes(LocalDateTime.now());
        log.info("Marked {} invite codes as expired", count);
        return count;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Emits a high-severity audit event when ADMIN role is granted via an invite code.
     * Stored with REQUIRES_NEW so audit failure never blocks the redemption.
     */
    private void emitAdminRoleGrantedAudit(User newAdmin, InviteCode inviteCode) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.ADMIN_ROLE_GRANTED_VIA_INVITE)
                    .actor(newAdmin)
                    .targetUser(newAdmin)
                    .resourceType("INVITE_CODE")
                    .resourceId(inviteCode.getId())
                    .details(String.format(
                            "ADMIN role granted via invite code '%s' created by '%s' (id=%s)",
                            inviteCode.getCode(),
                            inviteCode.getCreatedBy().getName(),
                            inviteCode.getCreatedBy().getId()
                    ))
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit ADMIN_ROLE_GRANTED_VIA_INVITE audit: {}", e.getMessage());
        }
    }

    private void emitFailedInviteRedemption(User user, InviteCode inviteCode, String reason) {
        try {
            if (user == null) {
                user = systemUserProvider.getSystemUser();
            }
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.FAILED_INVITE_REDEMPTION)
                    .actor(user)
                    .targetUser(user)
                    .event(inviteCode != null ? inviteCode.getEvent() : null)
                    .resourceType("INVITE_CODE")
                    .resourceId(inviteCode != null ? inviteCode.getId() : null)
                    .details("code=" + (inviteCode != null ? inviteCode.getCode() : "NOT_FOUND")
                            + ",reason=" + reason)
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit FAILED_INVITE_REDEMPTION audit: userId={}, error={}",
                    user != null ? user.getId() : "null", e.getMessage());
        }
    }

    private String generateUniqueCode() {
        String code;
        int attempts = 0;
        int maxAttempts = 10;
        do {
            code = generateRandomCode();
            attempts++;
            if (attempts > maxAttempts) {
                throw new InvalidBusinessStateException(
                        "Failed to generate unique invite code after " + maxAttempts + " attempts"
                );
            }
        } while (inviteCodeRepository.existsByCode(code));
        return code;
    }

    private String generateRandomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = SECURE_RANDOM.nextInt(CODE_CHARACTERS.length());
            code.append(CODE_CHARACTERS.charAt(index));
            if ((i + 1) % 4 == 0 && i < CODE_LENGTH - 1) {
                code.append('-');
            }
        }
        return code.toString();
    }

    private void validateCodeForRedemption(InviteCode inviteCode) {
        if (inviteCode.getStatus() == InviteCodeStatus.REDEEMED) {
            throw new InvalidInviteCodeException(
                    String.format("Invite code '%s' has already been redeemed by %s on %s",
                            inviteCode.getCode(),
                            inviteCode.getRedeemedBy().getName(),
                            inviteCode.getRedeemedAt())
            );
        }
        if (inviteCode.getStatus() == InviteCodeStatus.EXPIRED) {
            throw new InvalidInviteCodeException(
                    String.format("Invite code '%s' expired on %s",
                            inviteCode.getCode(), inviteCode.getExpiresAt())
            );
        }
        if (inviteCode.getStatus() == InviteCodeStatus.REVOKED) {
            throw new InvalidInviteCodeException(
                    String.format("Invite code '%s' has been revoked. Reason: %s",
                            inviteCode.getCode(), inviteCode.getRevokedReason())
            );
        }
        if (!inviteCode.isValid()) {
            throw new InvalidInviteCodeException(
                    String.format("Invite code '%s' is not valid for redemption", inviteCode.getCode())
            );
        }
    }

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

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
