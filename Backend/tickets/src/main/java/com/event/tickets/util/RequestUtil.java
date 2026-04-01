package com.event.tickets.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Request Utility
 *
 * Extracts client information from HTTP requests.
 * Used for audit logging and security tracking.
 */
@UtilityClass
public class RequestUtil {

  /**
   * Returns the current HTTP request, or null if called outside a request context
   * (e.g. from a scheduler or async thread).
   *
   * @return current HttpServletRequest, or null if not in a web request scope
   */
  public static HttpServletRequest getCurrentRequest() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes != null ? attributes.getRequest() : null;
  }

  /**
   * Extracts client IP address from HTTP request.
   * Returns "unknown" if request is null.
   *
   * Priority:
   * 1. X-Forwarded-For (first IP if comma-separated, for proxied requests)
   * 2. X-Real-IP
   * 3. Remote address (direct connection)
   *
   * @param request HTTP servlet request (may be null)
   * @return Client IP address, never null
   */
  public static String extractClientIp(HttpServletRequest request) {
    if (request == null) {
      return "unknown";
    }

    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }

    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isEmpty()) {
      return xRealIp.trim();
    }

    String remoteAddr = request.getRemoteAddr();
    return remoteAddr != null ? remoteAddr : "unknown";
  }

  /**
   * Extracts user agent from HTTP request.
   * Returns "unknown" if request is null.
   *
   * @param request HTTP servlet request (may be null)
   * @return User agent string, never null
   */
  public static String extractUserAgent(HttpServletRequest request) {
    if (request == null) {
      return "unknown";
    }

    String userAgent = request.getHeader("User-Agent");
    return userAgent != null ? userAgent : "unknown";
  }
}
