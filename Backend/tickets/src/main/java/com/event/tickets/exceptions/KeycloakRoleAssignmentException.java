package com.event.tickets.exceptions;

/**
 * Exception thrown when role assignment in Keycloak fails.
 */
public class KeycloakRoleAssignmentException extends RuntimeException {
  public KeycloakRoleAssignmentException(String message) {
    super(message);
  }

  public KeycloakRoleAssignmentException(String message, Throwable cause) {
    super(message, cause);
  }
}
