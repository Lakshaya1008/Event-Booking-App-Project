package com.event.tickets.domain.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for generating an invite code.
 *
 * Used by ADMIN for global roles or ORGANIZER for event-staff invites.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateInviteCodeRequestDto {

  @NotBlank(message = "Role name is required")
  @Pattern(
      regexp = "^(ORGANIZER|ATTENDEE|STAFF)$",
      message = "Role must be one of: ORGANIZER, ATTENDEE, STAFF"
  )
  private String roleName;

  private UUID eventId; // Required only for STAFF role

  @NotNull(message = "Expiration hours is required")
  @Positive(message = "Expiration hours must be positive")
  private Integer expirationHours;
}
