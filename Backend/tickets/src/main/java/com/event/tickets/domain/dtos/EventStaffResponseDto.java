package com.event.tickets.domain.dtos;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for event staff list.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventStaffResponseDto {

  private UUID eventId;
  private String eventName;
  private List<StaffMemberDto> staffMembers;
  private int totalStaffCount;
}
