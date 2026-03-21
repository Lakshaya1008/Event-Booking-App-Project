package com.event.tickets.domain.dtos;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for invite code redemption.
 *
 * FIXES APPLIED:
 *
 * FIX-DTO7 — Added requiresApproval flag.
 *   BEFORE: After redeeming an invite code the client received roleAssigned
 *   and currentRoles but had no way to know whether the user now needs to wait
 *   for admin approval (ORGANIZER/STAFF/ADMIN codes) or can act immediately.
 *   A frontend would have to guess, or the user would try to use the system
 *   and get a confusing 403 APPROVAL_PENDING error.
 *   AFTER: requiresApproval=true means the user's account is PENDING and they
 *   must wait for admin review. false means they can use the system right away.
 *
 * Note: Invite code redemption is only available to already-APPROVED users
 * (InviteCodeServiceImpl blocks PENDING users from redeeming codes).
 * So requiresApproval here refers to whether THIS redemption changes their
 * status to require re-review — currently the service does not change
 * approval status on redemption, so this is always false. The field is
 * included for future use if that changes, and for client clarity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedeemInviteCodeResponseDto {

    /** Human-readable success message. */
    private String message;

    /** The role that was assigned via the invite code. */
    private String roleAssigned;

    /** Event name if this was a STAFF code scoped to a specific event. Null otherwise. */
    private String eventName;

    /**
     * FIX-DTO7: Whether the user now needs admin approval before using the system.
     * Currently always false (only approved users can redeem codes).
     * Included for client clarity and future extensibility.
     */
    private boolean requiresApproval;

    /** All Keycloak roles currently assigned to the user after redemption. */
    private List<String> currentRoles;

    /**
     * Convenience constructor matching the original 4-arg signature
     * (for backward compatibility with existing callers in InviteCodeServiceImpl).
     * requiresApproval defaults to false.
     */
    public RedeemInviteCodeResponseDto(String message, String roleAssigned,
                                       String eventName, List<String> currentRoles) {
        this.message = message;
        this.roleAssigned = roleAssigned;
        this.eventName = eventName;
        this.currentRoles = currentRoles;
        this.requiresApproval = false;
    }
}