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

/**
 * Approval Controller
 *
 * ADMIN-only endpoints for managing user account approvals.
 *
 * FIXES APPLIED:
 *
 * FIX-AC1 — Updated Javadoc to reflect the correct business rules.
 *   BEFORE: "Users register via invite code → status=PENDING"
 *   This was wrong and was actively misleading future developers into
 *   implementing the same universal-PENDING logic incorrectly.
 *   AFTER: Accurate description of the role-conditional approval workflow.
 *
 * Correct workflow:
 * 1. User registers WITHOUT invite code → ATTENDEE role → APPROVED immediately (no admin action needed)
 * 2. User registers WITH invite code  → ORGANIZER / STAFF / ADMIN role → PENDING approval
 * 3. Admin views pending approvals: GET /api/v1/admin/approvals/pending
 *    Response includes each user's Keycloak roles so admin knows what role they registered for.
 * 4. Admin approves: POST /api/v1/admin/approvals/{userId}/approve
 *    OR
 *    Admin rejects: POST /api/v1/admin/approvals/{userId}/reject
 * 5. On approval: Keycloak account enabled, user can log in with their assigned role.
 *    On rejection: Keycloak account stays disabled, user receives rejection email.
 *
 * Security:
 * - All endpoints require ADMIN role
 * - ApprovalGateFilter enforces access based on DB approval_status per request
 */
@RestController
@RequestMapping("/api/v1/admin/approvals")
@RequiredArgsConstructor
@Slf4j
public class ApprovalController {

    private final ApprovalService approvalService;

    /**
     * Gets all users with PENDING approval status.
     * Response includes each user's Keycloak roles (e.g. ORGANIZER, STAFF)
     * so the admin knows what they registered for before approving.
     */
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