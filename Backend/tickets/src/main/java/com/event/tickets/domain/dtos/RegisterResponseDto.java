package com.event.tickets.domain.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registration Response DTO
 *
 * FIXES APPLIED:
 *
 * FIX-DTO1 — assignedRole only populated when requiresApproval=false (ATTENDEE).
 *   BEFORE: assignedRole was always set in the 201 response — exposing ORGANIZER,
 *   STAFF, ADMIN to the user before admin has reviewed their account.
 *   AFTER: For PENDING users (requiresApproval=true), assignedRole is null.
 *   The user learns their role from the approval email once admin acts.
 *   For ATTENDEE (requiresApproval=false), assignedRole="ATTENDEE" is safe to expose.
 *
 * FIX-DTO2 — Javadoc corrected.
 *   BEFORE: "true: User account is PENDING approval (403 on login attempts)"
 *   This was wrong — ATTENDEE users are now APPROVED immediately with no login block.
 *   AFTER: Accurate description of what requiresApproval=true/false means.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponseDto {

    /** Success message describing what the user should do next. */
    private String message;

    /** The normalized (lowercase) email the account was created with. */
    private String email;

    /**
     * Whether the user must wait for admin approval before logging in.
     *
     * true  → ORGANIZER / STAFF / ADMIN — account is PENDING, login blocked until admin approves.
     * false → ATTENDEE — account is APPROVED immediately, user can log in right now.
     */
    private boolean requiresApproval;

    /**
     * The role assigned to the user.
     *
     * Only populated when requiresApproval=false (ATTENDEE registrations).
     * Null for PENDING users — role is revealed in the approval email to avoid
     * information disclosure before admin has reviewed the account.
     */
    private String assignedRole;

    /** Human-readable instructions for what the user should do next. */
    private String instructions;
}