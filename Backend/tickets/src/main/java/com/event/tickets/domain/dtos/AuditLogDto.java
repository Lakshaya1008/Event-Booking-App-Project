package com.event.tickets.domain.dtos;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Audit Log Response DTO
 *
 * Immutable view of audit log entry.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDto {

  private UUID id;
  private String action;
  private String actorName;
  private UUID actorId;
  private String targetUserName;
  private UUID targetUserId;
  private String eventName;
  private UUID eventId;
  private String resourceType;
  private UUID resourceId;
  private String details;
  private String ipAddress;
  private LocalDateTime createdAt;
}
