package com.event.tickets.domain.dtos;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for user role operations.
 *
 * Returns the current roles assigned to a user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRolesResponseDto {

  private String userId;
  private String userName;
  private String email;
  private List<String> roles;
}
