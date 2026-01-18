package com.event.tickets.domain.entities;

/**
 * Invite Code Status Enum
 *
 * Represents the lifecycle state of an invite code.
 */
public enum InviteCodeStatus {
  /**
   * Code is active and can be redeemed.
   */
  PENDING,

  /**
   * Code has been successfully redeemed.
   */
  REDEEMED,

  /**
   * Code has expired (past expiration time).
   */
  EXPIRED,

  /**
   * Code was manually revoked before use.
   */
  REVOKED
}
