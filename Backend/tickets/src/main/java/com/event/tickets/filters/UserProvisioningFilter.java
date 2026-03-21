package com.event.tickets.filters;

import com.event.tickets.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * User Provisioning Filter
 *
 * FIXES APPLIED:
 *
 * FIX-UPF1 — Added @Order(1) so this filter runs BEFORE ApprovalGateFilter (@Order(2)).
 *   BEFORE: No @Order annotation — Spring assigned Ordered.LOWEST_PRECEDENCE (MAX_VALUE),
 *   meaning this filter ran LAST, after the gate had already blocked the request.
 *   The desync warning was never logged for blocked users because they never reached this filter.
 *   AFTER: @Order(1) guarantees: UserProvisioningFilter → ApprovalGateFilter → request handler.
 *
 * DESIGN DECISION (unchanged): This filter is intentionally read-only.
 *   Auto-provisioning users on first JWT validation would bypass the explicit
 *   registration + approval workflow. This filter only detects and logs desyncs.
 */
@Component
@Order(1)   // FIX-UPF1: Must run BEFORE ApprovalGateFilter (@Order(2))
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

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {

            UUID keycloakId;
            try {
                keycloakId = UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException e) {
                log.warn("Skipping provisioning check due to invalid JWT subject format: sub={}", jwt.getSubject());
                filterChain.doFilter(request, response);
                return;
            }

            // Read-only check: log desync between Keycloak and DB for observability.
            // No auto-provisioning — see class Javadoc for rationale.
            if (!userRepository.existsById(keycloakId)) {
                log.warn("DESYNC DETECTED: Keycloak user has no DB record. " +
                                "userId={}, email={} — user must register via /api/v1/auth/register",
                        keycloakId, jwt.getClaimAsString("email"));
            }
        }

        filterChain.doFilter(request, response);
    }
}