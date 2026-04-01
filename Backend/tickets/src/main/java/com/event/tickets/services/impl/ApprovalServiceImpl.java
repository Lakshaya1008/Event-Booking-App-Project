package com.event.tickets.services.impl;

import com.event.tickets.domain.dtos.UserApprovalDto;
import com.event.tickets.domain.entities.ApprovalStatus;
import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.InvalidBusinessStateException;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return userRepository.findByApprovalStatus(ApprovalStatus.PENDING, pageable)
                .map(this::toUserApprovalDtoWithRoles);
    }

    @Override
    @Transactional
    public void approveUser(UUID userId, UUID adminId) {
        log.info("Approving user: userId={}, adminId={}", userId, adminId);
        requireAdminRole(adminId);

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

        validateUserHasValidRole(user.getId());

        try {
            keycloakAdminService.activateUser(userId);
            log.info("Keycloak activation succeeded for user {}", userId);
        } catch (Exception e) {
            log.error("Keycloak activation FAILED for user {} - approval aborted", userId, e);
            throw new InvalidBusinessStateException(
                    "Cannot approve user because Keycloak activation failed", e);
        }

        user.setApprovalStatus(ApprovalStatus.APPROVED);
        user.setApprovedAt(LocalDateTime.now());
        user.setApprovedBy(admin);
        user.setKeycloakSyncPending(false);
        userRepository.save(user);

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
        requireAdminRole(adminId);

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
     * Maps a User to UserApprovalDto including current Keycloak roles.
     * If Keycloak is unavailable, roles falls back to an empty list.
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
        dto.setRoles(roles);
        if (user.getApprovedBy() != null) {
            dto.setApprovedByName(user.getApprovedBy().getName());
        }
        return dto;
    }

    private void validateUserHasValidRole(UUID userId) {
        try {
            List<String> roles = keycloakAdminService.getUserRoles(userId);
            List<String> appRoles = List.of("ADMIN", "ORGANIZER", "STAFF", "ATTENDEE");

            if (roles == null || roles.isEmpty()) {
                throw new InvalidBusinessStateException(
                        "User has no roles assigned in Keycloak. Cannot approve.");
            }

            boolean hasValidRole = roles.stream().anyMatch(appRoles::contains);
            if (!hasValidRole) {
                throw new InvalidBusinessStateException(
                        "User does not have a valid application role. Cannot approve.");
            }
        } catch (InvalidBusinessStateException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidBusinessStateException(
                    "Failed to validate user role before approval");
        }
    }

    private void requireAdminRole(UUID userId) {
        try {
            List<String> roles = keycloakAdminService.getUserRoles(userId);

            if (roles == null || roles.stream().noneMatch("ADMIN"::equals)) {
                throw new AccessDeniedException("User is not authorized to perform approval actions");
            }
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new AccessDeniedException("Failed to verify admin role");
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