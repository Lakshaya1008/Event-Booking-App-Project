package com.event.tickets.exceptions;

/**
 * Exception thrown when an invite code is invalid for redemption.
 *
 * Reasons include:
 * - Code already redeemed
 * - Code expired
 * - Code revoked
 */
public class InvalidInviteCodeException extends RuntimeException {

  public InvalidInviteCodeException(String message) {
    super(message);
  }
}
