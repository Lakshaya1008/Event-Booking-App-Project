package com.event.tickets.domain.dtos;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for available roles.
 *
 * Returns all roles that can be assigned in the system.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailableRolesResponseDto {

  private List<String> roles;
  private String message;
}
