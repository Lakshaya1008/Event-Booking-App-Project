package com.event.tickets.services.impl;

import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.KeycloakOperationException;
import com.event.tickets.exceptions.KeycloakUserCreationException;
import com.event.tickets.exceptions.KeycloakUserDeletionException;
import com.event.tickets.exceptions.KeycloakUserUpdateException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.AuditLogRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.KeycloakAdminService;
import com.event.tickets.services.SystemUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;

/**
 * Keycloak Admin Service Implementation
 *
 * Backend integration with Keycloak Admin API for role management.
 * This service is the SOLE authority for invoking Keycloak Admin operations.
 *
 * Security Model:
 * - Backend authenticates with Keycloak using admin credentials
 * - No frontend access to Keycloak Admin API
 * - All operations require ADMIN role (enforced at controller level)
 * - Roles managed exclusively by Keycloak (no DB persistence)
 *
 * Error Handling:
 * - Wraps Keycloak API exceptions in application-specific exceptions
 * - Provides detailed error messages for troubleshooting
 * - Logs all administrative operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakAdminServiceImpl implements KeycloakAdminService {

  private final Keycloak keycloakAdminClient;
  private final UserRepository userRepository;
  private final AuditLogRepository auditLogRepository;

  @Value("${keycloak.admin.realm}")
  private String realm;

  @Override
  @Transactional
  public void assignRoleToUser(UUID userId, String roleName) {
    log.info("Assigning role '{}' to user '{}'", roleName, userId);

    try {

      // Note: DB existence check removed to allow role assignment during registration
      // before DB user is persisted. Keycloak API will fail if user does not exist in Keycloak.

      RealmResource realmResource = keycloakAdminClient.realm(realm);
      UserResource userResource = realmResource.users().get(userId.toString());

      // Get role representation
      RoleResource roleResource = realmResource.roles().get(roleName);
      RoleRepresentation role = roleResource.toRepresentation();

      // Assign role to user
      userResource.roles().realmLevel().add(Collections.singletonList(role));

      log.info("Successfully assigned role '{}' to user '{}'", roleName, userId);

      // Audit log - get current user (ADMIN who performed action)
      User actor = getCurrentUser();
      HttpServletRequest request = getCurrentRequest();
      String ipAddress = extractClientIp(request);

      // For audit, try to find target user in DB; may be null during registration
      User targetUser = userRepository.findById(userId).orElse(null);

      AuditLog auditLog = AuditLog.builder()
          .action(AuditAction.ROLE_ASSIGNED)
          .actor(actor)
          .targetUser(targetUser)
          .resourceType("Role")
          .details(String.format("Assigned role: %s", roleName))
          .ipAddress(ipAddress)
          .userAgent(extractUserAgent(request))
          .build();

      auditLogRepository.save(auditLog);

    } catch (NotFoundException e) {
      log.error("Keycloak resource not found: userId={}, roleName={}", userId, roleName, e);
      throw new KeycloakOperationException(
          String.format("User or role not found in Keycloak: user=%s, role=%s", userId, roleName),
          e
      );
    } catch (Exception e) {
      log.error("Failed to assign role '{}' to user '{}'", roleName, userId, e);
      throw new KeycloakOperationException(
          String.format("Failed to assign role '%s' to user '%s': %s", roleName, userId, e.getMessage()),
          e
      );
    }
  }

  @Override
  @Transactional
  public void revokeRoleFromUser(UUID userId, String roleName) {
    log.info("Revoking role '{}' from user '{}'", roleName, userId);

    try {
      // Verify user exists in our database
      if (!userRepository.existsById(userId)) {
        throw new UserNotFoundException(
            String.format("User with ID '%s' not found", userId)
        );
      }

      User targetUser = userRepository.findById(userId)
          .orElseThrow(() -> new UserNotFoundException("User not found"));

      RealmResource realmResource = keycloakAdminClient.realm(realm);
      UserResource userResource = realmResource.users().get(userId.toString());

      // Get role representation
      RoleResource roleResource = realmResource.roles().get(roleName);
      RoleRepresentation role = roleResource.toRepresentation();

      // Remove role from user
      userResource.roles().realmLevel().remove(Collections.singletonList(role));

      log.info("Successfully revoked role '{}' from user '{}'", roleName, userId);

      // Audit log
      User actor = getCurrentUser();
      HttpServletRequest request = getCurrentRequest();
      String ipAddress = extractClientIp(request);

      AuditLog auditLog = AuditLog.builder()
          .action(AuditAction.ROLE_REVOKED)
          .actor(actor)
          .targetUser(targetUser)
          .resourceType("Role")
          .details(String.format("Revoked role: %s", roleName))
          .ipAddress(ipAddress)
          .userAgent(extractUserAgent(request))
          .build();

      auditLogRepository.save(auditLog);

    } catch (NotFoundException e) {
      log.error("Keycloak resource not found: userId={}, roleName={}", userId, roleName, e);
      throw new KeycloakOperationException(
          String.format("User or role not found in Keycloak: user=%s, role=%s", userId, roleName),
          e
      );
    } catch (Exception e) {
      log.error("Failed to revoke role '{}' from user '{}'", roleName, userId, e);
      throw new KeycloakOperationException(
          String.format("Failed to revoke role '%s' from user '%s': %s", roleName, userId, e.getMessage()),
          e
      );
    }
  }

  @Override
  public List<String> getUserRoles(UUID userId) {
    log.debug("Fetching roles for user '{}'", userId);

    try {
      // Verify user exists in our database
      if (!userRepository.existsById(userId)) {
        throw new UserNotFoundException(
            String.format("User with ID '%s' not found", userId)
        );
      }

      RealmResource realmResource = keycloakAdminClient.realm(realm);
      UserResource userResource = realmResource.users().get(userId.toString());

      // Get realm-level roles assigned to user
      List<RoleRepresentation> roles = userResource.roles().realmLevel().listEffective();

      List<String> roleNames = roles.stream()
          .map(RoleRepresentation::getName)
          .filter(this::isApplicationRole) // Filter out Keycloak default roles
          .collect(Collectors.toList());

      log.debug("User '{}' has roles: {}", userId, roleNames);
      return roleNames;

    } catch (NotFoundException e) {
      log.error("User not found in Keycloak: userId={}", userId, e);
      throw new KeycloakOperationException(
          String.format("User not found in Keycloak: %s", userId),
          e
      );
    } catch (Exception e) {
      log.error("Failed to fetch roles for user '{}'", userId, e);
      throw new KeycloakOperationException(
          String.format("Failed to fetch roles for user '%s': %s", userId, e.getMessage()),
          e
      );
    }
  }

  @Override
  public List<String> getAvailableRoles() {
    log.debug("Fetching available roles");

    try {
      RealmResource realmResource = keycloakAdminClient.realm(realm);

      List<RoleRepresentation> roles = realmResource.roles().list();

      List<String> roleNames = roles.stream()
          .map(RoleRepresentation::getName)
          .filter(this::isApplicationRole) // Filter out Keycloak default roles
          .collect(Collectors.toList());

      log.debug("Available roles: {}", roleNames);
      return roleNames;

    } catch (Exception e) {
      log.error("Failed to fetch available roles", e);
      throw new KeycloakOperationException(
          String.format("Failed to fetch available roles: %s", e.getMessage()),
          e
      );
    }
  }

  @Override
  public boolean userHasRole(UUID userId, String roleName) {
    try {
      List<String> userRoles = getUserRoles(userId);
      return userRoles.contains(roleName);
    } catch (Exception e) {
      log.warn("Failed to check if user '{}' has role '{}'", userId, roleName, e);
      return false;
    }
  }

  @Override
  public UUID createUser(String email, String password, String name) {
    log.info("Creating new user in Keycloak: email={}, name={}", email, name);

    try {
      RealmResource realmResource = keycloakAdminClient.realm(realm);

      // Create user representation
      org.keycloak.representations.idm.UserRepresentation userRep =
          new org.keycloak.representations.idm.UserRepresentation();
      userRep.setUsername(email);
      userRep.setEmail(email);
      userRep.setFirstName(name);
      userRep.setEnabled(true);
      userRep.setEmailVerified(false); // Can be set to true if email verification not required

      // Create user in Keycloak
      jakarta.ws.rs.core.Response response = realmResource.users().create(userRep);

      if (response.getStatus() != 201) {
        String errorMsg = String.format(
            "Failed to create user in Keycloak. Status: %d, Reason: %s",
            response.getStatus(),
            response.getStatusInfo().getReasonPhrase()
        );
        log.error(errorMsg);
        throw new com.event.tickets.exceptions.KeycloakUserCreationException(errorMsg);
      }

      // Extract user ID from Location header
      String locationHeader = response.getHeaderString("Location");
      if (locationHeader == null) {
        throw new com.event.tickets.exceptions.KeycloakUserCreationException(
            "Failed to extract user ID from Keycloak response"
        );
      }

      String userId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);
      UUID keycloakUserId = UUID.fromString(userId);

      // Set password
      org.keycloak.representations.idm.CredentialRepresentation credential =
          new org.keycloak.representations.idm.CredentialRepresentation();
      credential.setType(org.keycloak.representations.idm.CredentialRepresentation.PASSWORD);
      credential.setValue(password);
      credential.setTemporary(false);

      UserResource userResource = realmResource.users().get(keycloakUserId.toString());
      userResource.resetPassword(credential);

      log.info("Successfully created user in Keycloak: userId={}, email={}", keycloakUserId, email);
      return keycloakUserId;

    } catch (Exception e) {
      log.error("Failed to create user in Keycloak: email={}", email, e);
      throw new com.event.tickets.exceptions.KeycloakUserCreationException(
          String.format("Failed to create user in Keycloak: %s", e.getMessage()),
          e
      );
    }
  }

  @Override
  public void deleteUser(UUID userId) {
    log.info("Deleting user from Keycloak: userId={}", userId);

    try {
      RealmResource realmResource = keycloakAdminClient.realm(realm);
      UserResource userResource = realmResource.users().get(userId.toString());
      userResource.remove();

      log.info("Successfully deleted user from Keycloak: userId={}", userId);

    } catch (NotFoundException e) {
      log.warn("User not found in Keycloak during deletion: userId={}", userId);
      // Don't throw exception if user doesn't exist (already deleted or never existed)
    } catch (Exception e) {
      log.error("Failed to delete user from Keycloak: userId={}", userId, e);
      throw new com.event.tickets.exceptions.KeycloakUserDeletionException(
          String.format("Failed to delete user from Keycloak: %s", e.getMessage()),
          e
      );
    }
  }

  @Override
  public boolean userExists(UUID userId) {
    try {
      RealmResource realmResource = keycloakAdminClient.realm(realm);
      UserResource userResource = realmResource.users().get(userId.toString());
      userResource.toRepresentation(); // Will throw NotFoundException if user doesn't exist
      return true;
    } catch (NotFoundException e) {
      return false;
    } catch (Exception e) {
      log.error("Error checking if user exists: userId={}", userId, e);
      return false;
    }
  }

  @Override
  public void setUserEnabled(UUID userId, boolean enabled) {
    log.info("Setting user enabled status in Keycloak: userId={}, enabled={}", userId, enabled);

    try {
      RealmResource realmResource = keycloakAdminClient.realm(realm);
      UserResource userResource = realmResource.users().get(userId.toString());

      org.keycloak.representations.idm.UserRepresentation userRep = userResource.toRepresentation();
      userRep.setEnabled(enabled);
      userResource.update(userRep);

      log.info("Successfully updated user enabled status: userId={}, enabled={}", userId, enabled);

    } catch (NotFoundException e) {
      log.error("User not found in Keycloak: userId={}", userId, e);
      throw new com.event.tickets.exceptions.KeycloakUserUpdateException(
          String.format("User not found in Keycloak: %s", userId),
          e
      );
    } catch (Exception e) {
      log.error("Failed to update user enabled status: userId={}", userId, e);
      throw new com.event.tickets.exceptions.KeycloakUserUpdateException(
          String.format("Failed to update user enabled status: %s", e.getMessage()),
          e
      );
    }
  }

  @Override
  public boolean userExistsByEmail(String email) {
    try {
      RealmResource realmResource = keycloakAdminClient.realm(realm);
      List<org.keycloak.representations.idm.UserRepresentation> users = realmResource.users()
          .searchByEmail(email, true);
      return !users.isEmpty();
    } catch (Exception e) {
      log.error("Error checking if user exists by email: {}", email, e);
      return false;
    }
  }

  @Override
  public UUID getUserIdByEmail(String email) {
    try {
      RealmResource realmResource = keycloakAdminClient.realm(realm);
      List<org.keycloak.representations.idm.UserRepresentation> users = realmResource.users()
          .searchByEmail(email, true);
      if (!users.isEmpty()) {
        String userId = users.get(0).getId();
        return UUID.fromString(userId);
      }
      return null;
    } catch (Exception e) {
      log.error("Error getting user ID by email: {}", email, e);
      return null;
    }
  }

  /**
   * Filters out Keycloak default/system roles.
   * Only returns application-specific roles.
   *
   * @param roleName The role name to check
   * @return true if it's an application role
   */
  private boolean isApplicationRole(String roleName) {
    // Filter out Keycloak default roles
    return !roleName.startsWith("default-roles-") &&
           !roleName.equals("offline_access") &&
           !roleName.equals("uma_authorization") &&
           !roleName.startsWith("realm-");
  }

  /**
   * Gets current authenticated user from security context.
   * Extracts user ID from JWT subject claim.
   */
  private User getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
      Jwt jwt = (Jwt) authentication.getPrincipal();
      String userId = jwt.getSubject();
      if (userId != null) {
        try {
          return userRepository.findById(UUID.fromString(userId)).orElse(null);
        } catch (IllegalArgumentException e) {
          log.warn("Invalid user ID format in JWT: {}", userId);
          return null;
        }
      }
    }
    log.warn("Could not extract user from security context");
    return null;
  }

  /**
   * Gets current HTTP request.
   * For audit logging purposes.
   */
  private HttpServletRequest getCurrentRequest() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes != null ? attributes.getRequest() : null;
  }
}
