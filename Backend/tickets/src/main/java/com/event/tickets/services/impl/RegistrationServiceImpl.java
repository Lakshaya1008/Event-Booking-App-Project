package com.event.tickets.services.impl;

import com.event.tickets.domain.dtos.RegisterRequestDto;
import com.event.tickets.domain.dtos.RegisterResponseDto;
import com.event.tickets.domain.entities.ApprovalStatus;
import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.InviteCode;
import com.event.tickets.domain.entities.InviteCodeStatus;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.EmailAlreadyInUseException;
import com.event.tickets.exceptions.InvalidInviteCodeException;
import com.event.tickets.exceptions.InviteCodeNotFoundException;
import com.event.tickets.exceptions.KeycloakUserCreationException;
import com.event.tickets.exceptions.RegistrationException;
import com.event.tickets.repositories.EventRepository;
import com.event.tickets.repositories.InviteCodeRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuditLogService;
import com.event.tickets.services.EmailService;
import com.event.tickets.services.KeycloakAdminService;
import com.event.tickets.services.RegistrationService;
import com.event.tickets.services.SystemUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;
import static com.event.tickets.util.RequestUtil.getCurrentRequest;

/**
 * FIXES APPLIED:
 *
 * FIX-R1 — Role-conditional approval status.
 *   ATTENDEE (no invite code) → ApprovalStatus.APPROVED + Keycloak enabled immediately.
 *   ORGANIZER / STAFF / ADMIN (invite code) → ApprovalStatus.PENDING + Keycloak disabled.
 *   This implements the actual business rule: attendees need no admin review.
 *
 * FIX-R2 — getCurrentRequest() resolved ONCE at the top and reused.
 *   Previously called twice (extractClientIp + extractUserAgent each called it separately).
 *
 * FIX-R3 — All audit events use normalizedEmail, not raw request.getEmail().
 *   Previously audit logs recorded the original casing; DB stores lowercase.
 *
 * FIX-R4 — Invite code redemption failure is now re-thrown.
 *   A failed save here means the code stays PENDING and can be reused.
 *   Non-critical comment removed — this IS critical to the single-use guarantee.
 *
 * FIX-R5 — RegisterResponseDto no longer leaks assignedRole before approval.
 *   PENDING users should discover their role from the approval email, not the 201 response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationServiceImpl implements RegistrationService {

    private final UserRepository userRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final EventRepository eventRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final SystemUserProvider systemUserProvider;
    private final AuditLogService auditLogService;
    private final EmailService emailService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterResponseDto register(RegisterRequestDto request) {
        // FIX-R2: resolve request once, pass the reference everywhere
        HttpServletRequest httpRequest = getCurrentRequest();
        String clientIp   = extractClientIp(httpRequest);
        String userAgent  = extractUserAgent(httpRequest);

        String normalizedInviteCode = normalizeInviteCode(request.getInviteCode());
        // FIX-R3: normalize email immediately so every audit uses the stored value
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        log.info("Starting registration: email={}, inviteCode={}",
                normalizedEmail, normalizedInviteCode != null ? "PROVIDED" : "NONE");

        emitAuditEvent(null, null, null, AuditAction.REGISTRATION_ATTEMPT,
                "email=" + normalizedEmail + ",inviteCode=" + (normalizedInviteCode != null ? "PROVIDED" : "NONE"),
                clientIp, userAgent);

        UUID keycloakUserId = null;
        InviteCode inviteCode = null;
        String assignedRole = "ATTENDEE";
        UUID eventId = null;

        try {
            // Step 1: Email uniqueness check
            if (userRepository.existsByEmail(normalizedEmail)) {
                emitAuditEvent(null, null, null, AuditAction.REGISTRATION_FAILED,
                        "email=" + normalizedEmail + ",reason=EMAIL_ALREADY_EXISTS", clientIp, userAgent);
                throw new EmailAlreadyInUseException("Email already in use: " + normalizedEmail);
            }

            // Step 2: Validate invite code if provided
            if (normalizedInviteCode != null) {
                inviteCode = validateAndGetInviteCode(normalizedInviteCode);
                assignedRole = inviteCode.getRoleName();
                eventId = inviteCode.getEvent() != null ? inviteCode.getEvent().getId() : null;
                log.info("Invite code validated: role={}, eventId={}", assignedRole, eventId);
            }

            // FIX-R1: Determine approval status based on role.
            // ATTENDEE needs no admin review — approve and enable immediately.
            // All other roles (ORGANIZER, STAFF, ADMIN) require admin approval.
            boolean requiresApproval = !"ATTENDEE".equals(assignedRole);
            ApprovalStatus initialStatus = requiresApproval ? ApprovalStatus.PENDING : ApprovalStatus.APPROVED;

            // Step 3: Check Keycloak for existing user
            UUID existingKeycloakUserId = keycloakAdminService.getUserIdByEmail(normalizedEmail);
            if (existingKeycloakUserId != null) {
                if (userRepository.existsById(existingKeycloakUserId)) {
                    throw new RegistrationException("User already registered");
                } else {
                    keycloakUserId = existingKeycloakUserId;
                    log.info("Reusing existing Keycloak user: userId={}", keycloakUserId);
                }
            } else {
                try {
                    keycloakUserId = keycloakAdminService.createUser(
                            normalizedEmail, request.getPassword(), request.getName());
                    log.info("Keycloak user created: userId={}", keycloakUserId);
                } catch (Exception e) {
                    emitAuditEvent(null, null, null, AuditAction.REGISTRATION_FAILED,
                            "email=" + normalizedEmail + ",reason=KEYCLOAK_CREATION_FAILED", clientIp, userAgent);
                    throw new KeycloakUserCreationException("Failed to create user in Keycloak: " + e.getMessage(), e);
                }
            }

            // Step 4: Assign role in Keycloak
            try {
                keycloakAdminService.assignRoleToUser(keycloakUserId, assignedRole);
                log.info("Role assigned: userId={}, role={}", keycloakUserId, assignedRole);
            } catch (Exception e) {
                rollbackKeycloakUser(keycloakUserId);
                keycloakUserId = null;
                emitAuditEvent(null, null, null, AuditAction.REGISTRATION_FAILED,
                        "email=" + normalizedEmail + ",reason=ROLE_ASSIGNMENT_FAILED", clientIp, userAgent);
                throw new RegistrationException("Failed to assign role: " + e.getMessage(), e);
            }

            // FIX-R1: ATTENDEE is enabled immediately; others wait for admin approval.
            if (!requiresApproval) {
                try {
                    keycloakAdminService.activateUser(keycloakUserId);
                    log.info("ATTENDEE auto-activated in Keycloak: userId={}", keycloakUserId);
                } catch (Exception e) {
                    // Activation failure is non-fatal for ATTENDEEs — the DatabaseInitializer
                    // normalizeKeycloakStateForApprovedUsers will reconcile on next startup.
                    log.warn("ATTENDEE Keycloak activation failed (will reconcile on restart): userId={}", keycloakUserId);
                }
            }

            // Step 5: Persist user to DB
            User user = new User();
            user.setId(keycloakUserId);
            user.setEmail(normalizedEmail);
            user.setName(request.getName());
            user.setApprovalStatus(initialStatus);   // FIX-R1

            // FIX-R1: Auto-approve ATTENDEEs — stamp the approval timestamp now
            if (!requiresApproval) {
                user.setApprovedAt(LocalDateTime.now());
            }

            try {
                user = userRepository.save(user);
                log.info("User record created: userId={}, email={}, status={}",
                        user.getId(), user.getEmail(), initialStatus);
            } catch (DataIntegrityViolationException e) {
                rollbackKeycloakUser(keycloakUserId);
                keycloakUserId = null;
                emitAuditEvent(null, null, null, AuditAction.REGISTRATION_FAILED,
                        "email=" + normalizedEmail + ",reason=EMAIL_RACE_CONDITION", clientIp, userAgent);
                log.warn("Email duplicate race condition detected: email={}", normalizedEmail);
                throw new EmailAlreadyInUseException("Email already in use: " + normalizedEmail);
            } catch (Exception e) {
                rollbackKeycloakUser(keycloakUserId);
                keycloakUserId = null;
                emitAuditEvent(null, null, null, AuditAction.REGISTRATION_FAILED,
                        "email=" + normalizedEmail + ",reason=DB_PERSISTENCE_FAILED", clientIp, userAgent);
                throw new RegistrationException("Failed to create user record: " + e.getMessage(), e);
            }

            // Step 6: STAFF event assignment
            if ("STAFF".equals(assignedRole) && eventId != null) {
                final User savedUser = user;
                final UUID inviteEventId = eventId;
                try {
                    Event event = eventRepository.findById(inviteEventId)
                            .orElseThrow(() -> new RegistrationException("Event not found: " + inviteEventId));
                    boolean alreadyAssigned = event.getStaff().stream()
                            .anyMatch(s -> s.getId().equals(savedUser.getId()));
                    if (!alreadyAssigned) {
                        event.getStaff().add(savedUser);
                        eventRepository.save(event);
                    }
                    log.info("STAFF user '{}' assigned to event '{}'", savedUser.getId(), eventId);
                } catch (Exception e) {
                    userRepository.delete(savedUser);
                    rollbackKeycloakUser(keycloakUserId);
                    keycloakUserId = null;
                    emitAuditEvent(null, null, null, AuditAction.REGISTRATION_FAILED,
                            "email=" + normalizedEmail + ",reason=STAFF_ASSIGNMENT_FAILED", clientIp, userAgent);
                    throw new RegistrationException("Failed to assign staff to event: " + e.getMessage(), e);
                }
            }

            // Step 7: Mark invite as redeemed.
            // FIX-R4: Failure here is re-thrown — a failed save leaves the code PENDING
            // and a second user could redeem it. The @Transactional will roll back everything.
            if (inviteCode != null) {
                inviteCode.setStatus(InviteCodeStatus.REDEEMED);
                inviteCode.setRedeemedBy(user);
                inviteCode.setRedeemedAt(LocalDateTime.now());
                inviteCodeRepository.save(inviteCode);
                log.info("Invite code redeemed: code={}", inviteCode.getCode());
            }

            // Step 8: Audit success (FIX-R3: uses normalizedEmail everywhere)
            Event eventForAudit = null;
            if (eventId != null) {
                eventForAudit = new Event();
                eventForAudit.setId(eventId);
            }
            emitAuditEvent(user, user, eventForAudit, AuditAction.REGISTRATION_SUCCESS,
                    "email=" + normalizedEmail + ",role=" + assignedRole + ",requiresApproval=" + requiresApproval,
                    clientIp, userAgent);

            // Step 9: Send confirmation email (fire-and-forget)
            emailService.sendRegistrationEmail(user.getEmail(), user.getName());

            // Step 10: Build response
            // FIX-R5: Do NOT expose assignedRole in the response for PENDING users.
            // They will learn their role from the approval email once admin acts.
            RegisterResponseDto response = new RegisterResponseDto();
            response.setEmail(user.getEmail());
            response.setRequiresApproval(requiresApproval);

            if (requiresApproval) {
                response.setMessage("Registration successful! Your account is pending admin approval.");
                response.setInstructions("You will receive an email once your account has been reviewed.");
                // Role intentionally omitted for pending users
            } else {
                response.setMessage("Registration successful! You can log in immediately.");
                response.setAssignedRole(assignedRole); // ATTENDEE — safe to expose, no sensitivity
                response.setInstructions("Log in with your email and password.");
            }

            log.info("Registration completed: email={}, role={}, requiresApproval={}",
                    user.getEmail(), assignedRole, requiresApproval);
            return response;

        } catch (Exception e) {
            if (keycloakUserId != null) {
                try {
                    rollbackKeycloakUser(keycloakUserId);
                } catch (Exception re) {
                    log.error("CRITICAL: Failed to rollback Keycloak user: userId={}", keycloakUserId);
                }
            }
            if (e instanceof EmailAlreadyInUseException
                    || e instanceof InvalidInviteCodeException
                    || e instanceof InviteCodeNotFoundException
                    || e instanceof RegistrationException) {
                throw e;
            }
            throw new RegistrationException("Registration failed: " + e.getMessage(), e);
        }
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    private InviteCode validateAndGetInviteCode(String code) {
        InviteCode inviteCode = inviteCodeRepository.findByCode(code)
                .orElseThrow(() -> new InviteCodeNotFoundException("Invite code not found: " + code));
        if (!inviteCode.isValid()) {
            if (inviteCode.getStatus() == InviteCodeStatus.REDEEMED)
                throw new InvalidInviteCodeException("Invite code has already been redeemed");
            if (inviteCode.getStatus() == InviteCodeStatus.EXPIRED)
                throw new InvalidInviteCodeException("Invite code has expired");
            if (inviteCode.getStatus() == InviteCodeStatus.REVOKED)
                throw new InvalidInviteCodeException("Invite code has been revoked");
            throw new InvalidInviteCodeException("Invite code is not valid");
        }
        return inviteCode;
    }

    private String normalizeInviteCode(String inviteCode) {
        if (inviteCode == null) return null;
        String trimmed = inviteCode.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void rollbackKeycloakUser(UUID keycloakUserId) {
        if (keycloakUserId == null) return;
        try {
            keycloakAdminService.deleteUser(keycloakUserId);
            log.info("Keycloak user rolled back: userId={}", keycloakUserId);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to rollback Keycloak user. userId={}", keycloakUserId);
        }
    }

    private void emitAuditEvent(User actor, User targetUser, Event event, AuditAction action,
                                String details, String ipAddress, String userAgent) {
        try {
            if (actor == null) actor = systemUserProvider.getSystemUser();
            AuditLog auditLog = AuditLog.builder()
                    .action(action).actor(actor).targetUser(targetUser)
                    .event(event).details(details).ipAddress(ipAddress).userAgent(userAgent)
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit audit: action={}, error={}", action, e.getMessage());
        }
    }
}