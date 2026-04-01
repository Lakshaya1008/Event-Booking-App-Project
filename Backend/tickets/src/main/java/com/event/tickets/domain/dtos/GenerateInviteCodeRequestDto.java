package com.event.tickets.domain.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateInviteCodeRequestDto {

    @NotBlank(message = "Role name is required")
    @Pattern(
            regexp = "^(ADMIN|ORGANIZER|ATTENDEE|STAFF)$",
            message = "Role must be one of: ADMIN, ORGANIZER, ATTENDEE, STAFF"
    )
    private String roleName;

    private UUID eventId; // Required only for STAFF role

    @NotNull(message = "Expiration hours is required")
    @Positive(message = "Expiration hours must be positive")
    @Max(value = 8760, message = "Expiration hours cannot exceed 8760 (1 year). Regenerate the code if you need longer access.")
    private Integer expirationHours;
}