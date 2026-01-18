package com.event.tickets.filters;

import com.event.tickets.domain.entities.ApprovalStatus;
import com.event.tickets.domain.entities.User;
import com.event.tickets.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * User Provisioning Filter
 *
 * Automatically creates a shadow user record in the backend database
 * when a user authenticates via Keycloak for the first time.
 *
 * Execution order: Runs AFTER JWT validation, BEFORE ApprovalGateFilter
 *
 * Behavior:
 * 1. Extracts user ID (sub) from validated JWT
 * 2. Checks if user exists in backend database
 * 3. If not exists:
 *    - Creates User record with ID from JWT
 *    - Populates name and email from JWT claims
 *    - Sets approval status to APPROVED (for backward compatibility with existing Keycloak users)
 * 4. If exists: continue (no action)
 *
 * Note: New users registered via invite code system will have PENDING status by default.
 * This filter only handles existing Keycloak users who authenticate before approval system was added.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserProvisioningFilter extends OncePerRequestFilter {

  private final UserRepository userRepository;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof Jwt jwt) {

      UUID keycloakId = UUID.fromString(jwt.getSubject());

      if (!userRepository.existsById(keycloakId)) {
        log.info("Provisioning new user from JWT: userId={}, email={}",
            keycloakId, jwt.getClaimAsString("email"));

        User user = new User();
        user.setId(keycloakId);
        user.setName(jwt.getClaimAsString("preferred_username"));
        user.setEmail(jwt.getClaimAsString("email"));

        // Set APPROVED status for backward compatibility with existing Keycloak users
        // New registrations via invite code will set PENDING status explicitly
        user.setApprovalStatus(ApprovalStatus.APPROVED);

        userRepository.save(user);
        log.info("User provisioned successfully: userId={}", keycloakId);
      }

    }

    filterChain.doFilter(request, response);
  }
}
