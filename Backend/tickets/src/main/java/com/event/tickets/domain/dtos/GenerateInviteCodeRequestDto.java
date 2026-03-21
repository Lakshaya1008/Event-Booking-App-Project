package com.event.tickets.domain.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FIXES APPLIED:
 *
 * FIX I-5 — @Max(8760) added to expirationHours (max 1 year = 8760 hours).
 *
 *   BEFORE: @Positive only — callers could send expirationHours = 10_000_000
 *   (~1,141 years). The code counted against the PENDING rate limits forever,
 *   gradually consuming the 100-per-event / 500-per-organizer budget with
 *   codes that would never naturally expire within any operational lifetime.
 *
 *   AFTER: Maximum 8760 hours (365 days). This is already generous — typical
 *   invite codes are valid for 24–72 hours. Organizers needing longer validity
 *   can regenerate. The error message is descriptive.
 *
 * H-10 FIX (preserved): @Pattern includes ADMIN so admin-role invite codes
 * can be created via the API.
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
    @Max(value = 8760, message = "Expiration hours cannot exceed 8760 (1 year). Regenerate the code if you need longer access.")
    private Integer expirationHours;
}