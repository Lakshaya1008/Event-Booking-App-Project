package com.event.tickets.services;

import java.util.List;
import java.util.UUID;

/**
 * Keycloak Admin Service
 *
 * Backend integration with Keycloak Admin API for role management.
 * This is the ONLY service that communicates with Keycloak Admin API.
 *
 * Principles:
 * - Backend is the sole authority for Keycloak Admin API calls
 * - No frontend Keycloak interactions
 * - No role persistence in database (roles managed by Keycloak)
 * - ADMIN role required for all operations
 *
 * Available Roles:
 * - ADMIN: Global administrative role (Keycloak-managed)
 * - ORGANIZER: Event organizers
 * - ATTENDEE: Event attendees
 * - STAFF: Event staff members
 */
public interface KeycloakAdminService {

  /**
   * Assigns a role to a user in Keycloak.
   *
   * @param userId The UUID of the user (Keycloak user ID)
   * @param roleName The name of the role to assign (ORGANIZER, ATTENDEE, STAFF, ADMIN)
   * @throws com.event.tickets.exceptions.UserNotFoundException if user doesn't exist
   * @throws com.event.tickets.exceptions.KeycloakOperationException if Keycloak operation fails
   */
  void assignRoleToUser(UUID userId, String roleName);

  /**
   * Revokes a role from a user in Keycloak.
   *
   * @param userId The UUID of the user (Keycloak user ID)
   * @param roleName The name of the role to revoke
   * @throws com.event.tickets.exceptions.UserNotFoundException if user doesn't exist
   * @throws com.event.tickets.exceptions.KeycloakOperationException if Keycloak operation fails
   */
  void revokeRoleFromUser(UUID userId, String roleName);

  /**
   * Gets all roles assigned to a user in Keycloak.
   *
   * @param userId The UUID of the user (Keycloak user ID)
   * @return List of role names assigned to the user
   * @throws com.event.tickets.exceptions.UserNotFoundException if user doesn't exist
   * @throws com.event.tickets.exceptions.KeycloakOperationException if Keycloak operation fails
   */
  List<String> getUserRoles(UUID userId);

  /**
   * Gets all available roles in the realm.
   *
   * @return List of all available role names
   * @throws com.event.tickets.exceptions.KeycloakOperationException if Keycloak operation fails
   */
  List<String> getAvailableRoles();

  /**
   * Checks if a user has a specific role.
   *
   * @param userId The UUID of the user (Keycloak user ID)
   * @param roleName The name of the role to check
   * @return true if user has the role, false otherwise
   */
  boolean userHasRole(UUID userId, String roleName);

  /**
   * Creates a new user in Keycloak.
   * Used for invite-based registration where backend creates users.
   *
   * @param email User's email (also used as username)
   * @param password User's password (will be hashed by Keycloak)
   * @param name User's display name
   * @return The Keycloak user ID (UUID) of the created user
   * @throws com.event.tickets.exceptions.KeycloakUserCreationException if creation fails
   */
  UUID createUser(String email, String password, String name);

  /**
   * Deletes a user from Keycloak.
   * Used for rollback when registration fails after user creation.
   *
   * @param userId The Keycloak user ID
   * @throws com.event.tickets.exceptions.KeycloakUserDeletionException if deletion fails
   */
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
