package com.event.tickets.controllers;

import static com.event.tickets.util.JwtUtil.parseUserId;

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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * Validates DB user exists first, then delegates to Keycloak Admin API.
     * Audit log is written by KeycloakAdminServiceImpl.assignRoleToUser().
     */
    @PostMapping("/users/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserRolesResponseDto> assignRoleToUser(
            @PathVariable UUID userId,
            @Valid @RequestBody AssignRoleRequestDto request
    ) {
        log.info("ADMIN assigning role '{}' to user '{}'", request.getRoleName(), userId);

        // Validate local user first — avoids mutating Keycloak for unknown local users
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID '%s' not found", userId)));

        keycloakAdminService.assignRoleToUser(userId, request.getRoleName());

        List<String> updatedRoles = keycloakAdminService.getUserRoles(userId);

        UserRolesResponseDto response = new UserRolesResponseDto(
                userId, user.getName(), user.getEmail(), updatedRoles);

        log.info("Successfully assigned role '{}' to user '{}'", request.getRoleName(), userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/users/{userId}/roles/{roleName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserRolesResponseDto> revokeRoleFromUser(
            @PathVariable UUID userId,
            @PathVariable String roleName
    ) {
        log.info("ADMIN revoking role '{}' from user '{}'", roleName, userId);

        // Prevents Keycloak state mutation when user is not known locally.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID '%s' not found", userId)));

        keycloakAdminService.revokeRoleFromUser(userId, roleName);

        List<String> updatedRoles = keycloakAdminService.getUserRoles(userId);

        UserRolesResponseDto response = new UserRolesResponseDto(
                userId, user.getName(), user.getEmail(), updatedRoles);

        log.info("Successfully revoked role '{}' from user '{}'", roleName, userId);
        return ResponseEntity.ok(response);
    }

    /** Get all roles assigned to a user. ADMIN only. */
    @GetMapping("/users/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserRolesResponseDto> getUserRoles(
            @PathVariable UUID userId
    ) {
        log.debug("ADMIN fetching roles for user '{}'", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID '%s' not found", userId)));

        List<String> roles = keycloakAdminService.getUserRoles(userId);

        return ResponseEntity.ok(new UserRolesResponseDto(
                userId, user.getName(), user.getEmail(), roles));
    }

    /** Get all available roles in the system. ADMIN only. */
    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AvailableRolesResponseDto> getAvailableRoles() {
        log.debug("ADMIN fetching available roles");
        List<String> roles = keycloakAdminService.getAvailableRoles();
        return ResponseEntity.ok(new AvailableRolesResponseDto(roles, "Available roles in the system"));
    }
}