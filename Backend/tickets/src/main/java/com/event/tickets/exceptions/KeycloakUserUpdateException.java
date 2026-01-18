package com.event.tickets.exceptions;

/**
 * Exception thrown when user update in Keycloak fails.
 */
public class KeycloakUserUpdateException extends RuntimeException {
  public KeycloakUserUpdateException(String message) {
    super(message);
  }

  public KeycloakUserUpdateException(String message, Throwable cause) {
    super(message, cause);
  }
}
