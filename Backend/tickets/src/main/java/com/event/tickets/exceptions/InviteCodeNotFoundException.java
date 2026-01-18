package com.event.tickets.exceptions;

/**
 * Exception thrown when an invite code is not found.
 */
public class InviteCodeNotFoundException extends RuntimeException {

  public InviteCodeNotFoundException(String message) {
    super(message);
  }
}
