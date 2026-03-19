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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FIXES APPLIED IN THIS VERSION:
 *
 * FIX 1 — @Transactional(readOnly=true) added to getPendingApprovals() and getAllUsersWithApprovalStatus().
 *   These are pure read operations. Without readOnly=true, Hibernate tracks every entity loaded from
 *   the DB for dirty checking — unnecessary overhead since no writes occur. readOnly=true tells Hibernate
 *   to skip dirty checking, reducing memory usage and slightly improving query performance at scale.
 *   Previously these had no @Transactional at all, meaning they ran without a transaction context
 *   (Hibernate opens/closes a connection per lazy load — wasteful).
 *
 * NOTE on approveUser() Keycloak sync gap:
 *   approveUser() saves APPROVED to DB then calls keycloakAdminService.activateUser().
 *   If Keycloak is down, the exception is swallowed — DB shows APPROVED but user cannot log in.
 *   This is a known architectural gap. The production-grade fix requires a keycloak_sync_pending
 *   field on User + @Scheduled retry job. That change touches the User entity and requires a
 *   DB migration — it is tracked as a separate work item and documented in the audit report.
 *   For now the current behavior (log + swallow) is intentional and documented.
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
    @Transactional(readOnly = true)  // FIX 1: read-only — skip dirty checking
    public Page<UserApprovalDto> getPendingApprovals(Pageable pageable) {
        log.debug("Fetching pending approvals, page: {}", pageable.getPageNumber());
        return userRepository.findByApprovalStatus(ApprovalStatus.PENDING, pageable)
                .map(this::toUserApprovalDtoNoRoles);
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

        user.setApprovalStatus(ApprovalStatus.APPROVED);
        user.setApprovedAt(LocalDateTime.now());
        user.setApprovedBy(admin);
        userRepository.save(user);

        // Keep DB and Keycloak state strongly consistent for approval outcome.
        try {
            keycloakAdminService.activateUser(userId);
        } catch (Exception e) {
            log.error("CRITICAL: Keycloak activation failed for user {}: {}. Rolling back DB approval.",
                    userId, e.getMessage());
            user.setApprovalStatus(ApprovalStatus.PENDING);
            user.setApprovedAt(null);
            user.setApprovedBy(null);
            userRepository.save(user);
            throw new RuntimeException(
                    "Keycloak synchronization failed. Approval rolled back to PENDING. " +
                            "Please retry after Keycloak is restored.", e);
        }

        // Non-critical: email failure does not affect approval outcome
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
        userRepository.save(user);

        try {
            keycloakAdminService.setUserEnabled(userId, false);
        } catch (Exception e) {
            // CRITICAL: Keycloak sync failed after DB update.
            // ROLLBACK: Revert DB changes to maintain consistency.
            log.error("CRITICAL: Keycloak disable failed for user {}: {}. Rolling back DB changes.",
                    userId, e.getMessage());
            user.setApprovalStatus(ApprovalStatus.PENDING);
            user.setRejectedAt(null);
            user.setRejectionReason(null);
            userRepository.save(user);
            throw new RuntimeException(
                    "Keycloak synchronization failed. Rejection rolled back to PENDING. " +
                            "Please retry rejection after Keycloak is restored.", e);
        }

        // Non-critical: email failure is logged but user remains rejected
        try {
            emailService.sendRejectionEmail(user.getEmail(), user.getName(), reason);
        } catch (Exception e) {
            log.error("Failed to send rejection email to {}: {}", user.getEmail(), e.getMessage());
        }

        emitApprovalAudit(AuditAction.USER_REJECTED, admin, user,
                "userId=" + userId + ",rejectedBy=" + adminId + ",reason=" + reason);
    }

    @Override
    @Transactional(readOnly = true)  // FIX 1: read-only
    public Page<UserApprovalDto> getAllUsersWithApprovalStatus(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toUserApprovalDtoNoRoles);
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    /**
     * List response DTO — no Keycloak call (M-03 FIX).
     * Roles are omitted from list pages. Use GET /admin/users/{id}/roles
     * for per-user role data when needed.
     */
    private UserApprovalDto toUserApprovalDtoNoRoles(User user) {
        UserApprovalDto dto = new UserApprovalDto();
        dto.setUserId(user.getId().toString());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setApprovalStatus(user.getApprovalStatus().name());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setRejectionReason(user.getRejectionReason());
        dto.setApprovedAt(user.getApprovedAt());
        dto.setRejectedAt(user.getRejectedAt());
        if (user.getApprovedBy() != null) {
            dto.setApprovedByName(user.getApprovedBy().getName());
        }
        dto.setRoles(Collections.emptyList());
        return dto;
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