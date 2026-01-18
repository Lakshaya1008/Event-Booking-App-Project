package com.event.tickets.services;

import java.util.UUID;

/**
 * Keycloak User Service
 *
 * Handles user creation and management in Keycloak via Admin REST API.
 * Used for invite-based registration where backend creates users in Keycloak.
 *
 * Security:
 * - Backend acts as Keycloak admin client
 * - Uses service account credentials (NOT user credentials)
 * - Backend is the SOLE authority for user creation
 * - Frontend NEVER accesses Keycloak Admin API
 *
 * Flow:
 * 1. User submits registration with invite code
 * 2. Backend validates invite code
 * 3. Backend creates user in Keycloak via this service
 * 4. Backend assigns role from invite code
 * 5. User logs in via standard Keycloak OAuth2 flow
 *
 * Note: This service does NOT issue JWTs or handle authentication.
 * Users must authenticate with Keycloak after creation.
 */
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
