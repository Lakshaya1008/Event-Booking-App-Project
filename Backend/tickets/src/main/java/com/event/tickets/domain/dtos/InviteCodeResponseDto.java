package com.event.tickets.domain.dtos;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private UUID createdByUserId;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    private String redeemedBy;
    private LocalDateTime redeemedAt;

    private LocalDateTime revokedAt;

    private String revokedReason;
}