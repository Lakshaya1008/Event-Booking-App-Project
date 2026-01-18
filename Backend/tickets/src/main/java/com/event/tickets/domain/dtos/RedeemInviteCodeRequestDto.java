package com.event.tickets.domain.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for redeeming an invite code.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedeemInviteCodeRequestDto {

  @NotBlank(message = "Invite code is required")
  private String code;
}
