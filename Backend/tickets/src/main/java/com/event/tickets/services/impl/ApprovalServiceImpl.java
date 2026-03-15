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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

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
        return userRepository.findByApprovalStatus(ApprovalStatus.PENDING, pageable)
                .map(this::toUserApprovalDto);
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
        user.setApprovedAt(LocalDateTime.now()); // FIX #7: only set on approval
        user.setApprovedBy(admin);
        userRepository.save(user);

        try {
            keycloakAdminService.activateUser(userId);
            log.info("Keycloak account activated: userId={}", userId);
        } catch (Exception e) {
            log.warn("Failed to activate Keycloak account (non-critical): userId={}", userId, e);
        }

        emitApprovalAudit(AuditAction.USER_APPROVED, admin, user, "status=APPROVED,adminId=" + adminId);

        // Send approval email
        emailService.sendApprovalEmail(user.getEmail(), user.getName());

        log.info("User approved successfully: userId={}, adminId={}", userId, adminId);
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
        // FIX #7: use rejectedAt NOT approvedAt on rejection
        // Previously approvedAt was stamped here, making rejection and approval
        // look identical in the database.
        user.setRejectedAt(LocalDateTime.now());
        user.setApprovedBy(admin);
        user.setRejectionReason(reason);
        userRepository.save(user);

        try {
            keycloakAdminService.setUserEnabled(userId, false);
            log.info("User disabled in Keycloak: userId={}", userId);
        } catch (Exception e) {
            log.warn("Failed to disable Keycloak account (non-critical): userId={}", userId, e);
        }

        emitApprovalAudit(AuditAction.USER_REJECTED, admin, user,
                "status=REJECTED,adminId=" + adminId + ",reason=" + reason);

        // Send rejection email
        emailService.sendRejectionEmail(user.getEmail(), user.getName(), reason);

        log.info("User rejected successfully: userId={}, adminId={}", userId, adminId);
    }

    @Override
    public Page<UserApprovalDto> getAllUsersWithApprovalStatus(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toUserApprovalDto);
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

    private UserApprovalDto toUserApprovalDto(User user) {
        UserApprovalDto dto = new UserApprovalDto();
        dto.setUserId(user.getId().toString());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setApprovalStatus(user.getApprovalStatus().name());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setRejectionReason(user.getRejectionReason());
        dto.setApprovedAt(user.getApprovedAt());
        dto.setRejectedAt(user.getRejectedAt()); // FIX #7 follow-up
        if (user.getApprovedBy() != null) {
            dto.setApprovedByName(user.getApprovedBy().getName());
        }
        try {
            dto.setRoles(keycloakAdminService.getUserRoles(user.getId()));
        } catch (Exception e) {
            log.warn("Failed to fetch Keycloak roles: userId={}", user.getId(), e);
            dto.setRoles(java.util.Collections.emptyList());
        }
        return dto;
    }
}