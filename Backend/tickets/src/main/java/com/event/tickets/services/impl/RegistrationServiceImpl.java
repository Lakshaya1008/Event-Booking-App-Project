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
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;

/**
 * Registration Service Implementation
 *
 * FIX #2: STAFF event assignment was a TODO/no-op. Users got the Keycloak role
 * but were never added to the event.staff list. Now actually executes.
 *
 * FIX #12: Outer catch block double-rolled-back Keycloak after inner catches
 * had already done it. Inner catches now null keycloakUserId after rollback
 * so the outer catch skips the second deleteUser() call.
 *
 * EMAIL: sends registration confirmation after successful registration.
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
    @Transactional
    public RegisterResponseDto register(RegisterRequestDto request) {
        HttpServletRequest httpRequest = getCurrentRequest();
        String clientIp = extractClientIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);

        log.info("Starting registration: email={}, inviteCode={}",
                request.getEmail(), request.getInviteCode() != null ? "PROVIDED" : "NONE");

        emitAuditEvent(null, null, null, AuditAction.REGISTRATION_ATTEMPT,
                "email=" + request.getEmail() + ",inviteCode=" +
                        (request.getInviteCode() != null ? request.getInviteCode() : "NONE"),
                clientIp, userAgent);

        // FIX #12: keycloakUserId is nulled after each inner rollback so the
        // outer catch never attempts a second deleteUser() call
        UUID keycloakUserId = null;
        InviteCode inviteCode = null;
        String assignedRole = "ATTENDEE";
        UUID eventId = null;

        try {
            // Step 1: Email uniqueness
            if (userRepository.existsByEmail(request.getEmail())) {
                emitAuditEvent(null, null, null, AuditAction.REGISTRATION_FAILED,
                        "email=" + request.getEmail() + ",reason=EMAIL_ALREADY_EXISTS", clientIp, userAgent);
                throw new EmailAlreadyInUseException("Email already in use: " + request.getEmail());
            }

            // Step 2: Validate invite code
            if (request.getInviteCode() != null) {
                inviteCode = validateAndGetInviteCode(request.getInviteCode());
                assignedRole = inviteCode.getRoleName();
                eventId = inviteCode.getEvent() != null ? inviteCode.getEvent().getId() : null;
                log.info("Invite code validated: role={}, eventId={}", assignedRole, eventId);
            }

            // Step 3: Check Keycloak for existing user
            UUID existingKeycloakUserId = keycloakAdminService.getUserIdByEmail(request.getEmail());
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
                            request.getEmail(), request.getPassword(), request.getName());
                    log.info("Keycloak user created: userId={}", keycloakUserId);
                } catch (Exception e) {
                    emitAuditEvent(null, null, null, AuditAction.REGISTRATION_FAILED,
                            "email=" + request.getEmail() + ",reason=KEYCLOAK_CREATION_FAILED", clientIp, userAgent);
                    throw new KeycloakUserCreationException("Failed to create user in Keycloak: " + e.getMessage(), e);
                }
            }

            // Step 4: Assign role in Keycloak
            try {
                keycloakAdminService.assignRoleToUser(keycloakUserId, assignedRole);
                log.info("Role assigned: userId={}, role={}", keycloakUserId, assignedRole);
            } catch (Exception e) {
                rollbackKeycloakUser(keycloakUserId);
                keycloakUserId = null; // FIX #12: prevent outer catch re-rolling back
                emitAuditEvent(null, null, null, AuditAction.REGISTRATION_FAILED,
                        "email=" + request.getEmail() + ",reason=ROLE_ASSIGNMENT_FAILED", clientIp, userAgent);
                throw new RegistrationException("Failed to assign role: " + e.getMessage(), e);
            }

            // Step 5: Create user in DB
            User user = new User();
            user.setId(keycloakUserId);
            user.setEmail(request.getEmail());
            user.setName(request.getName());
            user.setApprovalStatus(ApprovalStatus.PENDING);

            try {
                user = userRepository.save(user);
                log.info("User record created: userId={}, email={}", user.getId(), user.getEmail());
            } catch (Exception e) {
                rollbackKeycloakUser(keycloakUserId);
                keycloakUserId = null; // FIX #12
                emitAuditEvent(null, null, null, AuditAction.REGISTRATION_FAILED,
                        "email=" + request.getEmail() + ",reason=DB_PERSISTENCE_FAILED", clientIp, userAgent);
                throw new RegistrationException("Failed to create user record: " + e.getMessage(), e);
            }

            // Step 6: FIX #2 — STAFF event assignment now actually executes
            // Previously this was a TODO/no-op — users got the role in Keycloak but
            // were never added to the event.staff list, so staff access didn't work.
            if ("STAFF".equals(assignedRole) && eventId != null) {
                final User savedUser = user;
                final UUID inviteEventId = eventId; // FIX: capture for lambda
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
                    keycloakUserId = null; // FIX #12
                    emitAuditEvent(null, null, null, AuditAction.REGISTRATION_FAILED,
                            "email=" + request.getEmail() + ",reason=STAFF_ASSIGNMENT_FAILED", clientIp, userAgent);
                    throw new RegistrationException("Failed to assign staff to event: " + e.getMessage(), e);
                }
            }

            // Step 7: Mark invite as redeemed
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

            // Step 8: Audit success
            Event eventForAudit = null;
            if (eventId != null) {
                eventForAudit = new Event();
                eventForAudit.setId(eventId);
            }
            userRepository.saveAndFlush(user);
            emitAuditEvent(user, user, eventForAudit, AuditAction.REGISTRATION_SUCCESS,
                    "email=" + request.getEmail() + ",role=" + assignedRole, clientIp, userAgent);

            // Step 9: Send confirmation email
            emailService.sendRegistrationEmail(user.getEmail(), user.getName());

            // Step 10: Build response
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
            // FIX #12: only rollback if inner catches have NOT already done so
            if (keycloakUserId != null) {
                try {
                    rollbackKeycloakUser(keycloakUserId);
                } catch (Exception re) {
                    log.error("CRITICAL: Failed to rollback Keycloak user: userId={}", keycloakUserId);
                }
            }
            if (e instanceof EmailAlreadyInUseException ||
                    e instanceof InvalidInviteCodeException ||
                    e instanceof RegistrationException) {
                throw e;
            }
            throw new RegistrationException("Registration failed: " + e.getMessage(), e);
        }
    }

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

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}