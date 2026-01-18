package com.event.tickets.exceptions;

/**
 * Exception thrown when user creation in Keycloak fails.
 */
public class KeycloakUserCreationException extends RuntimeException {
  public KeycloakUserCreationException(String message) {
    super(message);
  }

  public KeycloakUserCreationException(String message, Throwable cause) {
    super(message, cause);
  }
}
