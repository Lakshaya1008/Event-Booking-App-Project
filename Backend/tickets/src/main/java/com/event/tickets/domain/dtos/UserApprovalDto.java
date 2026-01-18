package com.event.tickets.domain.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * User Approval DTO
 *
 * Represents a user pending admin approval.
 * Used in admin approval list endpoints.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserApprovalDto {

  /**
   * User's ID (Keycloak user ID).
   */
  private String userId;

  /**
   * User's display name.
   */
  private String name;

  /**
   * User's email address.
   */
  private String email;

  /**
   * Current approval status (PENDING, APPROVED, REJECTED).
   */
  private String approvalStatus;

  /**
   * Roles assigned to the user in Keycloak.
   */
  private java.util.List<String> roles;

  /**
   * When the user account was created.
   */
  private LocalDateTime createdAt;

  /**
   * Rejection reason (if rejected).
   */
  private String rejectionReason;

  /**
   * When the account was approved/rejected.
   */
  private LocalDateTime approvedAt;

  /**
   * Admin who approved/rejected the account.
   */
  private String approvedByName;
}
