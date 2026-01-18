package com.event.tickets.domain.dtos;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for invite code generation and retrieval.
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
  private String createdBy;
  private LocalDateTime createdAt;
  private LocalDateTime expiresAt;
  private String redeemedBy;
  private LocalDateTime redeemedAt;
}
