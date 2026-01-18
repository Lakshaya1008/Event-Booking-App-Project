package com.event.tickets.domain.dtos;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for assigning staff to an event.
 *
 * Organizer-only operation for event-scoped staff management.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignStaffRequestDto {

  @NotNull(message = "User ID is required")
  private UUID userId;
}
