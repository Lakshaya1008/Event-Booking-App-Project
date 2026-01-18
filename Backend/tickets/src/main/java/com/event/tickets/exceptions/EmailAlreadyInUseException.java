package com.event.tickets.exceptions;

/**
 * Exception thrown when email is already in use during registration.
 */
public class EmailAlreadyInUseException extends RuntimeException {
  public EmailAlreadyInUseException(String message) {
    super(message);
  }

  public EmailAlreadyInUseException(String message, Throwable cause) {
    super(message, cause);
  }
}
