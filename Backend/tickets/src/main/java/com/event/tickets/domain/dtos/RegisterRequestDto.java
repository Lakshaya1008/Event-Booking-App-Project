package com.event.tickets.domain.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registration Request DTO
 *
 * Used for invite-based user registration.
 * All fields are required and validated.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDto {

  /**
   * Invite code provided to the user.
   * Format: XXXX-XXXX-XXXX-XXXX (16 characters + 3 hyphens)
   */
  @NotBlank(message = "Invite code is required")
  @Pattern(
      regexp = "^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$",
      message = "Invalid invite code format. Expected: XXXX-XXXX-XXXX-XXXX"
  )
  private String inviteCode;

  /**
   * User's email address.
   * Will be used as both email and username in Keycloak.
   */
  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  @Size(max = 255, message = "Email must not exceed 255 characters")
  private String email;

  /**
   * User's password.
   * Must meet minimum security requirements.
   */
  @NotBlank(message = "Password is required")
  @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
  @Pattern(
      regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
      message = "Password must contain at least one uppercase letter, one lowercase letter, and one digit"
  )
  private String password;

  /**
   * User's display name.
   */
  @NotBlank(message = "Name is required")
  @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
  private String name;
}
