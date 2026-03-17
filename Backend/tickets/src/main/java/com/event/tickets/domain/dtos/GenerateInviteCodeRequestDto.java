package com.event.tickets.domain.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * H-10 FIX: @Pattern now includes ADMIN.
 *
 * Previously: regexp = "^(ORGANIZER|ATTENDEE|STAFF)$"
 * Any POST to /api/v1/invites with roleName:"ADMIN" returned HTTP 400 with
 * "Role must be one of: ORGANIZER, ATTENDEE, STAFF" — even for ADMIN users.
 * ADMINs could never create ADMIN-role invite codes via the API.
 *
 * The authorization check (only ADMINs can create ADMIN invites) is enforced
 * in InviteCodeController.validateInviteCreation() — the DTO validation only
 * needs to reject completely invalid role names.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateInviteCodeRequestDto {

    @NotBlank(message = "Role name is required")
    @Pattern(
            regexp = "^(ADMIN|ORGANIZER|ATTENDEE|STAFF)$",
            message = "Role must be one of: ADMIN, ORGANIZER, ATTENDEE, STAFF"
    )
    private String roleName;

    private UUID eventId; // Required only for STAFF role

    @NotNull(message = "Expiration hours is required")
    @Positive(message = "Expiration hours must be positive")
    private Integer expirationHours;
}