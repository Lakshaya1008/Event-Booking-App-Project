package com.event.tickets.domain.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reject Reason DTO
 *
 * FIXES APPLIED:
 *
 * FIX-RR1 — Minimum length reduced from 10 to 3 characters.
 *   BEFORE: @Size(min = 10) blocked short but perfectly valid reasons like
 *   "Spam", "Bot", "Fake" — admins were forced to pad reasons artificially.
 *   AFTER: min = 3 allows short meaningful reasons while still preventing
 *   empty single-character rejections.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectReasonDto {

    /**
     * Reason for rejecting the user account.
     * Required for transparency and audit purposes.
     * Short reasons like "Spam" or "Bot" are valid.
     */
    @NotBlank(message = "Rejection reason is required")
    @Size(min = 3, max = 500, message = "Rejection reason must be between 3 and 500 characters")
    private String reason;
}