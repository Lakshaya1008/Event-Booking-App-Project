package com.event.tickets.controllers;

import com.event.tickets.domain.dtos.AssignRoleRequestDto;
import com.event.tickets.domain.dtos.AvailableRolesResponseDto;
import com.event.tickets.domain.dtos.UserRolesResponseDto;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.KeycloakAdminService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin Governance Controller
 *
 * Provides ADMIN-only endpoints for role management via Keycloak Admin API.
 *
 * Security Model:
 * - All endpoints require ADMIN role
 * - Backend is the SOLE authority for Keycloak Admin API calls
 * - No frontend Keycloak interactions permitted
 * - Roles managed exclusively by Keycloak (no DB persistence)
 *
 * IMPORTANT:
 * - ADMIN role does NOT bypass business rules
 * - ADMINs cannot access events they don't own
 * - ADMINs can only manage roles, not override authorization
 *
 * Endpoints:
 * - POST /admin/users/{userId}/roles - Assign role to user
 * - DELETE /admin/users/{userId}/roles/{roleName} - Revoke role from user
 * - GET /admin/users/{userId}/roles - Get user's roles
 * - GET /admin/roles - Get all available roles
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminGovernanceController {

  private final KeycloakAdminService keycloakAdminService;
  private final UserRepository userRepository;

  /**
   * Assign a role to a user.
   *
   * ADMIN-only operation.
   * Invokes Keycloak Admin API to assign realm-level role.
   *
   * @param userId The UUID of the user
   * @param request The role assignment request containing role name
   * @return Success message with updated user roles
   */
  @PostMapping("/users/{userId}/roles")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UserRolesResponseDto> assignRoleToUser(
      @PathVariable UUID userId,
      @Valid @RequestBody AssignRoleRequestDto request
  ) {
    log.info("ADMIN assigning role '{}' to user '{}'", request.getRoleName(), userId);

    // Assign role via Keycloak Admin API
    keycloakAdminService.assignRoleToUser(userId, request.getRoleName());

    // Fetch updated roles
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(
            String.format("User with ID '%s' not found", userId)
        ));

    List<String> updatedRoles = keycloakAdminService.getUserRoles(userId);

    UserRolesResponseDto response = new UserRolesResponseDto(
        userId.toString(),
        user.getName(),
        user.getEmail(),
        updatedRoles
    );

    log.info("Successfully assigned role '{}' to user '{}'", request.getRoleName(), userId);
    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  /**
   * Revoke a role from a user.
   *
   * ADMIN-only operation.
   * Invokes Keycloak Admin API to remove realm-level role.
   *
   * @param userId The UUID of the user
   * @param roleName The name of the role to revoke
   * @return Success message with updated user roles
   */
  @DeleteMapping("/users/{userId}/roles/{roleName}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UserRolesResponseDto> revokeRoleFromUser(
      @PathVariable UUID userId,
      @PathVariable String roleName
  ) {
    log.info("ADMIN revoking role '{}' from user '{}'", roleName, userId);

    // Revoke role via Keycloak Admin API
    keycloakAdminService.revokeRoleFromUser(userId, roleName);

    // Fetch updated roles
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(
            String.format("User with ID '%s' not found", userId)
        ));

    List<String> updatedRoles = keycloakAdminService.getUserRoles(userId);

    UserRolesResponseDto response = new UserRolesResponseDto(
        userId.toString(),
        user.getName(),
        user.getEmail(),
        updatedRoles
    );

    log.info("Successfully revoked role '{}' from user '{}'", roleName, userId);
    return ResponseEntity.ok(response);
  }

  /**
   * Get all roles assigned to a user.
   *
   * ADMIN-only operation.
   * Queries Keycloak for user's realm-level roles.
   *
   * @param userId The UUID of the user
   * @return User information with assigned roles
   */
  @GetMapping("/users/{userId}/roles")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UserRolesResponseDto> getUserRoles(
      @PathVariable UUID userId
  ) {
    log.debug("ADMIN fetching roles for user '{}'", userId);

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(
            String.format("User with ID '%s' not found", userId)
        ));

    List<String> roles = keycloakAdminService.getUserRoles(userId);

    UserRolesResponseDto response = new UserRolesResponseDto(
        userId.toString(),
        user.getName(),
        user.getEmail(),
        roles
    );

    return ResponseEntity.ok(response);
  }

  /**
   * Get all available roles in the system.
   *
   * ADMIN-only operation.
   * Returns all realm-level roles that can be assigned to users.
   *
   * @return List of available role names
   */
  @GetMapping("/roles")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<AvailableRolesResponseDto> getAvailableRoles() {
    log.debug("ADMIN fetching available roles");

    List<String> roles = keycloakAdminService.getAvailableRoles();

    AvailableRolesResponseDto response = new AvailableRolesResponseDto(
        roles,
        "Available roles in the system"
    );

    return ResponseEntity.ok(response);
  }
}
