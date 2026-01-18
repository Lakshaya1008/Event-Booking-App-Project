package com.event.tickets.domain.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registration Response DTO
 *
 * Returned after successful registration.
 * Indicates whether user needs to wait for approval.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponseDto {

  /**
   * Success message.
   */
  private String message;

  /**
   * User's email address.
   */
  private String email;

  /**
   * Whether the user needs to wait for admin approval.
   * - true: User account is PENDING approval (403 on login attempts)
   * - false: User account is APPROVED (can login immediately)
   */
  private boolean requiresApproval;

  /**
   * The role assigned to the user.
   */
  private String assignedRole;

  /**
   * Additional instructions for the user.
   */
  private String instructions;
}
