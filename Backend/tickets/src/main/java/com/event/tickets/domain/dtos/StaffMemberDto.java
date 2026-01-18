package com.event.tickets.domain.dtos;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for staff member information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffMemberDto {

  private UUID userId;
  private String userName;
  private String email;
}
