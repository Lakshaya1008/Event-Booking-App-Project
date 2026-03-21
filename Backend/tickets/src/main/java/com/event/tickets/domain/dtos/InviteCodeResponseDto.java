package com.event.tickets.domain.dtos;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FIXES APPLIED:
 *
 * FIX I-3 — revokedAt and revokedReason added.
 *
 *   BEFORE: A caller listing invite codes could see a REVOKED status but had
 *   no way to know when it was revoked or why. The InviteCode entity stores
 *   both fields but mapToResponseDto() silently dropped them.
 *
 *   AFTER: revokedAt and revokedReason are now returned in all invite code
 *   responses. They are null for non-REVOKED codes. mapToResponseDto() in
 *   InviteCodeServiceImpl is updated to populate them.
 *
 * FIX I-7 — createdByUserId UUID added alongside createdBy name.
 *
 *   BEFORE: createdBy was only a display name string. If a user's name
 *   changed, or two users shared the same name, the response was ambiguous.
 *   An admin querying invite code history had no stable identifier for the creator.
 *
 *   AFTER: createdByUserId (UUID) is returned in addition to createdBy (name).
 *   The name is kept for display convenience. The UUID is the stable reference.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InviteCodeResponseDto {

    private UUID id;
    private String code;
    private String roleName;
    private UUID eventId;
    private String eventName;
    private String status;

    /** Display name of the user who generated this code. */
    private String createdBy;

    /**
     * FIX I-7: UUID of the user who generated this code.
     * Stable identifier — does not change if the user's display name changes.
     */
    private UUID createdByUserId;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    private String redeemedBy;
    private LocalDateTime redeemedAt;

    /**
     * FIX I-3: When was this code revoked? Null for non-REVOKED codes.
     */
    private LocalDateTime revokedAt;

    /**
     * FIX I-3: Why was this code revoked? Null for non-REVOKED codes.
     */
    private String revokedReason;
}