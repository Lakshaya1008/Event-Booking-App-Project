package com.event.tickets.domain.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserApprovalDto {

    /** Keycloak user ID (UUID as string). */
    private String userId;

    /** User's display name. */
    private String name;

    /** User's email address (normalized lowercase). */
    private String email;

    /**
     * Current approval status: PENDING, APPROVED, REJECTED, or UNKNOWN (legacy null).
     */
    private String approvalStatus;

    /**
     * Keycloak roles assigned to this user.
     * For PENDING users this shows what role they registered for.
     * Empty list if Keycloak is temporarily unavailable.
     */
    private List<String> roles;

    /** When the user account was created (registration timestamp). */
    private LocalDateTime createdAt;

    /** Reason provided when the account was rejected. Null if not rejected. */
    private String rejectionReason;

    /** Timestamp when the account was approved. Only set for APPROVED status. */
    private LocalDateTime approvedAt;

    /** Timestamp when the account was rejected. Only set for REJECTED status. */
    private LocalDateTime rejectedAt;

    /** Name of the admin who reviewed (approved or rejected) this account. */
    private String approvedByName;
}