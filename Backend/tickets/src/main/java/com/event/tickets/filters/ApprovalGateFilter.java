package com.event.tickets.filters;

import com.event.tickets.domain.entities.ApprovalStatus;
import com.event.tickets.domain.entities.User;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.KeycloakAdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class ApprovalGateFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {

            UUID userId;
            try {
                userId = UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid JWT subject format in approval gate: sub={}", jwt.getSubject());
                sendForbiddenResponse(response, "INVALID_TOKEN_SUBJECT",
                        "Invalid authentication token subject.", "unknown");
                return;
            }
            String path = request.getRequestURI();
            String method = request.getMethod();

            log.debug("Checking approval status: userId={}, method={}, path={}", userId, method, path);

            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                log.warn("User not found in database during approval check: userId={}", userId);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not provisioned in system");
                return;
            }

            ApprovalStatus status = user.getApprovalStatus();

            if (status == null) {
                log.error("Invalid user state: null approval status for userId={}", userId);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "User state invalid");
                return;
            }

            boolean existsInKeycloak;
            try {
                existsInKeycloak = keycloakAdminService.userExists(userId);
            } catch (Exception e) {
                log.error("Failed to verify user existence in identity provider: userId={}", userId, e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to verify identity state");
                return;
            }

            if (!existsInKeycloak) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        "User does not exist in identity provider");
                return;
            }

            if (status == ApprovalStatus.PENDING || status == ApprovalStatus.REJECTED) {
                log.warn("APPROVAL GATE BLOCK: {} - userId={}, email={}, path={}", status, userId, user.getEmail(), path);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "User not approved");
                return;
            }

            log.debug("Approval gate passed: userId={}, status={}, path={}", userId, status, path);
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/auth")
                || path.startsWith("/api/v1/published-events")
                || path.startsWith("/api/v1/invites/redeem")
                || path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")) {
            log.debug("Approval gate bypassed: {}", path);
            return true;
        }
        return false;
    }

    private void sendForbiddenResponse(HttpServletResponse response, String errorCode,
                                       String message, String userId) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Map<String, String> errorBody = Map.of(
                "error", errorCode,
                "message", message,
                "status", "403",
                "timestamp", java.time.Instant.now().toString()
        );
        response.getWriter().write(objectMapper.writeValueAsString(errorBody));
    }

}