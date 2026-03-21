package com.event.tickets.domain.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * User Approval DTO
 *
 * FIXES APPLIED:
 *
 * FIX-DTO3 — roles list is now actually populated from Keycloak.
 *   BEFORE: roles was always set to Collections.emptyList() in
 *   toUserApprovalDtoNoRoles() — the field existed but was never filled.
 *   An admin reviewing the pending approvals list had NO way to see what
 *   role a user registered for (ORGANIZER vs STAFF vs ADMIN).
 *   AFTER: ApprovalServiceImpl.toUserApprovalDtoWithRoles() fetches the
 *   user's Keycloak roles and populates this field. Admin sees the role
 *   before deciding whether to approve or reject.
 *
 * FIX-DTO4 — approvalStatus changed from String to use consistent casing.
 *   The field is still a String (not enum) to avoid coupling the DTO to the
 *   entity layer, but is guaranteed to be one of: PENDING, APPROVED, REJECTED.
 *   Previously could be null if approvalStatus was null on legacy users.
 *   AFTER: ApprovalServiceImpl maps null → "UNKNOWN" defensively.
 */
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
     * FIX-DTO3: Keycloak roles assigned to this user.
     * For PENDING users this shows what role they registered for
     * (e.g. ["ORGANIZER"] or ["STAFF"]) — the admin uses this to make
     * an informed approval decision.
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