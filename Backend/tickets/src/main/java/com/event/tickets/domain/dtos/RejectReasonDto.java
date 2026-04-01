package com.event.tickets.domain.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectReasonDto {

    /**
     * Reason for rejecting the user account.
     * Required for transparency and audit purposes.
     * Short reasons like "Spam" or "Bot" are valid.
     */
    @NotBlank(message = "Rejection reason is required")
    @Size(min = 3, max = 500, message = "Rejection reason must be between 3 and 500 characters")
    private String reason;
}