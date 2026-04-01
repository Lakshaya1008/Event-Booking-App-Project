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
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;
import static com.event.tickets.util.RequestUtil.getCurrentRequest;

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

    private static final int MAX_INVITES_PER_EVENT = 100;
    private static final int MAX_INVITES_PER_ORGANIZER = 500;

    // ── GENERATE ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public InviteCodeResponseDto generateInviteCode(UUID creatorId, String roleName,
                                                    UUID eventId, int expirationHours) {
        log.info("Generating invite code: creator={}, role={}, event={}, expiresInHours={}",
                creatorId, roleName, eventId, expirationHours);

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("Creator with ID '%s' not found", creatorId)));

        if ("STAFF".equals(roleName) && eventId == null) {
            throw new InvalidInputException("Event ID is required for STAFF role invites");
        }

        Event event = null;
        if (eventId != null) {
            event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new EventNotFoundException(
                            String.format("Event with ID '%s' not found", eventId)));

            long inviteCountForEvent = inviteCodeRepository.countByEventIdAndStatus(eventId, InviteCodeStatus.PENDING);
            if (inviteCountForEvent >= MAX_INVITES_PER_EVENT) {
                throw new InvalidBusinessStateException(
                        String.format("Event has reached maximum invite codes limit (%d)", MAX_INVITES_PER_EVENT));
            }
        }

        long inviteCountByCreator = inviteCodeRepository.countByCreatedByIdAndStatus(creatorId, InviteCodeStatus.PENDING);
        if (inviteCountByCreator >= MAX_INVITES_PER_ORGANIZER) {
            throw new InvalidBusinessStateException(
                    String.format("You have reached the maximum invite codes limit (%d)", MAX_INVITES_PER_ORGANIZER));
        }

        String code = generateRandomCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(expirationHours);

        InviteCode inviteCode = InviteCode.builder()
                .code(code)
                .roleName(roleName)
                .event(event)
                .status(InviteCodeStatus.PENDING)
                .createdBy(creator)
                .expiresAt(expiresAt)
                .build();

        int maxAttempts = 5;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                inviteCode = inviteCodeRepository.save(inviteCode);
                break;
            } catch (DataIntegrityViolationException e) {
                if (attempt == maxAttempts) {
                    throw new InvalidBusinessStateException(
                            "Failed to generate unique invite code after " + maxAttempts + " attempts");
                }
                log.warn("Invite code collision on save (attempt {}/{}), retrying", attempt, maxAttempts);
                inviteCode.setCode(generateRandomCode());
            }
        }

        log.info("Generated invite code '{}' for role '{}', expires at {}", code, roleName, expiresAt);
        return mapToResponseDto(inviteCode);
    }

    // ── REDEEM ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public RedeemInviteCodeResponseDto redeemInviteCode(UUID userId, String code) {
        log.info("User '{}' redeeming invite code '{}'", userId, code);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID '%s' not found", userId)));

        try {
            InviteCode inviteCode = inviteCodeRepository.findByCodeForUpdate(code)
                    .orElseThrow(() -> new InviteCodeNotFoundException(
                            String.format("Invite code '%s' not found", code)));

            if ("STAFF".equals(inviteCode.getRoleName()) && inviteCode.getEvent() == null) {
                throw new InvalidInviteCodeException("STAFF invite code must be tied to an event");
            }

            // to prevent a concurrent redemption from bypassing the expiry check.
            InviteCodeStatus statusBeforeCheck = inviteCode.getStatus();
            inviteCode.checkAndMarkExpired();
            if (inviteCode.getStatus() == InviteCodeStatus.EXPIRED
                    && statusBeforeCheck != InviteCodeStatus.EXPIRED) {
                inviteCodeRepository.save(inviteCode);
                log.info("Persisted EXPIRED status for code '{}' before validation", inviteCode.getCode());
            }

            validateCodeForRedemption(inviteCode);

            // Assign role in Keycloak
            try {
                keycloakAdminService.assignRoleToUser(userId, inviteCode.getRoleName());
                log.info("Assigned role '{}' to user '{}'", inviteCode.getRoleName(), userId);
            } catch (Exception e) {
                log.error("Failed to assign role in Keycloak", e);
                emitFailedInviteRedemption(user, inviteCode, "ROLE_ASSIGNMENT_FAILED: " + e.getMessage());
                throw new InvalidBusinessStateException(
                        String.format("Failed to assign role '%s' in Keycloak: %s",
                                inviteCode.getRoleName(), e.getMessage()), e);
            }

            // BEFORE: getUserRoles() was called inside the ADMIN block AND again unconditionally
            // at the end — two Keycloak calls for ADMIN redemptions, one wasted call for all others.
            List<String> currentRoles = keycloakAdminService.getUserRoles(userId);

            // High-severity audit for ADMIN role grants
            if ("ADMIN".equals(inviteCode.getRoleName())) {
                try {
                    if (currentRoles != null && currentRoles.stream().anyMatch("ADMIN"::equals)) {
                        log.warn("HIGH-SEVERITY: ADMIN role granted to user '{}' via invite code '{}'",
                                userId, inviteCode.getCode());
                        emitAdminRoleGrantedAudit(user, inviteCode);
                    }
                } catch (InvalidBusinessStateException e) {
                    throw e;
                } catch (Exception e) {
                    log.warn("Could not emit ADMIN audit during redemption, proceeding", e);
                }
            }

            // Assign to event staff list if STAFF code
            String eventName = null;
            if ("STAFF".equals(inviteCode.getRoleName()) && inviteCode.getEvent() != null) {
                Event staffEvent = inviteCode.getEvent();
                boolean alreadyStaff = staffEvent.getStaff().stream()
                        .anyMatch(s -> s.getId().equals(user.getId()));
                if (!alreadyStaff) {
                    staffEvent.getStaff().add(user);
                } else {
                    log.warn("User '{}' is already staff of event '{}' — skipping duplicate add",
                            user.getName(), staffEvent.getName());
                }
                eventRepository.save(staffEvent);
                eventName = staffEvent.getName();
                log.info("Assigned user '{}' as staff to event '{}'", user.getName(), staffEvent.getName());
            }

            // Mark code as redeemed
            inviteCode.setStatus(InviteCodeStatus.REDEEMED);
            inviteCode.setRedeemedBy(user);
            inviteCode.setRedeemedAt(LocalDateTime.now());
            inviteCodeRepository.save(inviteCode);

            log.info("Successfully redeemed invite code '{}' for user '{}'", code, user.getName());
            emitInviteRedeemedAudit(user, inviteCode);

            return new RedeemInviteCodeResponseDto(
                    "Invite code redeemed successfully",
                    inviteCode.getRoleName(),
                    eventName,
                    currentRoles);

        } catch (InvalidInviteCodeException | InviteCodeNotFoundException e) {
            emitFailedInviteRedemption(user, null, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    // ── REVOKE ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void revokeInviteCode(UUID revokerId, UUID codeId, String reason, boolean isAdmin) {
        if (!userRepository.existsById(revokerId))
            throw new UserNotFoundException(String.format("Revoker with ID '%s' not found", revokerId));

        InviteCode inviteCode = inviteCodeRepository.findById(codeId)
                .orElseThrow(() -> new InviteCodeNotFoundException(
                        String.format("Invite code with ID '%s' not found", codeId)));

        // Admin can revoke any code. Non-admin can only revoke their own.
        if (!isAdmin && (inviteCode.getCreatedBy() == null
                || !revokerId.equals(inviteCode.getCreatedBy().getId()))) {
            throw new AccessDeniedException("You are not allowed to revoke invite codes created by other users.");
        }

        if (inviteCode.getStatus() != InviteCodeStatus.PENDING)
            throw new InvalidInviteCodeException(
                    String.format("Cannot revoke invite code: current status is %s", inviteCode.getStatus()));

        inviteCode.setStatus(InviteCodeStatus.REVOKED);
        inviteCode.setRevokedAt(LocalDateTime.now());
        inviteCode.setRevokedReason(reason);
        inviteCodeRepository.save(inviteCode);

        log.info("Revoked invite code '{}' by revoker '{}' (admin={})", inviteCode.getCode(), revokerId, isAdmin);
    }

    // ── GET / LIST ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public InviteCodeResponseDto getInviteCode(UUID codeId) {
        InviteCode inviteCode = inviteCodeRepository.findById(codeId)
                .orElseThrow(() -> new InviteCodeNotFoundException(
                        String.format("Invite code with ID '%s' not found", codeId)));
        inviteCode.checkAndMarkExpired();
        if (inviteCode.getStatus() == InviteCodeStatus.EXPIRED) inviteCodeRepository.save(inviteCode);
        return mapToResponseDto(inviteCode);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InviteCodeResponseDto> listInviteCodesByCreator(UUID creatorId, Pageable pageable) {
        return inviteCodeRepository.findByCreatedById(creatorId, pageable).map(this::mapToResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InviteCodeResponseDto> listInviteCodesByEvent(UUID eventId, Pageable pageable) {
        return inviteCodeRepository.findByEventId(eventId, pageable).map(this::mapToResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InviteCodeResponseDto> listAllInviteCodes(Pageable pageable) {
        return inviteCodeRepository.findAll(pageable).map(this::mapToResponseDto);
    }

    @Override
    @Transactional
    public int markExpiredCodes() {
        int count = inviteCodeRepository.markExpiredCodes(LocalDateTime.now());
        log.info("Marked {} invite codes as expired", count);
        return count;
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    private void validateCodeForRedemption(InviteCode inviteCode) {
        if (inviteCode.getStatus() == InviteCodeStatus.REDEEMED)
            throw new InvalidInviteCodeException(String.format(
                    "Invite code '%s' has already been redeemed by %s on %s",
                    inviteCode.getCode(), inviteCode.getRedeemedBy().getName(), inviteCode.getRedeemedAt()));
        if (inviteCode.getStatus() == InviteCodeStatus.EXPIRED)
            throw new InvalidInviteCodeException(String.format(
                    "Invite code '%s' expired on %s", inviteCode.getCode(), inviteCode.getExpiresAt()));
        if (inviteCode.getStatus() == InviteCodeStatus.REVOKED)
            throw new InvalidInviteCodeException(String.format(
                    "Invite code '%s' has been revoked. Reason: %s",
                    inviteCode.getCode(), inviteCode.getRevokedReason()));
        if (!inviteCode.isValid())
            throw new InvalidInviteCodeException(String.format(
                    "Invite code '%s' is not valid for redemption", inviteCode.getCode()));
    }

    private String generateRandomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_CHARACTERS.charAt(SECURE_RANDOM.nextInt(CODE_CHARACTERS.length())));
            if ((i + 1) % 4 == 0 && i < CODE_LENGTH - 1) code.append('-');
        }
        return code.toString();
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
                .createdByUserId(inviteCode.getCreatedBy().getId())
                .createdAt(inviteCode.getCreatedAt())
                .expiresAt(inviteCode.getExpiresAt())
                .redeemedBy(inviteCode.getRedeemedBy() != null ? inviteCode.getRedeemedBy().getName() : null)
                .redeemedAt(inviteCode.getRedeemedAt())
                .revokedAt(inviteCode.getRevokedAt())
                .revokedReason(inviteCode.getRevokedReason())
                .build();
    }

    private void emitInviteRedeemedAudit(User user, InviteCode inviteCode) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.INVITE_REDEEMED)
                    .actor(user).targetUser(user)
                    .event(inviteCode.getEvent())
                    .resourceType("INVITE_CODE").resourceId(inviteCode.getId())
                    .details(String.format("code=%s,role=%s", inviteCode.getCode(), inviteCode.getRoleName()))
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit INVITE_REDEEMED audit: {}", e.getMessage());
        }
    }

    private void emitAdminRoleGrantedAudit(User newAdmin, InviteCode inviteCode) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.ADMIN_ROLE_GRANTED_VIA_INVITE)
                    .actor(newAdmin).targetUser(newAdmin)
                    .resourceType("INVITE_CODE").resourceId(inviteCode.getId())
                    .details(String.format("ADMIN role granted via invite code '%s' created by '%s'",
                            inviteCode.getCode(), inviteCode.getCreatedBy().getName()))
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
            if (user == null) user = systemUserProvider.getSystemUser();
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.FAILED_INVITE_REDEMPTION)
                    .actor(user).targetUser(user)
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
            log.error("Failed to emit FAILED_INVITE_REDEMPTION audit: {}", e.getMessage());
        }
    }
}