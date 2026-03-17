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
 * M-03 FIX: N+1 Keycloak calls eliminated from getPendingApprovals and getAllUsersWithApprovalStatus.
 *
 * Previously toUserApprovalDto() called keycloakAdminService.getUserRoles(userId) for every user
 * on the page — a separate HTTP round-trip to Keycloak per user. A page of 20 users triggered
 * 20 Keycloak API calls, making the admin approval list extremely slow and Keycloak-rate-limit-prone.
 *
 * Fix: roles are omitted from list responses (they're expensive and rarely needed when scanning
 * pending approvals). A separate GET /admin/users/{userId}/roles endpoint exists for the rare
 * case where an admin needs the role list for a specific user.
 *
 * The toUserApprovalDtoWithRoles() method is kept for the single-user approve/reject responses
 * where one Keycloak call is acceptable.
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
    public Page<UserApprovalDto> getPendingApprovals(Pageable pageable) {
        log.debug("Fetching pending approvals, page: {}", pageable.getPageNumber());
        // M-03 FIX: toUserApprovalDtoNoRoles avoids N+1 Keycloak calls on list pages
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

        try {
            keycloakAdminService.activateUser(userId);
        } catch (Exception e) {
            log.error("Keycloak activation failed for user {}: {} — DB already updated", userId, e.getMessage());
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
        userRepository.save(user);

        try {
            keycloakAdminService.setUserEnabled(userId, false);
        } catch (Exception e) {
            log.error("Keycloak disable failed for user {}: {}", userId, e.getMessage());
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
    public Page<UserApprovalDto> getAllUsersWithApprovalStatus(Pageable pageable) {
        // M-03 FIX: no Keycloak call per user — roles not included in list pages
        return userRepository.findAll(pageable).map(this::toUserApprovalDtoNoRoles);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * M-03 FIX: List pages use this — NO Keycloak round-trip.
     * Roles are omitted. Use the AdminGovernanceController GET /admin/users/{id}/roles
     * endpoint when role data is needed for a specific user.
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
        dto.setRoles(Collections.emptyList()); // roles fetched separately on demand
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