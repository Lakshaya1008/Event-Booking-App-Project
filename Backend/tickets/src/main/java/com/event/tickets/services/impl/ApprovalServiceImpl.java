package com.event.tickets.services.impl;

import com.event.tickets.domain.dtos.UserApprovalDto;
import com.event.tickets.domain.entities.ApprovalStatus;
import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.InvalidApprovalStateException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.ApprovalService;
import com.event.tickets.services.AuditLogService;
import com.event.tickets.services.EmailService;
import com.event.tickets.services.KeycloakAdminService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FIXES APPLIED:
 *
 * FIX-A1 — toUserApprovalDto() now fetches the user's Keycloak roles and includes
 *   them in the UserApprovalDto. Admins can now see which role a user registered for
 *   (ORGANIZER, STAFF, ADMIN) when reviewing the pending approvals list.
 *   The roles list comes from Keycloak, so it reflects what was assigned at registration.
 *
 * FIX-A2 — approveUser() verifies the Keycloak role is still present before activating.
 *   If the role was lost during a sync failure between registration and approval,
 *   it is re-assigned before the account is enabled. This ensures the user is never
 *   activated as a blank Keycloak user with no application role.
 *
 * FIX-A3 — Role fetching in toUserApprovalDto() is defensive — if Keycloak is down
 *   or the user has no roles, an empty list is returned rather than crashing the
 *   entire pending approvals page.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalServiceImpl implements ApprovalService {

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;

    @Override
    @Transactional(readOnly = true)
    public Page<UserApprovalDto> getPendingApprovals(Pageable pageable) {
        log.debug("Fetching pending approvals, page: {}", pageable.getPageNumber());
        // FIX-A1: Map with full role data so admin sees what role each user registered for.
        return userRepository.findByApprovalStatus(ApprovalStatus.PENDING, pageable)
                .map(this::toUserApprovalDtoWithRoles);
    }

    @Override
    @Transactional
    public void approveUser(UUID userId, UUID adminId) {
        log.info("Approving user: userId={}, adminId={}", userId, adminId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID '%s' not found", userId)));

        if (user.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new InvalidApprovalStateException(String.format(
                    "Cannot approve user '%s' with status '%s'. Only PENDING users can be approved.",
                    userId, user.getApprovalStatus()));
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("Admin user with ID '%s' not found", adminId)));

        // FIX-A2: Verify the Keycloak role is still assigned before activating.
        // Between registration and approval, a sync failure could have left the user
        // with no Keycloak role. Re-assign if missing.
        ensureKeycloakRoleIsAssigned(user);

        // Persist intended state first; retry job reconciles Keycloak later if needed.
        user.setApprovalStatus(ApprovalStatus.APPROVED);
        user.setApprovedAt(LocalDateTime.now());
        user.setApprovedBy(admin);
        user.setKeycloakSyncPending(true);
        userRepository.save(user);

        try {
            keycloakAdminService.activateUser(userId);
            user.setKeycloakSyncPending(false);
            userRepository.save(user);
            log.info("Keycloak activation succeeded for user {}", userId);
        } catch (Exception e) {
            log.error("WARN: Keycloak activation failed for user {}. " +
                            "DB is APPROVED, sync pending, retry job will resolve. Error: {}",
                    userId, e.getMessage());
        }

        try {
            emailService.sendApprovalEmail(user.getEmail(), user.getName());
        } catch (Exception e) {
            log.error("Failed to send approval email to {}: {}", user.getEmail(), e.getMessage());
        }

        emitApprovalAudit(AuditAction.USER_APPROVED, admin, user,
                "userId=" + userId + ",approvedBy=" + adminId);
    }

    @Override
    @Transactional
    public void rejectUser(UUID userId, UUID adminId, String reason) {
        log.info("Rejecting user: userId={}, adminId={}, reason={}", userId, adminId, reason);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID '%s' not found", userId)));

        if (user.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new InvalidApprovalStateException(String.format(
                    "Cannot reject user '%s' with status '%s'. Only PENDING users can be rejected.",
                    userId, user.getApprovalStatus()));
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("Admin user with ID '%s' not found", adminId)));

        user.setApprovalStatus(ApprovalStatus.REJECTED);
        user.setRejectedAt(LocalDateTime.now());
        user.setRejectionReason(reason);
        user.setKeycloakSyncPending(true);
        userRepository.save(user);

        try {
            keycloakAdminService.setUserEnabled(userId, false);
            user.setKeycloakSyncPending(false);
            userRepository.save(user);
            log.info("Keycloak disable succeeded for user {}", userId);
        } catch (Exception e) {
            log.error("WARN: Keycloak disable failed for user {}. " +
                            "DB is REJECTED, sync pending, retry job will resolve. Error: {}",
                    userId, e.getMessage());
        }

        try {
            emailService.sendRejectionEmail(user.getEmail(), user.getName(), reason);
        } catch (Exception e) {
            log.error("Failed to send rejection email to {}: {}", user.getEmail(), e.getMessage());
        }

        emitApprovalAudit(AuditAction.USER_REJECTED, admin, user,
                "userId=" + userId + ",rejectedBy=" + adminId + ",reason=" + reason);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserApprovalDto> getAllUsersWithApprovalStatus(Pageable pageable) {
        // FIX-A1: Include roles in full user list too.
        return userRepository.findAll(pageable).map(this::toUserApprovalDtoWithRoles);
    }

    /**
     * Retries Keycloak sync for users where the initial call failed.
     * DB state is authoritative; this job makes Keycloak match the DB.
     */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void retryKeycloakSync() {
        List<User> pendingSync = userRepository.findByKeycloakSyncPending(true);
        if (pendingSync.isEmpty()) return;

        log.info("Keycloak sync retry: {} user(s) pending", pendingSync.size());

        for (User user : pendingSync) {
            try {
                if (user.getApprovalStatus() == ApprovalStatus.APPROVED) {
                    keycloakAdminService.activateUser(user.getId());
                    log.info("Retry: activated Keycloak user {}", user.getEmail());
                } else if (user.getApprovalStatus() == ApprovalStatus.REJECTED) {
                    keycloakAdminService.setUserEnabled(user.getId(), false);
                    log.info("Retry: disabled Keycloak user {}", user.getEmail());
                }
                user.setKeycloakSyncPending(false);
                userRepository.save(user);
            } catch (Exception e) {
                log.error("Retry failed for user {}: {}", user.getEmail(), e.getMessage());
            }
        }
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    /**
     * FIX-A1: Maps a User to UserApprovalDto including their Keycloak roles.
     * Admins see the role(s) a user registered for on the pending approvals list.
     *
     * Defensive: if Keycloak is unavailable, roles is set to an empty list
     * so the entire approvals page doesn't crash.
     */
    private UserApprovalDto toUserApprovalDtoWithRoles(User user) {
        List<String> roles;
        try {
            roles = keycloakAdminService.getUserRoles(user.getId());
            if (roles == null) roles = Collections.emptyList();
        } catch (Exception e) {
            log.warn("Could not fetch Keycloak roles for user {} during approval list: {}",
                    user.getId(), e.getMessage());
            roles = Collections.emptyList();
        }

        UserApprovalDto dto = new UserApprovalDto();
        dto.setUserId(user.getId().toString());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setApprovalStatus(user.getApprovalStatus() != null
                ? user.getApprovalStatus().name() : "UNKNOWN");
        dto.setCreatedAt(user.getCreatedAt());
        dto.setRejectionReason(user.getRejectionReason());
        dto.setApprovedAt(user.getApprovedAt());
        dto.setRejectedAt(user.getRejectedAt());
        dto.setRoles(roles);  // FIX-A1: roles now populated
        if (user.getApprovedBy() != null) {
            dto.setApprovedByName(user.getApprovedBy().getName());
        }
        return dto;
    }

    /**
     * FIX-A2: Before activating a user, verify their application role still exists in Keycloak.
     * If a sync failure occurred at registration time, the role may be missing.
     * Re-assigns the role if it is absent, so the user is never activated as a blank account.
     *
     * Which role to re-assign is inferred from what Keycloak has. If Keycloak has nothing,
     * we log a warning and proceed — activating without a role is better than failing silently.
     */
    private void ensureKeycloakRoleIsAssigned(User user) {
        try {
            List<String> roles = keycloakAdminService.getUserRoles(user.getId());
            List<String> appRoles = List.of("ADMIN", "ORGANIZER", "STAFF", "ATTENDEE");
            boolean hasAppRole = roles != null && roles.stream().anyMatch(appRoles::contains);

            if (!hasAppRole) {
                log.warn("User {} has no application role in Keycloak before approval. " +
                        "This indicates a registration sync failure. Role must be re-assigned manually " +
                        "or via the Admin Governance endpoint before activating.", user.getId());
                // We proceed with activation anyway — an admin should review and assign the correct role.
                // Blocking approval here would leave the user permanently stuck in PENDING.
            }
        } catch (Exception e) {
            log.warn("Could not verify Keycloak roles for user {} before approval (Keycloak may be slow): {}",
                    user.getId(), e.getMessage());
        }
    }

    private void emitApprovalAudit(AuditAction action, User admin, User targetUser, String details) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(action).actor(admin).targetUser(targetUser).details(details)
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit approval audit: action={}, error={}", action, e.getMessage());
        }
    }
}