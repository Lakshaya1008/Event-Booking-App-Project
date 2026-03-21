package com.event.tickets.domain.dtos;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for invite code generation and retrieval.
 *
 * FIXES APPLIED:
 *
 * FIX-DTO5 — Added revokedAt and revokedReason fields.
 *   BEFORE: The InviteCode entity has revokedAt and revokedReason columns
 *   but this DTO had no corresponding fields. When an admin viewed a REVOKED
 *   code the API response showed status=REVOKED but gave no information about
 *   when it was revoked or why — making the revocation audit trail invisible
 *   in the API layer.
 *   AFTER: Both fields included. mapToResponseDto() in InviteCodeServiceImpl
 *   must be updated to populate them (see InviteCodeServiceImpl fix).
 *
 * FIX-DTO6 — Added createdByUserId alongside createdBy name.
 *   BEFORE: createdBy was a display name string only. If two users share a
 *   name, the admin cannot distinguish who created the code.
 *   AFTER: createdByUserId (UUID) added so the API consumer can link back
 *   to the specific user record. Name kept for display convenience.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InviteCodeResponseDto {

    private UUID id;

    /** The actual invite code string (XXXX-XXXX-XXXX-XXXX format). */
    private String code;

    /** Role that will be assigned when this code is redeemed. */
    private String roleName;

    /** Event this code is scoped to (null for global / non-STAFF codes). */
    private UUID eventId;
    private String eventName;

    /** Current status: PENDING, REDEEMED, EXPIRED, REVOKED. */
    private String status;

    /** FIX-DTO6: UUID of the user who created this code. */
    private UUID createdByUserId;

    /** Display name of the user who created this code. */
    private String createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    /** Display name of the user who redeemed this code. Null if not redeemed. */
    private String redeemedBy;
    private LocalDateTime redeemedAt;

    /** FIX-DTO5: When the code was revoked. Null if not revoked. */
    private LocalDateTime revokedAt;

    /** FIX-DTO5: Reason provided when the code was revoked. Null if not revoked. */
    private String revokedReason;
}