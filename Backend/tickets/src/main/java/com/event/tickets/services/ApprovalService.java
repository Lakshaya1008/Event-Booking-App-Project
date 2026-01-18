package com.event.tickets.services;

import com.event.tickets.domain.dtos.UserApprovalDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Approval Service
 *
 * Manages user account approval workflow.
 * Handles PENDING → APPROVED or PENDING → REJECTED transitions.
 *
 * Security:
 * - Only ADMIN role can approve/reject users
 * - Approval status is stored in backend database
 * - Keycloak roles remain unchanged (assigned during registration)
 * - ApprovalGateFilter enforces access based on approval status
 *
 * Workflow:
 * 1. User registers via invite code → status=PENDING
 * 2. Admin reviews pending users
 * 3. Admin approves → status=APPROVED (user can now access system)
 * 4. Admin rejects → status=REJECTED (user permanently blocked)
 */
public interface ApprovalService {

  /**
   * Gets all users with PENDING approval status.
   *
   * @param pageable Pagination parameters
   * @return Page of users pending approval
   */
  Page<UserApprovalDto> getPendingApprovals(Pageable pageable);

  /**
   * Approves a user account.
   * Sets approval status to APPROVED and records admin who approved.
   *
   * @param userId The user ID to approve
   * @param adminId The admin user ID performing the approval
   * @throws com.event.tickets.exceptions.UserNotFoundException if user doesn't exist
   * @throws com.event.tickets.exceptions.InvalidApprovalStateException if user is not in PENDING state
   */
  void approveUser(UUID userId, UUID adminId);

  /**
   * Rejects a user account.
   * Sets approval status to REJECTED and records reason.
   * Optionally disables user in Keycloak.
   *
   * @param userId The user ID to reject
   * @param adminId The admin user ID performing the rejection
   * @param reason The reason for rejection (for transparency)
   * @throws com.event.tickets.exceptions.UserNotFoundException if user doesn't exist
   * @throws com.event.tickets.exceptions.InvalidApprovalStateException if user is not in PENDING state
   */
  void rejectUser(UUID userId, UUID adminId, String reason);

  /**
   * Gets all users (PENDING, APPROVED, REJECTED) for admin view.
   *
   * @param pageable Pagination parameters
   * @return Page of all users with approval status
   */
  Page<UserApprovalDto> getAllUsersWithApprovalStatus(Pageable pageable);
}
