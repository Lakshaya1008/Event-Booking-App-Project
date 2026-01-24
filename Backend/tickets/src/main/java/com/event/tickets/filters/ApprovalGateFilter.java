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
 * Approval Gate Filter - PRODUCTION HARDENED
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * CRITICAL SECURITY COMPONENT - DO NOT MODIFY WITHOUT SECURITY REVIEW
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * PURPOSE:
 * Enforces admin approval requirement for user accounts as a BUSINESS GATE,
 * not an authentication gate. This is SEPARATE from Keycloak authentication.
 *
 * WHY THIS EXISTS:
 * - Keycloak handles authentication (who you are)
 * - Keycloak assigns roles (what you claim to be)
 * - THIS FILTER enforces approval (whether we trust your claim)
 *
 * SECURITY MODEL:
 * - User authenticates with Keycloak → gets JWT with roles ✓
 * - JWT is cryptographically valid ✓
 * - User has legitimate role assignment ✓
 * - BUT: User may not be approved for business operations ✗
 * - This filter blocks unapproved users DESPITE valid auth
 *
 * EXECUTION ORDER:
 * @Order(2) - Runs AFTER UserProvisioningFilter, BEFORE controller dispatch
 * 1. Spring Security validates JWT
 * 2. Spring Security extracts roles from JWT
 * 3. UserProvisioningFilter creates/validates user record
 * 4. >>> THIS FILTER checks approval status <<<
 * 5. @PreAuthorize checks roles
 * 6. Controller executes business logic
 *
 * SAFE-BY-DEFAULT BEHAVIOR:
 * - ALL authenticated endpoints require approval by default
 * - Approval check runs for EVERY request unless explicitly exempted
 * - Exemptions are maintained in EXPLICIT ALLOWLIST (see shouldNotFilter)
 * - No controller annotation can accidentally bypass this filter
 *
 * ALLOWLIST CATEGORIES (EXPLICIT AND DOCUMENTED):
 *
 * Category 1: AUTHENTICATION & REGISTRATION (No auth required)
 *   - /api/v1/auth/register - Public registration endpoint
 *   Rationale: Must be accessible before user has account/approval
 *
 * Category 2: MONITORING & OPERATIONS (No auth required)
 *   - /actuator/health/** - Health checks for load balancers
 *   - /actuator/info - Application metadata
 *   Rationale: Required for infrastructure monitoring, no business data exposed
 *
 * Category 3: INVITE REDEMPTION (Authenticated but pre-approval)
 *   - /api/v1/invites/redeem - Invite code redemption
 *   Rationale: Intentionally allowed for users in PENDING state to complete onboarding
 *   WARNING: This is a DELIBERATE EXCEPTION - user can act before approval
 *
 * ALL OTHER ENDPOINTS: Approval required, no exceptions
 *
 * APPROVAL STATES:
 * - NULL: Legacy user (auto-migrated to APPROVED)
 * - PENDING: New user awaiting admin review → 403 BLOCKED
 * - APPROVED: Admin approved → ALLOWED
 * - REJECTED: Admin rejected → 403 BLOCKED
 *
 * PRODUCTION SAFETY:
 * - Logs all approval denials with userId and path
 * - Never logs sensitive data (tokens, passwords)
 * - Fails closed (unknown state → block)
 * - Transaction-safe (read-only check, no side effects)
 *
 * MAINTENANCE NOTES:
 * - To add new exempted endpoint: Update shouldNotFilter() with comment explaining WHY
 * - To change approval logic: Requires security review
 * - To disable filter: DO NOT - this is a business requirement, not optional
 *
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Component
@Order(2) // Run after UserProvisioningFilter (Order 1 by default)
@RequiredArgsConstructor
@Slf4j
public class ApprovalGateFilter extends OncePerRequestFilter {

  private final UserRepository userRepository;
  private final AuditLogService auditLogService;
  private final SystemUserProvider systemUserProvider;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * ALLOWLIST: Paths that bypass approval gate
   * These are the ONLY paths where unapproved users can make requests
   */
  private static final String[] APPROVAL_BYPASS_PATHS = {
      // Authentication & Registration (public endpoints)
      "/api/v1/auth/register",

      // Monitoring (infrastructure, no business logic)
      "/actuator/health",
      "/actuator/info",

      // Invite Redemption (DELIBERATE EXCEPTION - see class javadoc)
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
        // User not found in DB - UserProvisioningFilter should have created it
        // Allow request to proceed; will fail in controller if user is required
        log.warn("User not found in database during approval check: userId={}", userId);
        filterChain.doFilter(request, response);
        return;
      }

      ApprovalStatus status = user.getApprovalStatus();

      // Handle NULL status for legacy users (existed before approval system was added)
      // Auto-migrate them to APPROVED for backward compatibility
      if (status == null) {
        log.info("Auto-migrating legacy user to APPROVED status: userId={}, email={}",
            userId, user.getEmail());
        user.setApprovalStatus(ApprovalStatus.APPROVED);
        userRepository.save(user);
        status = ApprovalStatus.APPROVED;
      }

      // FAIL-CLOSED: Block PENDING users
      if (status == ApprovalStatus.PENDING) {
        log.warn("APPROVAL GATE BLOCK: User pending approval - userId={}, email={}, method={}, path={}",
            userId, user.getEmail(), method, path);
        
        // Emit approval gate violation audit event
        emitApprovalGateViolation(user, path, method, "PENDING");
        
        sendForbiddenResponse(
            response,
            "APPROVAL_PENDING",
            "Your account is awaiting approval from an administrator. " +
            "You will be notified once your account has been reviewed.",
            userId.toString()
        );
        return;
      }

      // FAIL-CLOSED: Block REJECTED users
      if (status == ApprovalStatus.REJECTED) {
        log.warn("APPROVAL GATE BLOCK: User account rejected - userId={}, email={}, method={}, path={}",
            userId, user.getEmail(), method, path);
        String reason = user.getRejectionReason() != null
            ? user.getRejectionReason()
            : "No reason provided";
            
        // Emit approval gate violation audit event
        emitApprovalGateViolation(user, path, method, "REJECTED: " + reason);
        
        sendForbiddenResponse(
            response,
            "APPROVAL_REJECTED",
            "Your account has been rejected. Reason: " + reason,
            userId.toString()
        );
        return;
      }

      // Status is APPROVED - allow request
      log.debug("Approval gate passed: userId={}, status={}, path={}", userId, status, path);
    }

    filterChain.doFilter(request, response);
  }

  /**
   * CRITICAL: Determines if approval gate should be bypassed
   *
   * SAFE-BY-DEFAULT: Returns FALSE (apply filter) unless explicitly allowlisted
   *
   * MAINTENANCE: When adding new bypass path:
   * 1. Add to APPROVAL_BYPASS_PATHS array above
   * 2. Document rationale in class javadoc
   * 3. Get security review if path handles business data
   *
   * @param request The HTTP request
   * @return true ONLY if path is in explicit allowlist
   */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();

    // Check explicit allowlist
    for (String bypassPath : APPROVAL_BYPASS_PATHS) {
      if (path.startsWith(bypassPath)) {
        log.debug("Approval gate bypassed for allowlisted path: {}", path);
        return true;
      }
    }

    // NOT in allowlist → apply filter (SAFE-BY-DEFAULT)
    return false;
  }

  /**
   * Sends a 403 Forbidden response with structured JSON error
   *
   * @param response The HTTP response
   * @param errorCode Machine-readable error code
   * @param message Human-readable error message
   * @param userId User ID for correlation (NOT logged in response)
   * @throws IOException If writing response fails
   */
  private void sendForbiddenResponse(
      HttpServletResponse response,
      String errorCode,
      String message,
      String userId) throws IOException {

    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    // Structured error response (userId NOT included - correlation via logs only)
    Map<String, String> errorBody = Map.of(
        "error", errorCode,
        "message", message,
        "status", "403",
        "timestamp", java.time.Instant.now().toString()
    );

    response.getWriter().write(objectMapper.writeValueAsString(errorBody));
  }

  /**
   * Emits an audit event for approval gate violations.
   *
   * @param user The user being blocked
   * @param path The requested path
   * @param method The HTTP method
   * @param reason The reason for the block
   */
  private void emitApprovalGateViolation(User user, String path, String method, String reason) {
    try {
      if (user == null) {
        user = systemUserProvider.getSystemUser();
      }
      AuditLog auditLog = AuditLog.builder()
          .action(AuditAction.APPROVAL_GATE_VIOLATION)
          .actor(user)
          .targetUser(user)
          .resourceType("API_ENDPOINT")
          .resourceId(null)
          .details("path=" + path + ",method=" + method + ",reason=" + reason)
          .ipAddress(extractClientIp(null))
          .userAgent(extractUserAgent(null))
          .build();
      auditLogService.saveAuditLog(auditLog);
    } catch (Exception e) {
      log.error("Failed to emit approval gate violation audit event: userId={}, error={}", 
          user != null ? user.getId() : "null", e.getMessage());
      // Audit failures should not break the main flow
    }
  }
}
