package com.event.tickets.services;

import java.util.UUID;

public interface KeycloakUserService {

  /**
   * Creates a new user in Keycloak.
   *
   * @param email User's email (also used as username)
   * @param password User's password (will be hashed by Keycloak)
   * @param name User's display name
   * @return The Keycloak user ID (UUID) of the created user
   * @throws com.event.tickets.exceptions.KeycloakUserCreationException if creation fails
   */
  UUID createUser(String email, String password, String name);

  /**
   * Assigns a realm-level role to a user in Keycloak.
   *
   * @param userId The Keycloak user ID
   * @param roleName The role name (ADMIN, ORGANIZER, ATTENDEE, STAFF)
   * @throws com.event.tickets.exceptions.KeycloakRoleAssignmentException if assignment fails
   */
  void assignRole(UUID userId, String roleName);

  void deleteUser(UUID userId);

  /**
   * Checks if a user exists in Keycloak.
   *
   * @param userId The Keycloak user ID
   * @return true if user exists, false otherwise
   */
  boolean userExists(UUID userId);

  /**
   * Enables or disables a user account in Keycloak.
   * Can be used to lock rejected accounts.
   *
   * @param userId The Keycloak user ID
   * @param enabled true to enable, false to disable
   * @throws com.event.tickets.exceptions.KeycloakUserUpdateException if update fails
   */
  void setUserEnabled(UUID userId, boolean enabled);
}
