package com.event.tickets.domain.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for assigning a role to a user.
 *
 * ADMIN-only operation via Keycloak Admin API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignRoleRequestDto {

  @NotBlank(message = "Role name is required")
  @Pattern(
      regexp = "^(ADMIN|ORGANIZER|ATTENDEE|STAFF)$",
      message = "Role must be one of: ADMIN, ORGANIZER, ATTENDEE, STAFF"
  )
  private String roleName;
}
