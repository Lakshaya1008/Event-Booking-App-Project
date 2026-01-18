package com.event.tickets.domain.dtos;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for invite code redemption.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedeemInviteCodeResponseDto {

  private String message;
  private String roleAssigned;
  private String eventName;
  private List<String> currentRoles;
}
