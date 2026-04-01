package com.event.tickets.controllers;

import com.event.tickets.domain.dtos.RejectReasonDto;
import com.event.tickets.domain.dtos.UserApprovalDto;
import com.event.tickets.services.ApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

import static com.event.tickets.util.JwtUtil.parseUserId;

@RestController
@RequestMapping("/api/v1/admin/approvals")
@RequiredArgsConstructor
@Slf4j
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserApprovalDto>> getPendingApprovals(Pageable pageable) {
        log.info("Admin fetching pending approvals, page: {}", pageable.getPageNumber());
        Page<UserApprovalDto> pendingApprovals = approvalService.getPendingApprovals(pageable);
        return ResponseEntity.ok(pendingApprovals);
    }

    /**
     * Approves a user account.
     * Enables the user in Keycloak so they can log in with their assigned role.
     */
    @PostMapping("/{userId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> approveUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId) {

        UUID adminId = parseUserId(jwt);
        log.info("Admin approving user: adminId={}, userId={}", adminId, userId);

        approvalService.approveUser(userId, adminId);

        return ResponseEntity.ok(Map.of(
                "message", "User approved successfully",
                "userId", userId.toString(),
                "status", "APPROVED"
        ));
    }

    /**
     * Rejects a user account.
     * Keeps the Keycloak account disabled. User receives rejection email with reason.
     */
    @PostMapping("/{userId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> rejectUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @Valid @RequestBody RejectReasonDto rejectReasonDto) {

        UUID adminId = parseUserId(jwt);
        log.info("Admin rejecting user: adminId={}, userId={}, reason={}",
                adminId, userId, rejectReasonDto.getReason());

        approvalService.rejectUser(userId, adminId, rejectReasonDto.getReason());

        return ResponseEntity.ok(Map.of(
                "message", "User rejected successfully",
                "userId", userId.toString(),
                "status", "REJECTED",
                "reason", rejectReasonDto.getReason()
        ));
    }

    /**
     * Gets all users with their approval status and Keycloak roles.
     * Useful for the admin dashboard showing all registered users.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserApprovalDto>> getAllUsersWithApprovalStatus(Pageable pageable) {
        log.info("Admin fetching all users with approval status, page: {}", pageable.getPageNumber());
        Page<UserApprovalDto> allUsers = approvalService.getAllUsersWithApprovalStatus(pageable);
        return ResponseEntity.ok(allUsers);
    }
}