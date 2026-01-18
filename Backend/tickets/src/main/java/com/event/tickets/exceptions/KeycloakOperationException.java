package com.event.tickets.exceptions;

/**
 * Exception thrown when Keycloak Admin API operations fail.
 *
 * This exception wraps underlying Keycloak API errors and provides
 * meaningful error messages for administrative operations.
 */
public class KeycloakOperationException extends RuntimeException {

  public KeycloakOperationException(String message) {
    super(message);
  }

  public KeycloakOperationException(String message, Throwable cause) {
    super(message, cause);
  }
}
