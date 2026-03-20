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
 * FIXES APPLIED IN THIS VERSION:
 *
 * FIX 1 — jakarta.transaction.@Transactional replaced with org.springframework.
 *   The Spring-managed annotation enables:
 *   - rollbackFor = Exception.class (explicit rollback on any checked exception)
 *   - TestContext integration (TransactionalTestExecutionListener works correctly)
 *   - readOnly=true on query methods (minor: not applicable here since register() writes)
 *   The previous jakarta annotation was a functional inconsistency — it works in
 *   Spring Boot via bridge, but signals the developer didn't consciously choose.
 *
 * FIX 2 — DataIntegrityViolationException catch on userRepository.save().
 *   BEFORE: existsByEmail() → createUser() was not atomic.
 *   Two concurrent registration requests with the same email could both call
 *   existsByEmail() → get false → both proceed to keycloakAdminService.createUser().
 *   The second Keycloak call returns 409 (caught as KeycloakUserCreationException).
 *   But if both somehow reach userRepository.save(), the DB unique constraint on
 *   users.email throws DataIntegrityViolationException which was uncaught — it
 *   bubbled up as a generic 500, AND the Keycloak user was left orphaned.
 *   AFTER: DataIntegrityViolationException is caught at userRepository.save(),
 *   Keycloak is rolled back, and EmailAlreadyInUseException is thrown cleanly.
 *
 * FIX 3 — getCurrentRequest() centralised via RequestUtil.
 *   Removed the private copy-paste helper; delegates to RequestUtil.
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
        String clientIp = extractClientIp(getCurrentRequest());
        String userAgent = extractUserAgent(getCurrentRequest());

        log.info("Starting registration: email={}, inviteCode={}",
                request.getEmail(), request.getInviteCode() != null ? "PROVIDED" : "NONE");

        emitAuditEvent(null, null, null, AuditAction.REGISTRATION_ATTEMPT,
                "email=" + request.getEmail() + ",inviteCode=" +
                        (request.getInviteCode() != null ? request.getInviteCode() : "NONE"),
                clientIp, userAgent);

        // keycloakUserId is nulled after each inner rollback so the outer catch
        // never attempts a second deleteUser() call (FIX #12 — preserved)
        UUID keycloakUserId = null;
        InviteCode inviteCode = null;
        String assignedRole = "ATTENDEE";
        UUID eventId = null;

        try {
            // Step 1: Normalize email (case-insensitive, RFC 5321)
            String normalizedEmail = request.getEmail().toLowerCase().trim();

            // Step 2: Email uniqueness — first check
            if (userRepository.existsByEmail(normalizedEmail)) {
                emitAuditEvent(null, null, null, AuditAction.REGISTRATION_FAILED,
                        "email=" + normalizedEmail + ",reason=EMAIL_ALREADY_EXISTS", clientIp, userAgent);
                throw new EmailAlreadyInUseException("Email already in use: " + normalizedEmail);
            }

            // Step 3: Validate invite code
            if (request.getInviteCode() != null) {
                inviteCode = validateAndGetInviteCode(request.getInviteCode());
                assignedRole = inviteCode.getRoleName();
                eventId = inviteCode.getEvent() != null ? inviteCode.getEvent().getId() : null;
                log.info("Invite code validated: role={}, eventId={}", assignedRole, eventId);
            }

            // Step 4: Check Keycloak for existing user
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

            // Step 5: Assign role in Keycloak
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

            // Step 6: Create user in DB
            User user = new User();
            user.setId(keycloakUserId);
            user.setEmail(normalizedEmail);
            user.setName(request.getName());
            user.setApprovalStatus(ApprovalStatus.PENDING);

            try {
                user = userRepository.save(user);
                log.info("User record created: userId={}, email={}", user.getId(), user.getEmail());
            } catch (DataIntegrityViolationException e) {
                // FIX 2: Race condition — another thread registered same email between
                // our existsByEmail() check and this save. DB unique constraint fires.
                // Roll back Keycloak and return a clean EmailAlreadyInUseException.
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
                        "email=" + request.getEmail() + ",reason=DB_PERSISTENCE_FAILED", clientIp, userAgent);
                throw new RegistrationException("Failed to create user record: " + e.getMessage(), e);
            }

            // Step 7: STAFF event assignment (FIX #2 — preserved)
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
                            "email=" + request.getEmail() + ",reason=STAFF_ASSIGNMENT_FAILED", clientIp, userAgent);
                    throw new RegistrationException("Failed to assign staff to event: " + e.getMessage(), e);
                }
            }

            // Step 8: Mark invite as redeemed
            if (inviteCode != null) {
                try {
                    inviteCode.setStatus(InviteCodeStatus.REDEEMED);
                    inviteCode.setRedeemedBy(user);
                    inviteCode.setRedeemedAt(LocalDateTime.now());
                    inviteCodeRepository.save(inviteCode);
                    log.info("Invite code redeemed: code={}", inviteCode.getCode());
                } catch (Exception e) {
                    log.error("Failed to mark invite as redeemed (non-critical): code={}, error={}",
                            inviteCode.getCode(), e.getMessage());
                }
            }

            // Step 9: Audit success
            Event eventForAudit = null;
            if (eventId != null) {
                eventForAudit = new Event();
                eventForAudit.setId(eventId);
            }
            emitAuditEvent(user, user, eventForAudit, AuditAction.REGISTRATION_SUCCESS,
                    "email=" + request.getEmail() + ",role=" + assignedRole, clientIp, userAgent);

            // Step 10: Send confirmation email (fire-and-forget — never propagates)
            emailService.sendRegistrationEmail(user.getEmail(), user.getName());

            // Step 11: Build response
            RegisterResponseDto response = new RegisterResponseDto();
            response.setMessage("Registration successful! Your account is pending admin approval.");
            response.setEmail(user.getEmail());
            response.setRequiresApproval(true);
            response.setAssignedRole(assignedRole);
            response.setInstructions("You will receive an email once your account has been reviewed.");

            log.info("Registration completed: email={}, role={}, userId={}",
                    user.getEmail(), assignedRole, user.getId());
            return response;

        } catch (Exception e) {
            // Only rollback Keycloak if inner catches have NOT already done so (FIX #12)
            if (keycloakUserId != null) {
                try {
                    rollbackKeycloakUser(keycloakUserId);
                } catch (Exception re) {
                    log.error("CRITICAL: Failed to rollback Keycloak user: userId={}", keycloakUserId);
                }
            }
            if (e instanceof EmailAlreadyInUseException ||
                    e instanceof InvalidInviteCodeException ||
                    e instanceof InviteCodeNotFoundException ||
                    e instanceof RegistrationException) {
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