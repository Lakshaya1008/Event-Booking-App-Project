package com.event.tickets.domain.dtos;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private boolean requiresApproval;

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