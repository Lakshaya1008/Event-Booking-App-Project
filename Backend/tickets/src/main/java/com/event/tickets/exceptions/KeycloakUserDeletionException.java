package com.event.tickets.exceptions;

/**
 * Exception thrown when user deletion in Keycloak fails.
 */
public class KeycloakUserDeletionException extends RuntimeException {
  public KeycloakUserDeletionException(String message) {
    super(message);
  }

  public KeycloakUserDeletionException(String message, Throwable cause) {
    super(message, cause);
  }
}
