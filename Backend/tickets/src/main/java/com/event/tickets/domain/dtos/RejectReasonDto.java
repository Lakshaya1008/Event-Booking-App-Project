package com.event.tickets.domain.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reject Reason DTO
 *
 * Used when admin rejects a user account.
 * Reason is required to provide transparency.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectReasonDto {

  /**
   * Reason for rejecting the user account.
   * Must be provided for transparency and audit purposes.
   */
  @NotBlank(message = "Rejection reason is required")
  @Size(min = 10, max = 500, message = "Rejection reason must be between 10 and 500 characters")
  private String reason;
}
