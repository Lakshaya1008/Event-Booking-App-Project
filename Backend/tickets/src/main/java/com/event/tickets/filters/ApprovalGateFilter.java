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

/**
 * Approval Gate Filter
 *
 * FIX #10: emitApprovalGateViolation() previously called extractClientIp(null)
 * and extractUserAgent(null), producing null/unknown in every audit record.
 * The HttpServletRequest was available in doFilterInternal() but not being passed
 * down to the private method. Every violation audit had no forensic data.
 *
 * Fix: request is now passed as a parameter to emitApprovalGateViolation().
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class ApprovalGateFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final SystemUserProvider systemUserProvider;
    /**
     * L-15 style FIX: ObjectMapper injected by Spring, not created with new.
     * ApprovalGateFilter writes error responses — using a separate ObjectMapper
     * instance would serialize timestamps differently from the rest of the API.
     */
    private final ObjectMapper objectMapper;

    private static final String[] APPROVAL_BYPASS_PATHS = {
            "/api/v1/auth/register",
            "/actuator/health",
            "/actuator/info",
            "/api/v1/invites/redeem"
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

                // FIX #10: pass request so real IP and UserAgent are captured in audit
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

                // FIX #10: pass request
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

    /**
     * FIX #10: Accepts HttpServletRequest so real IP and UserAgent are recorded.
     * Old signature had no request parameter — used extractClientIp(null) which
     * always produced "unknown", defeating the purpose of the audit trail.
     */
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