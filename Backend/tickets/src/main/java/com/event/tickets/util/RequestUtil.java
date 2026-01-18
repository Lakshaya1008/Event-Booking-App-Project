package com.event.tickets.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;

/**
 * Request Utility
 *
 * Extracts client information from HTTP requests.
 *
 * Used for audit logging and security tracking.
 */
@UtilityClass
public class RequestUtil {

  /**
   * Extracts client IP address from HTTP request.
   *
   * Priority:
   * 1. X-Forwarded-For (first IP if comma-separated)
   * 2. X-Real-IP
   * 3. Remote address
   *
   * @param request HTTP servlet request
   * @return Client IP address
   */
  public static String extractClientIp(HttpServletRequest request) {
    if (request == null) {
      return "unknown";
    }

    // Check X-Forwarded-For header (proxy/load balancer)
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      // Take first IP if comma-separated
      return xForwardedFor.split(",")[0].trim();
    }

    // Check X-Real-IP header
    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isEmpty()) {
      return xRealIp.trim();
    }

    // Fallback to remote address
    String remoteAddr = request.getRemoteAddr();
    return remoteAddr != null ? remoteAddr : "unknown";
  }

  /**
   * Extracts user agent from HTTP request.
   *
   * @param request HTTP servlet request
   * @return User agent string
   */
  public static String extractUserAgent(HttpServletRequest request) {
    if (request == null) {
      return "unknown";
    }

    String userAgent = request.getHeader("User-Agent");
    return userAgent != null ? userAgent : "unknown";
  }
}
