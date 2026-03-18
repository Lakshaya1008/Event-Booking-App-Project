package com.event.tickets.filters;

import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.ApprovalStatus;
import com.event.tickets.domain.entities.User;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuditLogService;
import com.event.tickets.services.SystemUserProvider;
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

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class ApprovalGateFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final SystemUserProvider systemUserProvider;
    private final ObjectMapper objectMapper;

    /**
     * FIX ISSUE 9: Added Swagger UI and actuator paths to the bypass list.
     *
     * BEFORE: A PENDING user hitting /swagger-ui.html would get 403 APPROVAL_PENDING
     * instead of the documentation page. Same for Kubernetes/Render health probes
     * that authenticate with a JWT but hit /actuator/health.
     *
     * AFTER: Swagger and all actuator endpoints bypass the approval gate.
     * This is safe because:
     * - Actuator endpoints are individually secured in SecurityConfig
     * - Swagger UI is read-only documentation
     * - Business endpoints (/api/v1/**) still require APPROVED status
     *
     * FIX ISSUE #5: Security Policy Documentation
     * These bypass paths are safe to access regardless of approval status:
     * - /actuator/*: Infrastructure health checks (minimal info, secured individually)
     * - /swagger-ui, /api-docs: Read-only API documentation (no data access)
     * - /api/v1/auth/register: Users must register before approval (explicit enrollment)
     * - /api/v1/invites/redeem: Part of invitation workflow (precedes approval)
     *
     * All sensitive business logic endpoints (/api/v1/events, /api/v1/tickets, etc.)
     * still require APPROVED status and are NOT bypassed.
     */
    private static final String[] APPROVAL_BYPASS_PATHS = {
            "/api/v1/auth/register",
            "/actuator",               // all actuator endpoints including health, info, metrics
            "/api/v1/invites/redeem",
            // FIX ISSUE 9: Swagger/OpenAPI endpoints — docs must be accessible regardless of approval
            "/swagger-ui",
            "/v3/api-docs",
            "/api-docs",
    };

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Jwt jwt) {

            UUID userId = UUID.fromString(jwt.getSubject());
            String path = request.getRequestURI();
            String method = request.getMethod();

            log.debug("Checking approval status: userId={}, method={}, path={}", userId, method, path);

            User user = userRepository.findById(userId).orElse(null);

            if (user == null) {
                log.warn("User not found in database during approval check: userId={}", userId);
                filterChain.doFilter(request, response);
                return;
            }

            ApprovalStatus status = user.getApprovalStatus();

            if (status == null) {
                log.warn("Legacy user with null approval status: userId={}, email={}", userId, user.getEmail());
            }

            if (status == ApprovalStatus.PENDING) {
                log.warn("APPROVAL GATE BLOCK: PENDING — userId={}, email={}, path={}", userId, user.getEmail(), path);
                emitApprovalGateViolation(user, path, method, "PENDING", request);
                sendForbiddenResponse(response, "APPROVAL_PENDING",
                        "Your account is awaiting approval from an administrator. " +
                                "You will be notified once your account has been reviewed.",
                        userId.toString());
                return;
            }

            if (status == ApprovalStatus.REJECTED) {
                log.warn("APPROVAL GATE BLOCK: REJECTED — userId={}, email={}, path={}", userId, user.getEmail(), path);
                String reason = user.getRejectionReason() != null ? user.getRejectionReason() : "No reason provided";
                emitApprovalGateViolation(user, path, method, "REJECTED: " + reason, request);
                sendForbiddenResponse(response, "APPROVAL_REJECTED",
                        "Your account has been rejected. Reason: " + reason,
                        userId.toString());
                return;
            }

            log.debug("Approval gate passed: userId={}, status={}, path={}", userId, status, path);
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String bypassPath : APPROVAL_BYPASS_PATHS) {
            if (path.startsWith(bypassPath)) {
                log.debug("Approval gate bypassed: {}", path);
                return true;
            }
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

    private void emitApprovalGateViolation(User user, String path, String method,
                                           String reason, HttpServletRequest request) {
        try {
            if (user == null) user = systemUserProvider.getSystemUser();
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.APPROVAL_GATE_VIOLATION)
                    .actor(user)
                    .targetUser(user)
                    .resourceType("API_ENDPOINT")
                    .resourceId(null)
                    .details("path=" + path + ",method=" + method + ",reason=" + reason)
                    .ipAddress(extractClientIp(request))
                    .userAgent(extractUserAgent(request))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit approval gate violation audit: userId={}, error={}",
                    user != null ? user.getId() : "null", e.getMessage());
        }
    }
}