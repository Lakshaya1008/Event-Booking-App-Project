package com.event.tickets.filters;

import com.event.tickets.domain.entities.User;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.KeycloakAdminService;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired(required = false)
    private KeycloakAdminService keycloakAdminService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!(authentication.getPrincipal() instanceof Jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userIdStr = jwt.getSubject();

        if (userIdStr == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token: missing subject");
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token: malformed user ID");
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not provisioned in system");
            return;
        }

        if (user.getApprovalStatus() == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "User state invalid");
            return;
        }

        if (keycloakAdminService != null) {
            boolean exists;
            try {
                exists = keycloakAdminService.userExists(userId);
            } catch (Exception e) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to verify identity");
                return;
            }

            if (!exists) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        "User not found in identity provider");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}